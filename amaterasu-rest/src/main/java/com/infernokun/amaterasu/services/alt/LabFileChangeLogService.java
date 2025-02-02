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
import java.util.List;
import java.util.Optional;

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
        LOGGER.info("Starting changelog update...");
        List<Lab> labs = labRepository.findAll();

        for (Lab lab : labs) {
            Optional<LabFileChangeLog> optionalLog =
                    labFileChangeLogRepository.findByLab(lab);
            LocalDateTime remoteTimestamp = fetchRemoteFileTimestamp(lab);

            if (optionalLog.isPresent()) {
                LabFileChangeLog changeLog = optionalLog.get();

                // Update only if remote file is updated later
                if (remoteTimestamp.isAfter(changeLog.getUpdatedAt())) {
                    changeLog.setUpToDate(false);
                    changeLog.setUpdatedAt(LocalDateTime.now());
                    labFileChangeLogRepository.save(changeLog);

                    lab.setReady(false);
                    labService.updateLab(lab);
                    LOGGER.info("Updated changelog for lab: {}", lab.getId());
                }
            } else {
                // Create and save new log if none exists.
                LabFileChangeLog newLog = LabFileChangeLog.builder()
                        .lab(lab)
                        .upToDate(true)
                        .build();
                labFileChangeLogRepository.save(newLog);
                LOGGER.info("Created new changelog for lab: {}", lab.getId());
            }
        }
    }

    @Scheduled(cron = "15 * * * * *") // Example cron: runs every minute at second 15
    public void checkLabReadiness() {
        List<Lab> labs = labRepository.findAll();

        labs.forEach(lab -> {
            Optional<LabFileChangeLog> labFileChangeLogOptional =
                    labFileChangeLogRepository.findByLab(lab);

            if (labFileChangeLogOptional.isPresent() && lab.getLabType() == LabType.DOCKER_COMPOSE) {
                LabFileChangeLog labFileChangeLog = labFileChangeLogOptional.get();
                if (!labFileChangeLog.isUpToDate()) {
                    boolean isLabReady = labService.checkDockerComposeValidity(lab.getId());
                    lab.setReady(isLabReady);

                    labService.updateLab(lab);

                    labFileChangeLog.setUpdatedAt(LocalDateTime.now());
                    labFileChangeLog.setUpToDate(isLabReady);

                    labFileChangeLogRepository.save(labFileChangeLog);
                    LOGGER.info("Lab {} readiness updated to: {}", lab.getId(), isLabReady);
                }
            }
        });
    }


    public Optional<LabFileChangeLog> findByLab(Lab lab) {
        return labFileChangeLogRepository.findByLab(lab);
    }

    private LocalDateTime fetchRemoteFileTimestamp(Lab lab) {
        try {
            String command = String.format(
                    "stat -c %%Y %s/%s/%s",
                    amaterasuConfig.getDockerUser(), lab.getId(), lab.getDockerFile()
            );

            RemoteCommandResponse output = remoteCommandService.handleRemoteCommand(command, amaterasuConfig);

            LOGGER.info("Heres the output: {}", output.getBoth().trim());
            long timestamp = Long.parseLong(output.getBoth().trim());

            return Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (RemoteCommandException | NumberFormatException e) {
            return LocalDateTime.now();
        }
    }
}