package com.infernokun.amaterasu.services.cron;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.models.entities.RemoteServerStats;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.repositories.RemoteServerStatsRepository;
import com.infernokun.amaterasu.services.alt.ProxmoxService;
import com.infernokun.amaterasu.services.entity.RemoteServerService;
import com.infernokun.amaterasu.services.alt.RemoteCommandService;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.entity.RemoteServerStatsService;
import jakarta.annotation.PostConstruct;
import org.hibernate.StaleObjectStateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
public class RemoteStatsService extends BaseService {
    private static final String SCRIPT_FILENAME = "get_server_stats.sh";

    private final RemoteServerService remoteServerService;
    private final RemoteServerStatsService remoteServerStatsService;
    private final RemoteCommandService remoteCommandService;
    private final RemoteServerStatsRepository remoteServerStatsRepository;
    private final ObjectMapper objectMapper;
    private final AmaterasuConfig amaterasuConfig;
    private final ProxmoxService proxmoxService;
    private String scriptContentBase64;

    public RemoteStatsService(
            RemoteServerService remoteServerService, RemoteServerStatsService remoteServerStatsService,
            RemoteCommandService remoteCommandService,
            RemoteServerStatsRepository remoteServerStatsRepository,
            ObjectMapper objectMapper,
            AmaterasuConfig amaterasuConfig, ProxmoxService proxmoxService) {
        this.remoteServerService = remoteServerService;
        this.remoteServerStatsService = remoteServerStatsService;
        this.remoteCommandService = remoteCommandService;
        this.remoteServerStatsRepository = remoteServerStatsRepository;
        this.objectMapper = objectMapper;
        this.amaterasuConfig = amaterasuConfig;
        this.proxmoxService = proxmoxService;
    }

    @PostConstruct
    public void initialize() throws IOException {
        try (InputStream file = getClass().getClassLoader().getResourceAsStream("scripts/get_server_stats.sh")) {
            if (file == null) throw new FileNotFoundException("Script file not found in resources");
            scriptContentBase64 = Base64.getEncoder().encodeToString(file.readAllBytes());
        }
    }

    @Scheduled(cron = "0 * * * * *")
    public void getRemoteServerStats() {
        remoteServerService.findAllServers()
                .forEach(this::safeProcessStats);
    }

