package com.infernokun.amaterasu.services.alt.cron;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.models.entities.RemoteServerStats;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.repositories.RemoteServerStatsRepository;
import com.infernokun.amaterasu.services.entity.RemoteServerService;
import com.infernokun.amaterasu.services.alt.RemoteCommandService;
import com.infernokun.amaterasu.services.BaseService;
import org.hibernate.StaleObjectStateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Base64;

@Service
public class RemoteStatsService extends BaseService {
    private final AmaterasuConfig amaterasuConfig;
    private final RemoteCommandService remoteCommandService;
    private final RemoteServerStatsRepository remoteServerStatsRepository;
    private final RemoteServerService remoteServerService;
    private final ObjectMapper objectMapper;

    private static final String SCRIPT_FILENAME = "get_server_stats.sh";

    public RemoteStatsService(AmaterasuConfig amaterasuConfig, RemoteCommandService remoteCommandService, RemoteServerStatsRepository remoteServerStatsRepository, RemoteServerService remoteServerService) {
        this.amaterasuConfig = amaterasuConfig;
        this.remoteCommandService = remoteCommandService;
        this.remoteServerStatsRepository = remoteServerStatsRepository;
        this.remoteServerService = remoteServerService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Scheduled task that runs every minute.
     * It makes sure the remote statistics gathering script exists on the remote system,
     * uploads it if required, executes the script, and parses its JSON output into a
     * RemoteServerStats entity.
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void getRemoteServerStats() {
        try {
            // Remote directory where the script should be placed.
            String remoteDir = amaterasuConfig.getUploadDir();
            String scriptRemotePath = remoteDir + "/scripts/" + SCRIPT_FILENAME;

            // Check if the script exists on the remote system.
            String result = remoteCommandService
                    .handleRemoteCommand(
                            String.format("[ -f \"%s\" ] && echo \"true\" || echo \"false\"", scriptRemotePath),
                            amaterasuConfig).getBoth();

            if (!"true".equals(result.trim())) {
                LOGGER.info("Script not found on remote system. Uploading script to {}", scriptRemotePath);
                try (InputStream file = getClass().getClassLoader()
                        .getResourceAsStream("scripts/get_server_stats.sh")) {
                    if (file == null) {
                        throw new FileNotFoundException("Script file not found in resources");
                    }

                    remoteCommandService.handleRemoteCommand(
                            String.format("mkdir -p %s && echo %s | base64 -d > %s && chmod +x %s",
                                    remoteDir + "/scripts", Base64.getEncoder().encodeToString(
                                            file.readAllBytes()), scriptRemotePath, scriptRemotePath), amaterasuConfig);
                } catch (Exception e) {
                    LOGGER.error(e.getMessage());
                }
            }

            remoteServerService.getAllServers().forEach(remoteServer -> {
                try {
                    String jsonOutput = remoteCommandService.handleRemoteCommand(scriptRemotePath, amaterasuConfig)
                            .getBoth();

                    RemoteServerStats statsFromJson = objectMapper.readValue(jsonOutput, RemoteServerStats.class);
                    statsFromJson.setStatus(LabStatus.ACTIVE);

                    if (remoteServer.getRemoteServerStats() != null) {
                        String statsId = remoteServer.getRemoteServerStats().getId();
                        remoteServerStatsRepository.findById(statsId)
                                .ifPresentOrElse(existingStats -> {
                                    try {
                                        objectMapper.readerForUpdating(existingStats)
                                                .readValue(jsonOutput);
                                        existingStats.setStatus(LabStatus.ACTIVE);
                                        remoteServerStatsRepository.save(existingStats);
                                        LOGGER.info("Updated stats for server {}: hostname {}",
                                                remoteServer.getId(), statsFromJson.getHostname());
                                    } catch (JsonProcessingException e) {
                                        throw new RuntimeException(e);
                                    }
                                }, () -> {
                                    // Else (shouldn't happen in principle), treat as new.
                                    RemoteServerStats savedStats = remoteServerStatsRepository.save(statsFromJson);
                                    remoteServer.setRemoteServerStats(savedStats);
                                    remoteServerService.addServer(remoteServer);
                                    LOGGER.info("Added new stats for server {}: hostname {}",
                                            remoteServer.getId(), savedStats.getHostname());
                                });
                    } else {
                        // If no stats yet, create them.
                        RemoteServerStats savedStats = remoteServerStatsRepository.save(statsFromJson);
                        remoteServer.setRemoteServerStats(savedStats);
                        remoteServerService.addServer(remoteServer);
                        LOGGER.info("Created stats for server {}: hostname {}",
                                remoteServer.getId(), savedStats.getHostname());
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });

        } catch (RemoteCommandException e) {
            LOGGER.error("Error executing remote command: {}", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException | StaleObjectStateException e) {
            LOGGER.warn("Optimistic locking failure for server {}: ",  e.getMessage());
            // Optionally, you can retry the operation or handle it differently
        } catch (Exception e) {
            LOGGER.error("Error retrieving or processing remote server stats: {}", e.getMessage(), e);
        }
    }
}
