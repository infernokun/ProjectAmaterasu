package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabFileChangeLog;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.repositories.LabFileChangeLogRepository;
import com.infernokun.amaterasu.repositories.LabRepository;
import com.infernokun.amaterasu.services.LabService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class LabFileChangeLogService {
    private final LabFileChangeLogRepository labFileChangeLogRepository;
    private final LabRepository labRepository;
    private final AmaterasuConfig amaterasuConfig;
    private final RemoteCommandService remoteCommandService;
    private final LabService labService;

    private final Logger LOGGER = LoggerFactory.getLogger(LabFileChangeLogService.class);

    public LabFileChangeLogService(LabFileChangeLogRepository labFileChangeLogRepository, LabRepository labRepository, AmaterasuConfig amaterasuConfig, RemoteCommandService remoteCommandService, LabService labService) {
        this.labFileChangeLogRepository = labFileChangeLogRepository;
        this.labRepository = labRepository;
        this.amaterasuConfig = amaterasuConfig;
        this.remoteCommandService = remoteCommandService;
        this.labService = labService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void updateLabFileStatuses() {
        AtomicInteger count = new AtomicInteger();

        LOGGER.info("Starting changelog time check...");
        List<Lab> labs = labRepository.findAll();

        List<LabFileChangeLog> logsToUpdate = new ArrayList<>();
        List<Lab> labsToUpdate = new ArrayList<>();

        for (Lab lab : labs) {
            LabFileChangeLog labFileChangeLog = labFileChangeLogRepository.findByLab(lab)
                    .orElseGet(() -> LabFileChangeLog.builder()
                            .lab(lab)
                            .upToDate(false)
                            .build());

            LocalDateTime remoteTimestamp = fetchRemoteFileTimestamp(lab);

            // Update only if remote file is newer
            if (labFileChangeLog.getUpdatedAt() == null || remoteTimestamp.isAfter(labFileChangeLog.getUpdatedAt())) {
                LOGGER.info("{} is not ready: Remote time is {}. Changelog time is {}.",
                        lab.getName(),
                        formatTimestamp(remoteTimestamp),
                        formatTimestamp(labFileChangeLog.getUpdatedAt()));

                labFileChangeLog.setUpToDate(false);
                logsToUpdate.add(labFileChangeLog);

                lab.setReady(false);
                labsToUpdate.add(lab);
                count.incrementAndGet();
            } else {
                labFileChangeLog.setUpToDate(true);
                logsToUpdate.add(labFileChangeLog);
            }
        }

        AtomicInteger count2 = new AtomicInteger();

        labFileChangeLogRepository.saveAll(logsToUpdate);
        labRepository.saveAll(labsToUpdate);

        LOGGER.info("{} files that need updating!", count.get());

        LOGGER.info("Starting file validity check...");

        labs.stream()
                .filter(lab -> lab.getLabType() == LabType.DOCKER_COMPOSE)
                .map(lab -> labFileChangeLogRepository.findByLab(lab)
                        .filter(log -> !log.isUpToDate())
                        .map(log -> {
                            boolean isLabReady = labService.checkDockerComposeValidity(lab.getId());
                            lab.setReady(isLabReady);
                            log.setUpdatedAt(LocalDateTime.now());
                            log.setUpToDate(isLabReady);
                            return new AbstractMap.SimpleEntry<>(lab, log);
                        }))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(entry -> {
                    labsToUpdate.add(entry.getKey());
                    logsToUpdate.add(entry.getValue());
                    LOGGER.info("{} readiness updated to: {}", entry.getKey().getName(), entry.getValue().isUpToDate());
                    count2.incrementAndGet();
                });

        labFileChangeLogRepository.saveAll(logsToUpdate);
        labRepository.saveAll(labsToUpdate);

        LOGGER.info("{} files updated!", count2.get());
    }


    private LocalDateTime fetchRemoteFileTimestamp(Lab lab) {
        try {
            String command = String.format(
                    "stat -c %%Y %s/%s/%s",
                    amaterasuConfig.getUploadDir(), lab.getId(), lab.getDockerFile()
            );

            RemoteCommandResponse output = remoteCommandService.handleRemoteCommand(command, amaterasuConfig);

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