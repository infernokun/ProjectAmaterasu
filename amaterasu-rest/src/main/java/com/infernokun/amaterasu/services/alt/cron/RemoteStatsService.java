package com.infernokun.amaterasu.services.alt.cron;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.models.entities.RemoteServerStats;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.repositories.RemoteServerStatsRepository;
import com.infernokun.amaterasu.services.entity.RemoteServerService;
import com.infernokun.amaterasu.services.alt.RemoteCommandService;
import com.infernokun.amaterasu.services.BaseService;
import jakarta.annotation.PostConstruct;
import org.hibernate.StaleObjectStateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;

@Service
public class RemoteStatsService extends BaseService {
    private static final String SCRIPT_FILENAME = "get_server_stats.sh";

    private final RemoteServerService remoteServerService;
    private final RemoteCommandService remoteCommandService;
    private final RemoteServerStatsRepository remoteServerStatsRepository;
    private final ObjectMapper objectMapper;
    private final AmaterasuConfig amaterasuConfig;
    private String scriptContentBase64;

    public RemoteStatsService(
            RemoteServerService remoteServerService,
            RemoteCommandService remoteCommandService,
            RemoteServerStatsRepository remoteServerStatsRepository,
            ObjectMapper objectMapper,
            AmaterasuConfig amaterasuConfig) {
        this.remoteServerService = remoteServerService;
        this.remoteCommandService = remoteCommandService;
        this.remoteServerStatsRepository = remoteServerStatsRepository;
        this.objectMapper = objectMapper;
        this.amaterasuConfig = amaterasuConfig;
    }

    @PostConstruct
    public void initialize() throws IOException {
        try (InputStream file = getClass().getClassLoader().getResourceAsStream("scripts/get_server_stats.sh")) {
            if (file == null) {
                throw new FileNotFoundException("Script file not found in resources");
            }
            scriptContentBase64 = Base64.getEncoder().encodeToString(file.readAllBytes());
        }
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void getRemoteServerStats() {
        List<RemoteServer> remoteServers = remoteServerService.getAllServers();

        List<RemoteServer> dockerServers =
                remoteServers.stream().filter(remoteServer -> remoteServer.getServerType() == ServerType.DOCKER_HOST).toList();

        dockerServers.forEach(
                dockerServer -> {
                    try {
                        updateServerStats(dockerServer);
                    } catch (RemoteCommandException e) {
                        LOGGER.error("Error executing remote command for server {}: {}", dockerServer.getId(), e.getMessage());
                    } catch (Exception e) {
                        LOGGER.error(
                                "Error retrieving or processing remote server stats for server {}: {}", dockerServer.getId(), e.getMessage(), e);
                    }
                });
    }

    private void updateServerStats(RemoteServer dockerServer) {
        String remoteDir = amaterasuConfig.getUploadDir();
        String scriptRemotePath = remoteDir + "/scripts/" + SCRIPT_FILENAME;

        try {
            ensureScriptExists(dockerServer, scriptRemotePath, remoteDir);

            String jsonOutput = remoteCommandService.handleRemoteCommand(scriptRemotePath, dockerServer).getBoth();
            RemoteServerStats statsFromJson = objectMapper.readValue(jsonOutput, RemoteServerStats.class);
            statsFromJson.setStatus(LabStatus.ACTIVE);

            updateExistingStats(dockerServer, statsFromJson);

        } catch (JsonProcessingException e) {
            LOGGER.error("Error processing JSON from server {}: {}", dockerServer.getId(), e.getMessage(), e);
            throw new RuntimeException("Error processing JSON from remote server", e);
        } catch (RemoteCommandException e) {
            LOGGER.error(e.getMessage());
        }
    }

    private void ensureScriptExists(RemoteServer dockerServer, String scriptRemotePath, String remoteDir) {
        String checkScriptCommand = String.format("[ -f \"%s\" ] && echo \"true\" || echo \"false\"", scriptRemotePath);
        String result = remoteCommandService.handleRemoteCommand(checkScriptCommand, dockerServer).getBoth();

        if (!"true".equals(result.trim())) {
            LOGGER.info("Script not found on remote system {}. Uploading script to {}", dockerServer.getId(), scriptRemotePath);
            uploadScript(dockerServer, scriptRemotePath, remoteDir);
        }
    }

    private void uploadScript(RemoteServer dockerServer, String scriptRemotePath, String remoteDir) {
        String uploadCommand =
                String.format(
                        "mkdir -p %s && echo %s | base64 -d > %s && chmod +x %s",
                        remoteDir + "/scripts", scriptContentBase64, scriptRemotePath, scriptRemotePath);
        remoteCommandService.handleRemoteCommand(uploadCommand, dockerServer);
    }

    private void updateExistingStats(RemoteServer remoteServer, RemoteServerStats statsFromJson) {
        RemoteServerStats existingStats = remoteServer.getRemoteServerStats();

        if (existingStats != null && existingStats.getId() != null) {
            String statsId = existingStats.getId();
            remoteServerStatsRepository
                    .findById(statsId)
                    .ifPresentOrElse(
                            existing -> {
                                updateStats(existing, statsFromJson);
                            },
                            () -> {
                                // Else (shouldn't happen in principle), treat as new.
                                createNewStats(remoteServer, statsFromJson);
                            });
        } else {
            // If no stats yet, create them.
            createNewStats(remoteServer, statsFromJson);
        }
    }

    private void updateStats(RemoteServerStats existingStats, RemoteServerStats statsFromJson) {
        int maxRetries = 3;
        for (int retry = 0; retry < maxRetries; retry++) {
            try {
                // Ensure the ID of statsFromJson matches existingStats
                statsFromJson.setId(existingStats.getId());

                // Use objectMapper to update the existingStats object
                objectMapper.readerForUpdating(existingStats).readValue(objectMapper.writeValueAsString(statsFromJson));
                existingStats.setStatus(LabStatus.ACTIVE);

                remoteServerStatsRepository.save(existingStats);
                LOGGER.info("Updated stats for server {}: hostname {}", existingStats.getId(), statsFromJson.getHostname());
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
        RemoteServerStats savedStats = remoteServerStatsRepository.save(statsFromJson);
        remoteServer.setRemoteServerStats(savedStats);
        remoteServerService.modifyStatus(remoteServer);
        LOGGER.info("Created stats for server {}: hostname {}", remoteServer.getId(), savedStats.getHostname());
    }
}