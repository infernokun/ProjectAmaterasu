package com.infernokun.amaterasu.services.alt.cron;

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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

        remoteServerService.findByServerType(ServerType.DOCKER_HOST).forEach(dockerServer -> {
            LOGGER.info("Starting changelog time check... for {}", dockerServer.getName());

            for (Lab lab : labService.findByLabType(LabType.DOCKER_COMPOSE)) {
                LabFileChangeLog labFileChangeLog = labFileChangeLogRepository.findByLab(lab)
                        .orElseGet(() -> LabFileChangeLog.builder()
                                .lab(lab)
                                .build());

                if (labFileChangeLog.getId() == null) {
                    lab.setUpdatedAt(LocalDateTime.now().minusYears(10));
                    labFileChangeLog = this.labFileChangeLogRepository.save(labFileChangeLog);
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
            LOGGER.info("Starting file validity check...");

            labFileChangeLogRepository.findAll().stream()
                    .filter(labFileChangeLog -> labFileChangeLog.getLab().getLabType() == LabType.DOCKER_COMPOSE)
                    .filter(labFileChangeLog -> !labFileChangeLog.getLab().isReady() )
                    .forEach(dockerComposeLog -> {
                        boolean isLabReady = labService.checkDockerComposeValidity(dockerComposeLog.getLab().getId(), dockerServer);
                        dockerComposeLog.getLab().setReady(isLabReady);
                        dockerComposeLog.setUpdatedAt(LocalDateTime.now());
                        labService.updateLab(dockerComposeLog.getLab());
                        labFileChangeLogRepository.save(dockerComposeLog);

                        LOGGER.info("{} readiness updated to: {}", dockerComposeLog.getLab().getName(),
                                dockerComposeLog.getLab().isReady());

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

            RemoteCommandResponse fetchRemoteFileTimestampOutput = remoteCommandService.handleRemoteCommand(
                    command, remoteServer);

            String cmdOutput = fetchRemoteFileTimestampOutput.getBoth().trim();

            if (fetchRemoteFileTimestampOutput.getExitCode() != 0) {
                throw new RemoteCommandException("fetchRemoteFileTimestamp: Command exited (" + cmdOutput + ")");
            }


            if (cmdOutput.contains("No such file")) {
                throw new RemoteCommandException("fetchRemoteFileTimestamp: File not found");
            }

            long timestamp = Long.parseLong(cmdOutput);

            return Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (RemoteCommandException | NumberFormatException e) {
            LOGGER.error(e.getMessage());
            return LocalDateTime.MIN;
        }
    }

    private String formatTimestamp(LocalDateTime timestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yy hh:mma");
        return timestamp.format(formatter);
    }
}