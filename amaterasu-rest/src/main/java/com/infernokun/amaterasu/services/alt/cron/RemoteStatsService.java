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
            String scriptRemotePath = remoteDir + "/" + SCRIPT_FILENAME;

            // Check if the script exists on the remote system.
            String result = remoteCommandService
                    .handleRemoteCommand(
                            String.format("[ -f \"%s\" ] && echo \"true\" || echo \"false\"", scriptRemotePath),
                            amaterasuConfig).getBoth();

            if (!"true".equals(result.trim())) {
                LOGGER.info("Script not found on remote system. Uploading script to {}", scriptRemotePath);
                String scriptContent = Base64.getEncoder().encodeToString(buildStatsScriptContent().getBytes());
                remoteCommandService.handleRemoteCommand(
                        String.format("echo %s | base64 -d > %s && chmod +x %s", scriptContent, scriptRemotePath, scriptRemotePath),
                        amaterasuConfig);
            }

            remoteServerService.getAllServers().forEach(remoteServer -> {
                try {
                    String jsonOutput = remoteCommandService.handleRemoteCommand(scriptRemotePath, amaterasuConfig).getBoth();

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

    /**
     * Builds the content of the shell script which gathers system stats and outputs JSON.
     * The script is expected to gather various metrics, write its JSON result to a file
     * (appending ".out" to its filename), so that it can be retrieved later.
     *
     * @return The content of the script.
     */
    private String buildStatsScriptContent() {
        // The script gathers the desired stats and writes the JSON output into a file.
        // Adjust the commands as necessary for your remote system.
        return """
            #!/bin/bash
            
            # Gather hostname, OS info, memory, CPU, disk, and uptime, then output JSON.
            HOSTNAME=$(hostname)
            OS_NAME=$(lsb_release -si)
            OS_VERSION=$(lsb_release -sr)
            
            # Memory: extract total and available memory (in GB with one decimal place)
            TOTAL_RAM=$(awk '/MemTotal/ {printf "%.1f", $2 / 1024 / 1024}' /proc/meminfo)
            AVAILABLE_RAM=$(awk '/MemAvailable/ {printf "%.1f", $2 / 1024 / 1024}' /proc/meminfo)
            USED_RAM=$(awk -v total="$TOTAL_RAM" -v available="$AVAILABLE_RAM" 'BEGIN {printf "%.1f", total - available}')
            
            # CPU: get number of processors and CPU usage from /proc/stat
            CPU_COUNT=$(nproc)
            IDLE_BEFORE=$(awk '/cpu / {print $5}' /proc/stat)
            TOTAL_BEFORE=$(awk '/cpu / {sum=$2+$3+$4+$5+$6+$7+$8+$9+$10} END {print sum}' /proc/stat)
            sleep 1
            IDLE_AFTER=$(awk '/cpu / {print $5}' /proc/stat)
            TOTAL_AFTER=$(awk '/cpu / {sum=$2+$3+$4+$5+$6+$7+$8+$9+$10} END {print sum}' /proc/stat)
            IDLE_DELTA=$((IDLE_AFTER - IDLE_BEFORE))
            TOTAL_DELTA=$((TOTAL_AFTER - TOTAL_BEFORE))
            CPU_USAGE=$(awk -v idle="$IDLE_DELTA" -v total="$TOTAL_DELTA" 'BEGIN {printf "%.1f", (1 - idle / total) * 100}')
            
            # Disk: extract total and available disk space in GB with one decimal place
            TOTAL_DISK=$(df -m --output=size / | awk 'NR==2 {printf "%.1f", $1 / 1000}')
            AVAILABLE_DISK=$(df -m --output=avail / | awk 'NR==2 {printf "%.1f", $1 / 1000}')
            USED_DISK=$(awk -v total="$TOTAL_DISK" -v available="$AVAILABLE_DISK" 'BEGIN {printf "%.1f", total - available}')
            
            # Uptime in seconds using /proc/uptime
            UPTIME=$(awk '{print int($1)}' /proc/uptime)
            
            # Build JSON output
            JSON=$(cat <<EOF
            {
              "hostname": "$HOSTNAME",
              "osName": "$OS_NAME",
              "osVersion": "$OS_VERSION",
              "totalRam": $TOTAL_RAM,
              "availableRam": $AVAILABLE_RAM,
              "usedRam": $USED_RAM,
              "cpu": $CPU_COUNT,
              "cpuUsagePercent": $CPU_USAGE,
              "totalDiskSpace": $TOTAL_DISK,
              "availableDiskSpace": $AVAILABLE_DISK,
              "usedDiskSpace": $USED_DISK,
              "uptime": $UPTIME
            }
            EOF
            )
            
            # Write the JSON result to an output file (script filename + .json)
            OUTPUT_FILE="server_stats.json"
            echo "$JSON" > "$OUTPUT_FILE"
            
            # Optional: Print to console
            echo "$JSON"
            """;
    }
}
