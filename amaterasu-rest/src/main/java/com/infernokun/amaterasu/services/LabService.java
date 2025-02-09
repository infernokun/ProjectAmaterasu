package com.infernokun.amaterasu.services;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.FileUploadException;
import com.infernokun.amaterasu.exceptions.LabReadinessException;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.dto.LabDTO;
import com.infernokun.amaterasu.models.entities.*;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.repositories.LabFileChangeLogRepository;
import com.infernokun.amaterasu.repositories.LabRepository;
import com.infernokun.amaterasu.services.alt.LabFileUploadService;
import com.infernokun.amaterasu.services.alt.LabActionService;
import com.infernokun.amaterasu.services.alt.LabReadinessService;
import com.infernokun.amaterasu.services.base.BaseService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
                .orElseThrow(() -> new ResourceNotFoundException("Lab not found"));
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

    public Lab createLab(LabDTO labDTO) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        Lab newLab = null;

        switch (labDTO.getLabType()) {
            case DOCKER_COMPOSE -> {

                newLab = Lab.builder()
                        .name(labDTO.getName())
                        .labType(labDTO.getLabType())
                        .createdBy(labDTO.getCreatedBy())
                        .ready(false)
                        .dockerFile(labDTO.getName().toLowerCase().replace(" ", "-") + "_" + timestamp + ".yml")
                        .description(labDTO.getDescription())
                        .version(labDTO.getVersion())
                        .capacity(labDTO.getCapacity())
                        .build();
                Lab savedLab = labRepository.save(newLab);
                uploadLabFile(savedLab.getId(), labDTO.getDockerFile());

                LabFileChangeLog labFileChangeLog = new LabFileChangeLog(savedLab, false);
                labFileChangeLog.setUpdatedAt(LocalDateTime.now().minusYears(10));
                labFileChangeLogRepository.save(labFileChangeLog);
                return savedLab;
            }
            case KUBERNETES -> {
                LOGGER.info("Kube! Coming one day....");
                return newLab;
            }
            case VIRTUAL_MACHINE -> {
                LOGGER.info("VM! Coming one day....");
                return newLab;
            }
            case DOCKER_CONTAINER -> {
                LOGGER.info("Docker Container! Coming one day....");
                return newLab;
            }
            default -> {
                LOGGER.info("Coming one day....");
                return newLab;
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

    public boolean deleteLab(String id) {
        try {
            labRepository.deleteById(id);
            return true;
        } catch (OptimisticLockingFailureException e) {
            LOGGER.info("????");
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public Lab updateLab(Lab updatedLabData) {
        Optional<Lab> existingLabOptional = labRepository.findById(updatedLabData.getId());
        if (existingLabOptional.isPresent()) {
            return labRepository.save(updatedLabData);
        }
        return null;
    }

    public String uploadLabFile(String labId, String content) {
        Lab lab = findLabById(labId);

        switch (lab.getLabType()) {
            case DOCKER_COMPOSE -> {
                return labFileUploadService.uploadDockerComposeFile(lab, amaterasuConfig, content);
            }
            case DOCKER_CONTAINER -> {
                throw new FileUploadException("coming one day...");
            }
            default -> throw new FileUploadException("Lab type not implemented...");
        }
    }

    public Optional<LabActionResult> startLab(String labId, String userId, String labTrackerId) {
        LOGGER.info("lab id {} user id {}, lab tracker id {}", labId, userId, labTrackerId);

        Lab lab = this.findLabById(labId);
        User user = this.userService.findUserById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Team userTeam = user.getTeam();
        LOGGER.info("Starting lab...");

        LabTracker labTracker =
                labTrackerService.findLabTrackerById(labTrackerId).orElseGet(() ->
                        LabTracker.builder()
                                .labStarted(lab)
                                .labOwner(userTeam)
                                .build()
                );
        String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy @ hh:mma"));

        if (labTracker.getId() == null) {
            labTracker = labTrackerService.createLabTracker(labTracker);
        }

        LabActionResult labActionResult = labActionService.startLab(lab, labTracker, amaterasuConfig);

        labTracker.setUpdatedAt(LocalDateTime.now());
        labTracker.setUpdatedBy(user.getId());
        labTracker.setLabStatus(labActionResult.isSuccessful() ? LabStatus.ACTIVE : LabStatus.FAILED);

        if (!userTeam.getTeamActiveLabs().contains(labTracker.getId())) {
            userTeam.getTeamActiveLabs().add(labTracker.getId());
            teamService.updateTeam(userTeam);
        }
        labTrackerService.updateLabTracker(labTracker);
        return Optional.of(labActionResult);
    }

    public Optional<LabActionResult> stopLab(String labId, String userId, String labTrackerId) {
        LOGGER.info("Stopping lab with id {} by user id {} and lab tracker id {}", labId, userId, labTrackerId);

        Lab lab = this.findLabById(labId);
        User user = this.userService.findUserById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Team userTeam = user.getTeam();
        LOGGER.info("Stopping lab...");

        String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy @ hh:mma"));

        Optional<LabTracker> existingLabTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);
        if (existingLabTrackerOptional.isPresent()) {
            LabTracker existingLabTracker = existingLabTrackerOptional.get();
            LabActionResult labActionResult = labActionService.stopLab(lab, existingLabTracker, amaterasuConfig);

            existingLabTracker.setUpdatedAt(LocalDateTime.now());
            existingLabTracker.setUpdatedBy(user.getId());
            existingLabTracker.setLabStatus(labActionResult.isSuccessful() ? LabStatus.STOPPED : LabStatus.ACTIVE);

            labTrackerService.updateLabTracker(existingLabTracker);

            return Optional.of(labActionResult);
        }

        LOGGER.warn("No existing lab tracker found for lab tracker id {}", labTrackerId);
        return Optional.empty();
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

    public Optional<LabTracker> deleteLabFromTeam(String labId, String userId, String labTrackerId) {
        Lab stoppedLab = this.findLabById(labId);
        Optional<User> stoppedBy = this.userService.findUserById(userId);

        if (stoppedBy.isPresent()) {
            User user = stoppedBy.get();
            Team userTeam = user.getTeam();

            if (userTeam.getTeamActiveLabs().contains(labTrackerId)) {
                userTeam.getTeamDeletedLabs().add(labTrackerId);
                userTeam.getTeamActiveLabs().remove(labTrackerId);

                Optional<LabTracker> labTrackerOptional = labTrackerService
                        .findLabTrackerById(labTrackerId);

                if (labTrackerOptional.isPresent()) {
                    LabTracker labTracker = labTrackerOptional.get();
                    labTracker.setLabStatus(LabStatus.DELETED);

                    LabTracker updatedLabTracker = labTrackerService.updateLabTracker(labTracker);
                    teamService.updateTeam(userTeam);

                    return Optional.of(updatedLabTracker);
                }
            }
        }
        return Optional.empty();
    }

    public boolean checkDockerComposeValidity(String labId) {
        Lab lab = findLabById(labId);

        switch (lab.getLabType()) {
            case DOCKER_COMPOSE -> {
                return labReadinessService.checkDockerComposeReadiness(lab, amaterasuConfig);
            }
            case DOCKER_CONTAINER -> {
                throw new LabReadinessException("coming one day...");
            }
            default -> throw new LabReadinessException("Lab type not implemented...");
        }
    }

    public Map<String, Object> getLabFile(String labId) {
        Lab lab = findLabById(labId);

        switch (lab.getLabType()) {
            case DOCKER_COMPOSE -> {
                return labReadinessService.getDockerComposeFile(lab, amaterasuConfig);
            }
            case DOCKER_CONTAINER -> {
                throw new LabReadinessException("coming one day...");
            }
            default -> throw new LabReadinessException("Lab type not implemented...");
        }
    }

    public void clear(String teamId) {
        Optional<Team> team = teamService.findTeamById(teamId);

        if (team.isPresent()) {
            Team theTeam = team.get();

            theTeam.setTeamActiveLabs(new ArrayList<>());
            theTeam.setTeamDeletedLabs(new ArrayList<>());

            teamService.updateTeam(theTeam);

            labTrackerService.deleteAll();
        }
    }
}
