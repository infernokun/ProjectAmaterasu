package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.FileUploadException;
import com.infernokun.amaterasu.exceptions.LabReadinessException;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.dto.LabDTO;
import com.infernokun.amaterasu.models.entities.*;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.repositories.LabFileChangeLogRepository;
import com.infernokun.amaterasu.repositories.LabRepository;
import com.infernokun.amaterasu.services.alt.LabFileUploadService;
import com.infernokun.amaterasu.services.alt.LabActionService;
import com.infernokun.amaterasu.services.alt.LabReadinessService;
import com.infernokun.amaterasu.services.BaseService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class LabService extends BaseService {
    private final LabRepository labRepository;

    private final UserService userService;
    private final TeamService teamService;
    private final LabTrackerService labTrackerService;
    private final LabActionService labActionService;
    private final LabFileUploadService labFileUploadService;
    private final LabReadinessService labReadinessService;
    private final LabFileChangeLogRepository labFileChangeLogRepository;

    private final AmaterasuConfig amaterasuConfig;

    public LabService(
            LabRepository labRepository, UserService userService,
            TeamService teamService, LabTrackerService labTrackerService,
            LabActionService labActionService, LabFileUploadService labFileUploadService,
            LabReadinessService labReadinessService, LabFileChangeLogRepository labFileChangeLogRepository,
            AmaterasuConfig amaterasuConfig) {
        this.labRepository = labRepository;
        this.userService = userService;
        this.teamService = teamService;
        this.labTrackerService = labTrackerService;
        this.labActionService = labActionService;
        this.labFileUploadService = labFileUploadService;
        this.labReadinessService = labReadinessService;
        this.labFileChangeLogRepository = labFileChangeLogRepository;
        this.amaterasuConfig = amaterasuConfig;
    }

    public List<Lab> findAllLabs() {
        return labRepository.findAll();
    }

    public Lab findLabById(String id) {
        return labRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lab " + id + " not found"));
    }

    public List<Lab> findByLabType(LabType labType) {
        return labRepository.findByLabType(labType);
    }

    public Lab createLab(Lab lab) {
        // Format the current date-time for a cleaner string (e.g., "2025-01-25T01:51:23")
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        // Construct the dockerFile name with cleaned-up name and timestamp
        lab.setDockerFile(lab.getName().toLowerCase().replace(" ", "-") + "_" + timestamp + ".yml");
        lab.setReady(false);

        // Save the lab with the updated dockerFile name
        return labRepository.save(lab);
    }

    public Lab createLab(LabDTO labDTO, RemoteServer remoteServer) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        Lab newLab = null;

        switch (labDTO.getLabType()) {
            case DOCKER_COMPOSE -> {

                newLab = Lab.builder()
                        .name(labDTO.getName())
                        .description(labDTO.getDescription())
                        .labType(labDTO.getLabType())
                        .ready(false)
                        .dockerFile(labDTO.getName().toLowerCase().replace(" ", "-") + "_" + timestamp + ".yml")
                        .version(labDTO.getVersion())
                        .capacity(labDTO.getCapacity())
                        .build();
                newLab.setCreatedBy(labDTO.getCreatedBy());
                Lab savedLab = labRepository.save(newLab);
                uploadLabFile(savedLab.getId(), labDTO.getDockerFile(), remoteServer);

                LabFileChangeLog labFileChangeLog = new LabFileChangeLog(savedLab);
                labFileChangeLog.setUpdatedAt(LocalDateTime.now().minusYears(10));
                labFileChangeLogRepository.save(labFileChangeLog);

                return savedLab;
            }
            case KUBERNETES -> throw new RuntimeException("Kube! Coming one day....");
            case VIRTUAL_MACHINE -> {
                newLab = Lab.builder()
                        .name(labDTO.getName())
                        .description(labDTO.getDescription())
                        .labType(labDTO.getLabType())
                        .ready(true)
                        .version(labDTO.getVersion())
                        .capacity(labDTO.getCapacity())
                        .vmIds(labDTO.getVms())
                        .status(LabStatus.ACTIVE)
                        .build();
                newLab.setCreatedBy(labDTO.getCreatedBy());
                return labRepository.save(newLab);
            }
            case DOCKER_CONTAINER -> throw new RuntimeException("Docker Container! Coming one day....");
            default -> {
                throw new RuntimeException("Coming one day....");
            }
        }
    }

    public List<Lab> createManyLabs(List<Lab> labs) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        for (Lab lab : labs) {
            lab.setDockerFile(lab.getName().toLowerCase().replace(" ", "-") + "_" + timestamp + ".yml");
        }
        return labRepository.saveAll(labs);
    }

    public Lab updateLab(Lab updatedLabData) {
        findLabById(updatedLabData.getId());
        updatedLabData.setUpdatedAt(LocalDateTime.now());
        return labRepository.save(updatedLabData);
    }

    public String uploadLabFile(String labId, String content, RemoteServer remoteServer) {
        Lab lab = findLabById(labId);

        switch (lab.getLabType()) {
            case DOCKER_COMPOSE -> {
                return labFileUploadService.uploadDockerComposeFile(lab, content, remoteServer);
            }
            case DOCKER_CONTAINER -> {
                throw new FileUploadException("coming one day...");
            }
            default -> throw new FileUploadException("Lab type not implemented...");
        }
    }

    @Transactional(dontRollbackOn = ResourceNotFoundException.class)
    public LabActionResult startLab(String labId, String userId, String labTrackerId, RemoteServer remoteServer) {
        LOGGER.info("lab id {} user id {}, lab tracker id {}", labId, userId, labTrackerId);

        Lab lab = findLabById(labId);
        User user = this.userService.findUserById(userId);
        Team userTeam = user.getTeam();
        LOGGER.info("Starting lab...");

        Optional<LabTracker> labTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);

        LabTracker labTracker = labTrackerOptional.orElseGet(() ->
                LabTracker.builder()
                        .labStarted(lab)
                        .labOwner(userTeam)
                        .build()
        );

        if (remoteServer.getRemoteServerStats().getStatus() != LabStatus.ACTIVE) {
            return new LabActionResult(false, labTracker,
                    remoteServer.getName() + " is not ACTIVE!");
        }


        if (labTracker.getId() == null) {
            labTracker = labTrackerService.createLabTracker(labTracker);
        }

        LabActionResult labActionResult = labActionService.startLab(labTracker, remoteServer);
        labActionResult.getLabTracker().setUpdatedAt(LocalDateTime.now());
        labActionResult.getLabTracker().setUpdatedBy(user.getId());
        labActionResult.getLabTracker().setLabStatus(labActionResult.isSuccessful() ? LabStatus.ACTIVE : LabStatus.FAILED);

        if (labActionResult.getLabTracker().getLabStatus().equals(LabStatus.ACTIVE)) {
            AtomicBoolean inactiveService = new AtomicBoolean(false);
            labActionResult.getLabTracker().getServices().forEach((service -> {
                if (!service.getState().equals("running")) {
                    inactiveService.set(true);
                }
            }));

            if (inactiveService.get()) {
                labActionResult.getLabTracker().setLabStatus(LabStatus.DEGRADED);
            }
        }

        labActionResult.getLabTracker().setRemoteServer(remoteServer);

        try {
            labTrackerService.updateLabTracker(labActionResult.getLabTracker());
        } catch (Exception e) {
            labActionResult.setSuccessful(false);
            return labActionResult;
        }

        if (!userTeam.getTeamActiveLabs().contains(labActionResult.getLabTracker().getId())) {
            userTeam.getTeamActiveLabs().add(labActionResult.getLabTracker().getId());
            try {
                teamService.updateTeam(userTeam);
            } catch (Exception e) {
                labActionResult.setSuccessful(false);
                return labActionResult;
            }
        }
        return labActionResult;
    }

    @Transactional
    public LabActionResult stopLab(String labId, String userId, String labTrackerId, RemoteServer remoteServer) {
        LOGGER.info("Stopping lab with id {} by user id {} and lab tracker id {}", labId, userId, labTrackerId);

        Optional<LabTracker> existingLabTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);

        LabTracker existingLabTracker;
        if (existingLabTrackerOptional.isPresent()) {
            existingLabTracker = existingLabTrackerOptional.get();
        } else {
            return new LabActionResult(false, null, "Lab tracker not found");
        }

        LOGGER.info("Stopping lab...");

        String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy @ hh:mma"));

        LabActionResult labActionResult = labActionService.stopLab(findLabById(labId), existingLabTracker, remoteServer);

        labActionResult.getLabTracker().setUpdatedAt(LocalDateTime.now());
        labActionResult.getLabTracker().setUpdatedBy(userService.findUserById(userId).getId());
        labActionResult.getLabTracker().setLabStatus(labActionResult.isSuccessful() ? LabStatus.STOPPED : LabStatus.ACTIVE);

        labTrackerService.updateLabTracker(labActionResult.getLabTracker());

        return labActionResult;
    }

    @Transactional
    public LabActionResult deleteLab(String labId, String userId, String labTrackerId, RemoteServer remoteServer) {
        LOGGER.info("Deleting lab with id {} by user id {} and lab tracker id {}", labId, userId, labTrackerId);

        Optional<LabTracker> existingLabTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);

        LabTracker existingLabTracker;
        if (existingLabTrackerOptional.isPresent()) {
            existingLabTracker = existingLabTrackerOptional.get();
        } else {
            return new LabActionResult(false, null, "Lab tracker not found");
        }

        Team userTeam = userService.findUserById(userId).getTeam();

        LOGGER.info("Deleting lab...");

        LabActionResult labActionResult = labActionService.deleteLab(existingLabTracker, remoteServer);

        if (labActionResult.isSuccessful()) {
            try {
                existingLabTracker.setLabStatus(LabStatus.DELETED);
                userTeam.getTeamActiveLabs().remove(existingLabTracker.getId());
                userTeam.getTeamDeletedLabs().add(existingLabTracker.getId());
                teamService.updateTeam(userTeam);
                labTrackerService.updateLabTracker(existingLabTracker);
            } catch (Exception e) {
                throw new RuntimeException("Error during lab deletion: " + e.getMessage());
            }
        } else {
            LOGGER.warn("Lab deletion failed for lab {}", labId);
        }

        return labActionResult;
    }


    private Optional<LabTracker> handleLabStop(Lab lab, User user, LabTracker existingLabTracker, boolean successfulStop, LocalDateTime now, String formattedDate) {
        existingLabTracker.setUpdatedAt(now);
        existingLabTracker.setUpdatedBy(user.getId());

        if (!successfulStop) {
            existingLabTracker.setLabStatus(LabStatus.FAILED);
            labTrackerService.updateLabTracker(existingLabTracker);
            LOGGER.info("{} failed stop by {} at {}, status is {}", lab.getName(), user.getUsername(), formattedDate, existingLabTracker.getLabStatus());
            return Optional.of(existingLabTracker);
        }

        existingLabTracker.setLabStatus(LabStatus.STOPPED);
        labTrackerService.updateLabTracker(existingLabTracker);
        LOGGER.info("{} stopped by {} at {}, status is {}", lab.getName(), user.getUsername(), formattedDate, existingLabTracker.getLabStatus());
        return Optional.of(existingLabTracker);
    }

    public LabTracker deleteLabFromTeam(String labId, String userId, String labTrackerId) {
        Lab stoppedLab = this.findLabById(labId);
        User stoppedBy = this.userService.findUserById(userId);

        Team userTeam = stoppedBy.getTeam();

        if (userTeam.getTeamActiveLabs().contains(labTrackerId)) {
            userTeam.getTeamDeletedLabs().add(labTrackerId);
            userTeam.getTeamActiveLabs().remove(labTrackerId);

            Optional<LabTracker> labTrackerOptional = labTrackerService
                    .findLabTrackerById(labTrackerId);

            if (labTrackerOptional.isEmpty()) {
                throw new ResourceNotFoundException("Lab tracker not found!");
            }

            LabTracker labTracker = labTrackerOptional.get();
            labTracker.setLabStatus(LabStatus.DELETED);

            LabTracker updatedLabTracker = labTrackerService.updateLabTracker(labTracker);
            teamService.updateTeam(userTeam);

            return updatedLabTracker;
        }
        throw new ResourceNotFoundException("Lab not found in this team!");
    }

    public boolean checkDockerComposeValidity(String labId, RemoteServer remoteServer) {
        Lab lab = findLabById(labId);
        try {
            switch (lab.getLabType()) {
                case DOCKER_COMPOSE -> {
                    return labReadinessService.checkDockerComposeReadiness(lab, remoteServer);
                }
                case DOCKER_CONTAINER -> {
                    throw new LabReadinessException("coming one day...");
                }
                default -> throw new LabReadinessException("Lab type not implemented...");
            }
        } catch (LabReadinessException e) {
            LOGGER.error(e.getMessage());
            return false;
        }
    }

    public Map<String, Object> getLabFile(Lab lab, RemoteServer remoteServer) {
        switch (lab.getLabType()) {
            case DOCKER_COMPOSE -> {
                return labReadinessService.getDockerComposeFile(lab, remoteServer);
            }
            case DOCKER_CONTAINER -> {
                throw new LabReadinessException("coming one day...");
            }
            default -> throw new LabReadinessException("Lab type not implemented...");
        }
    }

    public Map<String, Object> getLabFile(LabTracker labTracker, RemoteServer remoteServer) {
        switch (labTracker.getLabStarted().getLabType()) {
            case DOCKER_COMPOSE -> {
                return labReadinessService.getDockerComposeFile(labTracker, remoteServer);
            }
            case DOCKER_CONTAINER -> {
                throw new LabReadinessException("coming one day...");
            }
            default -> throw new LabReadinessException("Lab type not implemented...");
        }
    }

    public boolean deleteLabItem(String labId) {
        if (!labRepository.existsById(labId)) {
            return false;
        }

        labFileChangeLogRepository.deleteByLabId(labId);
        labRepository.deleteById(labId);
        return true;
    }

    public void clear(String teamId) {
        Team team = teamService.findTeamById(teamId);

        team.setTeamActiveLabs(new ArrayList<>());
        team.setTeamDeletedLabs(new ArrayList<>());

        teamService.updateTeam(team);
        labTrackerService.deleteAll();
    }
}