    private void safeProcessStats(RemoteServer server) {
        try {
            switch(server.getServerType()) {
                case DOCKER_HOST -> processDockerServerStats(server);
                case PROXMOX -> processProxmoxServerStats(server);
                default -> LOGGER.error("Unsupported server type: {}", server.getServerType());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to process stats for server {}: {}", server.getId(), e.getMessage(), e);
        }
    }

    private void processProxmoxServerStats(RemoteServer proxmoxServer) {
        Optional<RemoteServerStats> existingStats = remoteServerStatsRepository.findByRemoteServerId(proxmoxServer.getId());

        if (existingStats.isPresent()) {
            RemoteServerStats stats = existingStats.get();
            stats.setUpdatedAt(LocalDateTime.now());

            boolean isServerHealthy = proxmoxService.proxmoxHealthCheck(proxmoxServer);
            stats.setStatus(isServerHealthy ? LabStatus.ACTIVE : LabStatus.OFFLINE);

            LOGGER.debug("Updating stats for Proxmox server {}: status={}",
                    proxmoxServer.getId(), stats.getStatus());
            remoteServerStatsService.updateStats(stats);
        } else {
            LOGGER.debug("No existing stats found for Proxmox server {}, creating new",
                    proxmoxServer.getId());
            updateServerStats(proxmoxServer, null);
        }
    }

    private void processDockerServerStats(RemoteServer dockerServer) {
        String remoteDir = amaterasuConfig.getUploadDir();
        String scriptRemotePath = remoteDir + "/scripts/" + SCRIPT_FILENAME;

        try {
            // Ensure the script exists before execution
            Optional<Boolean> scriptExists = ensureRemoteScriptAvailable(dockerServer, scriptRemotePath);
            if (scriptExists.isEmpty()) {
                LOGGER.error("Could not determine if the script exists on server {}, skipping stats processing.",
                        dockerServer.getId());
                updateServerStats(dockerServer, null);
                return;
            }

            if (!scriptExists.get()) {
                uploadScriptToServer(dockerServer, scriptRemotePath, remoteDir);
            }

            // Execute the remote command and get the output
            RemoteCommandResponse produceStatsCmd = remoteCommandService.handleRemoteCommand(
                    scriptRemotePath, dockerServer);

            if (!produceStatsCmd.isSuccess()) {
                updateServerStats(dockerServer, null);
                return;
            }

            Optional<RemoteServerStats> stats = executeStatsScriptAndParse(dockerServer, scriptRemotePath);
            updateServerStats(dockerServer, stats.orElse(null));
        } catch (Exception e) {
            LOGGER.error("Some error has occurred {}: {}", dockerServer.getId(), e.getMessage());
            updateServerStats(dockerServer, null);
        }
    }

    private Optional<RemoteServerStats> executeStatsScriptAndParse(RemoteServer server, String scriptPath) {
        RemoteCommandResponse response = remoteCommandService.handleRemoteCommand(scriptPath, server);

        if (!response.isSuccess()) {
            LOGGER.warn("Failed to execute stats script on server {}: {}", server.getId(), response.getError());
            return Optional.empty();
        }

        try {
            String jsonOutput = response.getBoth();
            return Optional.of(objectMapper.readValue(jsonOutput, RemoteServerStats.class));
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse stats JSON from server {}: {}", server.getId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    private void updateServerStats(RemoteServer remoteServer, RemoteServerStats statsFromJson) {
        RemoteServerStats existingStats = remoteServer.getRemoteServerStats();

        if (existingStats != null && existingStats.getId() != null) {
            remoteServerStatsRepository.findById(existingStats.getId())
                    .ifPresentOrElse(
                            stats -> updateStats(stats, statsFromJson, remoteServer),
                            () -> createNewStats(remoteServer, statsFromJson)
                    );
        } else {
            createNewStats(remoteServer, new RemoteServerStats());
        }
    }

    private Optional<Boolean> ensureRemoteScriptAvailable(RemoteServer server, String scriptPath) {
        String cmd = String.format("[ -f \"%s\" ] && echo \"true\" || echo \"false\"", scriptPath);
        RemoteCommandResponse response = remoteCommandService.handleRemoteCommand(cmd, server);

        if (!response.isSuccess()) {
            LOGGER.warn("Failed to check if script exists on server {}: {}", server.getId(), response.getError());
            return Optional.empty(); // command failed
        }

        String result = response.getBoth().trim();
        return Optional.of("true".equals(result)); // script exists or not
    }


    private void uploadScriptToServer(RemoteServer server, String scriptPath, String remoteDir) {
        String uploadCmd = String.format(
                "mkdir -p %s && echo %s | base64 -d > %s && chmod +x %s",
                remoteDir + "/scripts", scriptContentBase64, scriptPath, scriptPath
        );
        remoteCommandService.handleRemoteCommand(uploadCmd, server);
        LOGGER.info("Uploaded script to server {} at {}", server.getId(), scriptPath);
    }

    private void updateStats(RemoteServerStats existingStats, RemoteServerStats statsFromJson, RemoteServer remoteServer) {
        if (statsFromJson == null) {
            LOGGER.error("Server {} set to OFFLINE!", remoteServer.getName());
            existingStats.setStatus(LabStatus.OFFLINE);
            remoteServerStatsService.updateStats(existingStats);
            return;
        }

        int maxRetries = 3;
        for (int retry = 0; retry < maxRetries; retry++) {
            try {
                // Ensure the ID of statsFromJson matches existingStats
                statsFromJson.setId(existingStats.getId());
                statsFromJson.setRemoteServer(remoteServer);
                statsFromJson.setStatus(LabStatus.ACTIVE);

                // Use objectMapper to update the existingStats object
                objectMapper.readerForUpdating(existingStats).readValue(objectMapper.writeValueAsString(statsFromJson));

                remoteServerStatsService.updateStats(existingStats);
                return; // Exit the retry loop if successful

            } catch (JsonProcessingException e) {
                LOGGER.error("Error updating stats: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            } catch (ObjectOptimisticLockingFailureException | StaleObjectStateException e) {
                LOGGER.warn("Optimistic locking failure for server: {}, retry {}", existingStats.getId(), retry + 1);
                if (retry == maxRetries - 1) {
                    LOGGER.error("Failed to update stats after multiple retries for server: {}", existingStats.getId());
                    throw e; // Re-throw the exception if retries are exhausted
                }
                try {
                    Thread.sleep(100 * (retry + 1)); // Exponential backoff
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }


    private void createNewStats(RemoteServer remoteServer, RemoteServerStats statsFromJson) {
        statsFromJson.setRemoteServer(remoteServer);
        statsFromJson.setStatus(LabStatus.ACTIVE);

        RemoteServerStats savedStats = remoteServerStatsService.createStats(statsFromJson);

        remoteServer.setRemoteServerStats(savedStats);

        RemoteServer modifiedRemoteServer = remoteServerService.modifyStatus(remoteServer);
        LOGGER.info("Created stats for server {}: hostname {}", modifiedRemoteServer.getId(), savedStats.getHostname());
    }
}