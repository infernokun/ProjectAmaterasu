package com.infernokun.amaterasu.services.alt.cron;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabFileChangeLog;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.repositories.LabFileChangeLogRepository;
import com.infernokun.amaterasu.repositories.LabRepository;
import com.infernokun.amaterasu.services.entity.LabService;
import com.infernokun.amaterasu.services.alt.RemoteCommandService;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.entity.RemoteServerService;
import jakarta.transaction.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LabFileChangeLogService extends BaseService {
    private final LabFileChangeLogRepository labFileChangeLogRepository;
    private final LabRepository labRepository;
    private final AmaterasuConfig amaterasuConfig;
    private final RemoteCommandService remoteCommandService;
    private final RemoteServerService remoteServerService;
    private final LabService labService;

    public LabFileChangeLogService(LabFileChangeLogRepository labFileChangeLogRepository, LabRepository labRepository, AmaterasuConfig amaterasuConfig, RemoteCommandService remoteCommandService, RemoteServerService remoteServerService, LabService labService) {
        this.labFileChangeLogRepository = labFileChangeLogRepository;
        this.labRepository = labRepository;
        this.amaterasuConfig = amaterasuConfig;
        this.remoteCommandService = remoteCommandService;
        this.remoteServerService = remoteServerService;
        this.labService = labService;
    }
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void checkLabFileStats() {
        AtomicInteger count = new AtomicInteger();
        List<RemoteServer> remoteServers =  remoteServerService.getAllServers();

        List<RemoteServer> dockerServers = remoteServers.stream().filter((remoteServer ->
                remoteServer.getServerType() == ServerType.DOCKER_HOST)).toList();

        dockerServers.forEach(dockerServer -> {
            LOGGER.info("Starting changelog time check...");

            for (Lab lab : labService.findByLabType(LabType.DOCKER_COMPOSE)) {
                LabFileChangeLog labFileChangeLog = labFileChangeLogRepository.findByLab(lab)
                        .orElseGet(() -> LabFileChangeLog.builder()
                                .lab(lab)
                                .upToDate(false)
                                .build());

                if (labFileChangeLog.getId() == null) {
                    lab.setUpdatedAt(LocalDateTime.now().minusYears(10));
                    labFileChangeLog = this.labFileChangeLogRepository.save(labFileChangeLog);
                }

                LocalDateTime remoteTimestamp = fetchRemoteFileTimestamp(lab, dockerServer);

                // Update only if remote file is newer
                if (labFileChangeLog.getUpdatedAt() == null ||
                        remoteTimestamp.isAfter(labFileChangeLog.getUpdatedAt())) {
                    LOGGER.info("{} needs to be checked and is not ready: Remote time is {}. Changelog time is {}.",
                            lab.getName(),
                            formatTimestamp(remoteTimestamp),
                            formatTimestamp(labFileChangeLog.getUpdatedAt()));

                    labFileChangeLog.setUpToDate(false);

                    lab.setReady(false);
                    labService.updateLab(lab);
                    labFileChangeLogRepository.save(labFileChangeLog);
                    count.incrementAndGet();
                } else {
                    labFileChangeLog.setUpToDate(true);
                    labFileChangeLogRepository.save(labFileChangeLog);
                }
            }

            LOGGER.info("{} files that need updating!", count.get());

            AtomicInteger count2 = new AtomicInteger();
            LOGGER.info("Starting file validity check...");

            labFileChangeLogRepository.findAll().stream()
                    .filter(labFileChangeLog -> labFileChangeLog.getLab().getLabType() == LabType.DOCKER_COMPOSE)
                    .filter(labFileChangeLog -> !labFileChangeLog.getLab().isReady() )
                    .forEach(dockerComposeLog -> {
                        boolean isLabReady = labService.checkDockerComposeValidity(dockerComposeLog.getLab().getId(), dockerServer);
                        dockerComposeLog.getLab().setReady(isLabReady);
                        dockerComposeLog.setUpdatedAt(LocalDateTime.now());
                        dockerComposeLog.setUpToDate(isLabReady);
                        labService.updateLab(dockerComposeLog.getLab());
                        labFileChangeLogRepository.save(dockerComposeLog);

                        LOGGER.info("{} readiness updated to: {}", dockerComposeLog.getLab().getName(), dockerComposeLog.isUpToDate());

                        if (isLabReady) count2.incrementAndGet();
                    });

            LOGGER.info("{} files updated!", count2.get());
        });
    }


    private LocalDateTime fetchRemoteFileTimestamp(Lab lab, RemoteServer remoteServer) {
        try {
            String command = String.format(
                    "stat -c %%Y %s/%s/%s",
                    amaterasuConfig.getUploadDir(), lab.getId(), lab.getDockerFile()
            );

            RemoteCommandResponse output = remoteCommandService.handleRemoteCommand(command, remoteServer);

            long timestamp = Long.parseLong(output.getBoth().trim());

            return Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (RemoteCommandException | NumberFormatException e) {
            return LocalDateTime.MIN;
        }
    }

    private String formatTimestamp(LocalDateTime timestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yy hh:mma");
        return timestamp.format(formatter);
    }
}