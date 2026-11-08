package com.infernokun.amaterasu.services.entity.lab;

import com.infernokun.amaterasu.exceptions.LabReadinessException;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.DockerServiceInfo;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.alt.VolumeChangeRequest;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.alt.DockerService;
import com.infernokun.amaterasu.services.alt.RemoteCommandService;
import com.infernokun.amaterasu.services.entity.TeamService;
import com.infernokun.amaterasu.websocket.LabTrackerChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class LabTrackerService extends BaseService {
    private final TeamService teamService;
    private final LabTrackerRepository labTrackerRepository;
    private final RemoteCommandService remoteCommandService;
    private final DockerService dockerService;
    private final ApplicationEventPublisher events;

    public LabTrackerService(TeamService teamService, LabTrackerRepository labTrackerRepository,
                             RemoteCommandService remoteCommandService, DockerService dockerService,
                             ApplicationEventPublisher events) {
        this.teamService = teamService;
        this.labTrackerRepository = labTrackerRepository;
        this.remoteCommandService = remoteCommandService;
        this.dockerService = dockerService;
        this.events = events;
    }

    private LabTracker saveAndPublish(LabTracker labTracker) {
        LabTracker saved = labTrackerRepository.save(labTracker);
        events.publishEvent(LabTrackerChangedEvent.updated(saved));
        return saved;
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

    public List<LabTracker> findByLabStarted(Lab lab) {
        return labTrackerRepository.findByLabStarted(lab);
    }

    /** The lab's shared copy, if it has one that was not removed. */
    public Optional<LabTracker> findSharedCopy(String roomId, Lab environment) {
        return labTrackerRepository.findByRoomId(roomId).stream()
                .filter(tracker -> tracker.getLabStatus() != LabStatus.DELETED)
                .filter(tracker -> tracker.getLabStarted() != null && tracker.getLabStarted().getId().equals(environment.getId()))
                .max(java.util.Comparator.comparing(LabTracker::getUpdatedAt, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())));
    }

    /** A team's own copy of a lab definition, if it has one that was not removed. */
    public Optional<LabTracker> findTeamCopy(Lab environment, Team team) {
        return labTrackerRepository.findAllByLabStartedAndLabOwnerAndLabStatusNot(environment, team, LabStatus.DELETED).stream()
                .filter(tracker -> !tracker.isShared() && !tracker.isSolo())
                .max(java.util.Comparator.comparing(LabTracker::getUpdatedAt, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())));
    }

    /** The copy of a per-team environment that players without a team share, if it was not removed. */
    public Optional<LabTracker> findSoloCopy(Lab environment) {
        return labTrackerRepository.findByLabStarted(environment).stream()
                .filter(tracker -> tracker.getLabStatus() != LabStatus.DELETED && tracker.isSolo())
                .max(java.util.Comparator.comparing(LabTracker::getUpdatedAt, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())));
    }

    public List<LabTracker> findLabTrackerByLabStatus(LabStatus labStatus) {
        return labTrackerRepository.findByLabStatus(labStatus);
    }

    public Optional<LabTracker> findLabTrackerByLabStartedAndLabOwnerAndStatusNotDeleted(Lab labStarted, Team labOwner) {
        return this.labTrackerRepository.findLabTrackerByLabStartedAndLabOwnerAndLabStatusNot(labStarted, labOwner, LabStatus.DELETED);
    }

    public LabTracker createLabTracker(LabTracker labTracker) {
        return saveAndPublish(labTracker);
    }

    public List<LabTracker> createManyLabTrackers(List<LabTracker> labTrackers) {
        List<LabTracker> saved = labTrackerRepository.saveAll(labTrackers);
        saved.forEach(labTracker -> events.publishEvent(LabTrackerChangedEvent.updated(labTracker)));
        return saved;
    }

    public LabTracker deleteLabTracker(String id) {
        LabTracker deletedLabTracker = findLabTrackerById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lab tracker with ID " + id + " not found!"));
        labTrackerRepository.deleteById(id);
        events.publishEvent(LabTrackerChangedEvent.removed(deletedLabTracker));
        return deletedLabTracker;
    }

    @Transactional
    public boolean updateVmsIfStatus(LabTracker labTracker, List<ProxmoxVM> vms, LabStatus status, LabStatus expected) {
        boolean updated = labTrackerRepository.updateVmsIfStatus(labTracker.getId(), vms, status, expected) > 0;
        if (updated) {
            // A copy: changing the managed entity would be flushed over the conditional update
            events.publishEvent(LabTrackerChangedEvent.updated(copyWith(labTracker, vms, status)));
        }
        return updated;
    }

    private static LabTracker copyWith(LabTracker labTracker, List<ProxmoxVM> vms, LabStatus status) {
        LabTracker copy = LabTracker.builder()
                .labStarted(labTracker.getLabStarted())
                .labStatus(status)
                .labOwner(labTracker.getLabOwner())
                .services(labTracker.getServices())
                .vms(vms)
                .bridges(labTracker.getBridges())
                .remoteServer(labTracker.getRemoteServer())
                .roomId(labTracker.getRoomId())
                .build();
        copy.setId(labTracker.getId());
        copy.setCreatedBy(labTracker.getCreatedBy());
        copy.setUpdatedBy(labTracker.getUpdatedBy());
        copy.setCreatedAt(labTracker.getCreatedAt());
        copy.setUpdatedAt(labTracker.getUpdatedAt());
        copy.setVersioning(labTracker.getVersioning());
        return copy;
    }

    public LabTracker updateLabTracker(LabTracker labTracker) {
        labTracker.setUpdatedAt(LocalDateTime.now());
        return saveAndPublish(labTracker);
    }

    public String getLabLogs(LabTracker labTracker, RemoteServer remoteServer, String service) {
        switch (labTracker.getLabStarted().getLabType()) {
            case DOCKER_COMPOSE -> {
                try {
                    return dockerService.getLogs(labTracker, serverFor(labTracker, remoteServer), service);
                } catch (RemoteCommandException e) {
                    throw new LabReadinessException(e.getMessage());
                }
            }
            case DOCKER_CONTAINER -> throw new LabReadinessException("coming one day...");
            default -> throw new LabReadinessException("Lab type not implemented...");
        }
    }

    /**
     * Writes uploaded files into the tracker's volume directory and restarts the services that use
     * them. Files and volume changes are paired by their position in the request.
     */
    public List<RemoteCommandResponse> addVolumeFiles(LabTracker labTracker, RemoteServer remoteServer,
                                                      List<VolumeChangeRequest> volumeChanges,
                                                      List<MultipartFile> files) {
        if (volumeChanges.size() != files.size()) {
            throw new IllegalArgumentException("Expected " + volumeChanges.size() + " files but got " + files.size());
        }
        RemoteServer server = serverFor(labTracker, remoteServer);
        Path volumeRoot = Path.of(dockerService.trackerVolumeDir(labTracker)).normalize();

        List<RemoteCommandResponse> result = new ArrayList<>();
        Set<String> touchedServices = new LinkedHashSet<>();

        for (int i = 0; i < volumeChanges.size(); i++) {
            VolumeChangeRequest volumeChange = volumeChanges.get(i);
            Path target = Path.of(volumeChange.getTargetPath()).normalize();
            if (volumeChange.isDirectory()) {
                String fileName = Path.of(volumeChange.getFileName()).getFileName().toString();
                target = target.resolve(fileName).normalize();
            }
            if (!target.startsWith(volumeRoot) || target.equals(volumeRoot)) {
                throw new IllegalArgumentException("Upload path is outside this lab's volumes: " + volumeChange.getTargetPath());
            }

            byte[] content;
            try {
                content = files.get(i).getBytes();
            } catch (IOException e) {
                throw new IllegalStateException("Could not read uploaded file " + volumeChange.getFileName(), e);
            }

            result.add(remoteCommandService.writeRemoteFile(target.toString(), content, server));
            if (volumeChange.getServiceName() != null) {
                touchedServices.add(volumeChange.getServiceName());
            }
        }

        if (!touchedServices.isEmpty() && result.stream().allMatch(RemoteCommandResponse::isSuccess)) {
            result.add(dockerService.restartServices(labTracker, server, touchedServices));
        }
        return result;
    }

    public LabTracker refreshLabTracker(LabTracker labTracker) {
        if (labTracker.getLabStarted().getLabType() != LabType.DOCKER_COMPOSE || labTracker.getRemoteServer() == null
                || labTracker.getLabStatus() == LabStatus.STOPPED || labTracker.getLabStatus() == LabStatus.DELETED) {
            return labTracker;
        }

        Optional<List<DockerServiceInfo>> services = dockerService.inspectServices(labTracker, labTracker.getRemoteServer());
        if (services.isEmpty()) {
            return labTracker;
        }
        labTracker.setServices(services.get());

        long notRunning = services.get().stream().filter(service -> !"running".equals(service.getState())).count();
        if (labTracker.getLabStatus() == LabStatus.PAUSED && notRunning == services.get().size()) {
            return saveAndPublish(labTracker);
        }

        if (services.get().isEmpty() || notRunning == services.get().size()) {
            labTracker.setLabStatus(LabStatus.FAILED);
        } else if (notRunning > 0) {
            labTracker.setLabStatus(LabStatus.DEGRADED);
        } else {
            labTracker.setLabStatus(LabStatus.ACTIVE);
        }

        return saveAndPublish(labTracker);
    }

    /** The server a tracker was deployed on wins over whatever server the client sent. */
    public static RemoteServer serverFor(LabTracker labTracker, RemoteServer requested) {
        return labTracker.getRemoteServer() != null ? labTracker.getRemoteServer() : requested;
    }

    public void deleteAll(List<LabTracker> labTrackers) {
        this.labTrackerRepository.deleteAll(labTrackers);
        labTrackers.forEach(labTracker -> events.publishEvent(LabTrackerChangedEvent.removed(labTracker)));
    }
}
