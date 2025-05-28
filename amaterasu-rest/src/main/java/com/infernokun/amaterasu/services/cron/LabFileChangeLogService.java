package com.infernokun.amaterasu.services.cron;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabFileChangeLog;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.repositories.lab.LabFileChangeLogRepository;
import com.infernokun.amaterasu.services.entity.lab.LabService;
import com.infernokun.amaterasu.services.alt.RemoteCommandService;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.entity.lab.RemoteServerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LabFileChangeLogService extends BaseService {
    private final LabFileChangeLogRepository labFileChangeLogRepository;
    private final AmaterasuConfig amaterasuConfig;
    private final RemoteCommandService remoteCommandService;
    private final RemoteServerService remoteServerService;
    private final LabService labService;

    public LabFileChangeLogService(LabFileChangeLogRepository labFileChangeLogRepository,
                                   AmaterasuConfig amaterasuConfig, RemoteCommandService remoteCommandService,
                                   RemoteServerService remoteServerService, LabService labService) {
        this.labFileChangeLogRepository = labFileChangeLogRepository;
        this.amaterasuConfig = amaterasuConfig;
        this.remoteCommandService = remoteCommandService;
        this.remoteServerService = remoteServerService;
        this.labService = labService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void checkLabFileStats() {
        AtomicInteger count = new AtomicInteger();

        remoteServerService.findByServerType(ServerType.DOCKER_HOST).forEach(dockerServer -> {
            for (Lab lab : labService.findByLabType(LabType.DOCKER_COMPOSE)) {
                LabFileChangeLog labFileChangeLog = findByLab(lab)
                        .orElseGet(() -> LabFileChangeLog.builder()
                                .lab(lab)
                                .build());

                if (labFileChangeLog.getId() == null) {
                    lab.setUpdatedAt(LocalDateTime.now().minusYears(10));
                    labFileChangeLog = updateLabFileChangeLog(labFileChangeLog);
                }

                LocalDateTime remoteTimestamp = fetchRemoteFileTimestamp(lab, dockerServer);

                // Update only if remote file is newer
                if (labFileChangeLog.getUpdatedAt() == null ||
                        remoteTimestamp.isAfter(labFileChangeLog.getUpdatedAt()) ||
                        remoteTimestamp == LocalDateTime.MIN || !lab.isReady()) {
                    LOGGER.warn("For file {} the timestamp is {}, but db is {}",
                            lab.getDockerFile(), remoteTimestamp, labFileChangeLog.getUpdatedAt());


                    lab.setReady(false);
                    labService.updateLab(lab);
                    count.incrementAndGet();
                }
            }

            LOGGER.info("{} files that need updating!", count.get());

            // Validate
            AtomicInteger count2 = new AtomicInteger();

            findAllLabFileChangeLogs().stream()
                    .filter(labFileChangeLog -> labFileChangeLog.getLab().getLabType() == LabType.DOCKER_COMPOSE)
                    .filter(labFileChangeLog -> !labFileChangeLog.getLab().isReady())
                    .forEach(dockerComposeLog -> {
                        boolean isLabReady = labService.checkDockerComposeValidity(dockerComposeLog.getLab().getId(), dockerServer);
                        dockerComposeLog.getLab().setReady(isLabReady);
                        dockerComposeLog.setUpdatedAt(LocalDateTime.now());
                        labService.updateLab(dockerComposeLog.getLab());
                        updateLabFileChangeLog(dockerComposeLog);

                        LOGGER.info("{} readiness updated to: {}", dockerComposeLog.getLab().getName(),
                                dockerComposeLog.getLab().isReady());

                        if (isLabReady) count2.incrementAndGet();
                    });

            LOGGER.info("{} files updated!", count2.get());
        });
    }

    private LocalDateTime fetchRemoteFileTimestamp(Lab lab, RemoteServer remoteServer) {
        try {
            String statsCmd = String.format("stat -c %%Y %s/%s/%s",
                    amaterasuConfig.getUploadDir(), lab.getId(), lab.getDockerFile()
            );

            RemoteCommandResponse response = remoteCommandService.handleRemoteCommand(
                    statsCmd, remoteServer);

            if (!response.isSuccess()) {
                LOGGER.warn("Failed to execute command on server {}: {}", remoteServer.getId(), response.getError());
                return LocalDateTime.MIN;
            }

            String output = response.getBoth().trim();
            if (output.contains("No such file")) {
                LOGGER.warn("File not found on server {}: {}", remoteServer.getId(), statsCmd);
                return LocalDateTime.MIN;
            }

            return Instant.ofEpochSecond(Long.parseLong(output))
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

        } catch (RemoteCommandException | NumberFormatException e) {
            LOGGER.error("fetchRemoteFileTimestamp: {}", e.getMessage());
            return LocalDateTime.MIN;
        }
    }

    public List<LabFileChangeLog> findAllLabFileChangeLogs() {
        return labFileChangeLogRepository.findAll();
    }

    public Optional<LabFileChangeLog> findByLab(Lab lab) {
        return labFileChangeLogRepository.findByLab(lab);
    }

    public LabFileChangeLog updateLabFileChangeLog(LabFileChangeLog labFileChangeLog) {
        return labFileChangeLogRepository.save(labFileChangeLog);
    }

    private String formatTimestamp(LocalDateTime timestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yy hh:mma");
        return timestamp.format(formatter);
    }
}