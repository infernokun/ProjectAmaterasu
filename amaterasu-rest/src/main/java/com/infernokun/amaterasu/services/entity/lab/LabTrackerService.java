package com.infernokun.amaterasu.services.entity.lab;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.LabReadinessException;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.DockerServiceInfo;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.alt.VolumeChangeRequest;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.alt.RemoteCommandService;
import com.infernokun.amaterasu.services.entity.TeamService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static com.infernokun.amaterasu.services.alt.DockerService.parseDockerInspectOutput;

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

    public String getLabLogs(LabTracker labTracker, RemoteServer remoteServer, String service) {
        switch (labTracker.getLabStarted().getLabType()) {
            case DOCKER_COMPOSE -> {
                return getTrackerLabLogs(labTracker, remoteServer, service);
            }
            case DOCKER_CONTAINER -> {
                throw new LabReadinessException("coming one day...");
            }
            default -> throw new LabReadinessException("Lab type not implemented...");
        }
    }

    public String getTrackerLabLogs(LabTracker labTracker, RemoteServer dockerServer, String service) {
        try {
            // Resolve single-service logs through compose so the project-scoped container
            // is found; "docker logs <service>" used the bare service name and never matched
            // the actual container (named <project>_<service>_N).
            String cmd = service.equals("all") ? String.format("docker-compose -p %s logs", labTracker.getId()) :
                    String.format("docker-compose -p %s logs %s", labTracker.getId(), service);

            RemoteCommandResponse remoteCommandResponse = remoteCommandService.handleRemoteCommand(cmd, dockerServer);

            return remoteCommandResponse.getBoth();
        } catch (RemoteCommandException e) {
            throw new LabReadinessException(e.getMessage());
        }
    }

    public List<RemoteCommandResponse> addVolumeFiles(LabTracker labTracker, RemoteServer remoteServer,
                                                      List<VolumeChangeRequest> volumeChanges,
                                                      List<MultipartFile> files) {
        List<RemoteCommandResponse> result = new ArrayList<>();

        volumeChanges.forEach(volumeChange -> {
            String content;
            try {
                content = new String(files.get(volumeChange.getIndex()).getBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // For a directory target the uploaded file lives inside it; for a file target the
            // path IS the file. Previously the file branch ran "rmdir $FILE" (which errors on a
            // regular file) and both branches used echo-injection. writeRemoteFile is base64-safe
            // and creates parent directories as needed.
            String remotePath = volumeChange.isDirectory()
                    ? volumeChange.getTargetPath() + "/" + volumeChange.getFileName()
                    : volumeChange.getTargetPath();

            result.add(remoteCommandService.writeRemoteFile(remotePath, content, remoteServer));
        });
        return result;
    }

    public LabTracker refreshLabTracker(LabTracker labTracker) {
        // Check the Docker process status
        String checkTrackerProcessCmd = String.format("cd %s/tracker-compose && docker-compose -p %s ps -qa | xargs docker inspect",
                amaterasuConfig.getUploadDir(), labTracker.getId());
        RemoteCommandResponse checkTrackerProcessOutput = remoteCommandService.handleRemoteCommand(checkTrackerProcessCmd, labTracker.getRemoteServer());

        if (checkTrackerProcessOutput.getExitCode() != 0) {
            return labTracker;
        }

        labTracker.setServices(parseDockerInspectOutput(checkTrackerProcessOutput.getOutput().trim()));

        List<DockerServiceInfo> services = labTracker.getServices().stream().filter(service -> !service.getState().equals("running")).toList();

        if (services.size() == labTracker.getServices().size()) {
            labTracker.setLabStatus(LabStatus.FAILED);
        } else if (services.size() < labTracker.getServices().size() && !services.isEmpty()) {
            labTracker.setLabStatus(LabStatus.DEGRADED);
        } else {
            labTracker.setLabStatus(LabStatus.ACTIVE);
        }
        
        return labTrackerRepository.save(labTracker);
    }

    public void deleteAll() {
        this.labTrackerRepository.deleteAll();
    }
}
