package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.LabReadinessException;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.dto.VolumeChangeDTO;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.repositories.LabTrackerRepository;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.alt.RemoteCommandService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class LabTrackerService extends BaseService {
    private final TeamService teamService;
    private final LabTrackerRepository labTrackerRepository;
    private final AmaterasuConfig amaterasuConfig;
    private final RemoteCommandService remoteCommandService;

    public LabTrackerService(TeamService teamService, LabTrackerRepository labTrackerRepository, AmaterasuConfig amaterasuConfig, RemoteCommandService remoteCommandService) {
        this.teamService = teamService;
        this.labTrackerRepository = labTrackerRepository;
        this.amaterasuConfig = amaterasuConfig;
        this.remoteCommandService = remoteCommandService;
    }

    public List<LabTracker> findAllLabTrackers() {
        return labTrackerRepository.findAll();
    }

    public Optional<LabTracker> findLabTrackerById(String id) {
        return labTrackerRepository.findById(id);
    }

    public List<LabTracker> findLabTrackerByTeamId(String teamId) {
        Team labOwner = teamService.findTeamById(teamId);
        return labTrackerRepository.findLabTrackersByLabOwner(labOwner);
    }

    public List<LabTracker> findLabTrackerByLabStatus(LabStatus labStatus) {
        return labTrackerRepository.findByLabStatus(labStatus);
    }

    public Optional<LabTracker> findLabTrackerByLabStartedAndLabOwnerAndStatusNotDeleted(Lab labStarted, Team labOwner) {
        return this.labTrackerRepository.findLabTrackerByLabStartedAndLabOwnerAndLabStatusNot(labStarted, labOwner, LabStatus.DELETED);
    }

    public LabTracker createLabTracker(LabTracker labTracker) {
        return labTrackerRepository.save(labTracker);
    }

    public List<LabTracker> createManyLabTrackers(List<LabTracker> labTrackers) {
        return labTrackerRepository.saveAll(labTrackers);
    }

    public LabTracker deleteLabTracker(String id) {
        Optional<LabTracker> deletedLabTrackerOptional = findLabTrackerById(id);

        if (deletedLabTrackerOptional.isEmpty()) {
            throw new ResourceNotFoundException("Lab tracker with ID " + id + " not found!");
        }

        LabTracker deletedLabTracker = deletedLabTrackerOptional.get();
        labTrackerRepository.deleteById(id);
        return deletedLabTracker;
    }

    public LabTracker updateLabTracker(LabTracker labTracker) {
        findLabTrackerById(labTracker.getId());
        labTracker.setUpdatedAt(LocalDateTime.now());
        return labTrackerRepository.save(labTracker);
    }

    public String getLabLogs(LabTracker labTracker, RemoteServer remoteServer) {
        switch (labTracker.getLabStarted().getLabType()) {
            case DOCKER_COMPOSE -> {
                return getTrackerLabLogs(labTracker, remoteServer);
            }
            case DOCKER_CONTAINER -> {
                throw new LabReadinessException("coming one day...");
            }
            default -> throw new LabReadinessException("Lab type not implemented...");
        }
    }

    public String getTrackerLabLogs(LabTracker labTracker, RemoteServer dockerServer) {
        try {
            String cmd = String.format("docker-compose -p %s logs", labTracker.getId());

            RemoteCommandResponse remoteCommandResponse = remoteCommandService.handleRemoteCommand(cmd, dockerServer);

            return remoteCommandResponse.getBoth();
        } catch (RemoteCommandException e) {
            throw new LabReadinessException(e.getMessage());
        }
    }

    private String getString(Object object) {
        String cmd;
        if (object instanceof Lab) {
            cmd = String.format("DIR=%s/%s && cd $DIR && cat %s",
                    amaterasuConfig.getUploadDir(), ((Lab) object).getId(), ((Lab) object).getDockerFile());
        } else if (object instanceof LabTracker) {
            cmd = String.format("DIR=%s/tracker-compose && cd $DIR && cat %s",
                    amaterasuConfig.getUploadDir(), ((LabTracker) object).getId() + "_" + ((LabTracker) object)
                            .getLabStarted().getDockerFile());
        } else {
            throw new IllegalArgumentException("Unsupported lab type: " + object.getClass().getName());
        }
        return cmd;
    }

    public List<RemoteCommandResponse> addVolumeFiles(LabTracker labTracker, RemoteServer remoteServer,
                                                      List<VolumeChangeDTO> volumeChanges,
                                                      List<MultipartFile> files) {
        List<RemoteCommandResponse> result = new ArrayList<>();

        volumeChanges.forEach(volumeChange -> {
            String content = "";
            try {
                content = new String(files.get(volumeChange.getIndex()).getBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String createFileCmd = "";
            if (volumeChange.isDirectory()) {
                createFileCmd = String.format("DIR=%s && cd $DIR && echo \"%s\" | tee %s", volumeChange.getTargetPath(), content, volumeChange.getFileName());
            } else {
                createFileCmd = String.format("FILE=%s && rmdir $FILE && echo \"%s\"  | tee $FILE", volumeChange.getTargetPath(), content);
            }


            RemoteCommandResponse remoteCommandResponse = remoteCommandService.handleRemoteCommand(createFileCmd, remoteServer);

            result.add(remoteCommandResponse);
        });
        return result;
    }


    public void deleteAll() {
        this.labTrackerRepository.deleteAll();
    }
}
