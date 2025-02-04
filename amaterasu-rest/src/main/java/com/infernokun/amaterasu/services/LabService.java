package com.infernokun.amaterasu.services;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.FileUploadException;
import com.infernokun.amaterasu.exceptions.LabReadinessException;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.*;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.repositories.LabRepository;
import com.infernokun.amaterasu.services.alt.LabFileUploadService;
import com.infernokun.amaterasu.services.alt.LabHandlingService;
import com.infernokun.amaterasu.services.alt.LabReadinessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LabService {
    private final LabRepository labRepository;

    private final UserService userService;
    private final TeamService teamService;
    private final LabTrackerService labTrackerService;
    private final LabHandlingService labHandlingService;
    private final LabFileUploadService labFileUploadService;
    private final LabReadinessService labReadinessService;

    private final AmaterasuConfig amaterasuConfig;

    private final Logger LOGGER = LoggerFactory.getLogger(LabService.class);

    public LabService(
            LabRepository labRepository, UserService userService,
            TeamService teamService, LabTrackerService labTrackerService,
            LabHandlingService labHandlingService, LabFileUploadService labFileUploadService,
            LabReadinessService labReadinessService,
            AmaterasuConfig amaterasuConfig) {
        this.labRepository = labRepository;
        this.userService = userService;
        this.teamService = teamService;
        this.labTrackerService = labTrackerService;
        this.labHandlingService = labHandlingService;
        this.labFileUploadService = labFileUploadService;
        this.labReadinessService = labReadinessService;
        this.amaterasuConfig = amaterasuConfig;
    }

    public List<Lab> findAllLabs() {
        return labRepository.findAll();
    }

    public Optional<Lab> findLabById(String id) {
        return labRepository.findById(id);
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
        Optional<Lab> labOptional = findLabById(labId);

        if (labOptional.isEmpty()) {
            throw new FileUploadException("Lab not found");
        }
        Lab lab = labOptional.get();

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

        Lab lab = this.findLabById(labId).orElseThrow(() -> new IllegalArgumentException("Lab not found"));
        User user = this.userService.findUserById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Team userTeam = user.getTeam();
        LOGGER.info("Starting lab...");

        Optional<LabTracker> labTrackerOptional = Optional.ofNullable(
                labTrackerService.findLabTrackerById(labTrackerId).orElseGet(() ->
                        LabTracker.builder()
                                .labStarted(lab)
                                .labOwner(userTeam)
                                .build()
                )
        );
        String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy @ hh:mma"));

        if (labTrackerOptional.isPresent()) {
            LabTracker labTracker = labTrackerOptional.get();
            LabActionResult labActionResult = labHandlingService.startLab(lab, labTracker, amaterasuConfig);

            labTracker.setUpdatedAt(LocalDateTime.now());
            labTracker.setUpdatedBy(user.getId());

            labTracker.setLabStatus(labActionResult.isSuccessful() ? LabStatus.ACTIVE : LabStatus.FAILED);
            if (!userTeam.getTeamActiveLabs().contains(labTracker.getId())) {
                userTeam.getTeamDeletedLabs().add(labTracker.getId());
                teamService.updateTeam(userTeam);
            }
            labTrackerService.updateLabTracker(labTracker);
            return Optional.of(labActionResult);
        }
        return Optional.empty();
    }

    public Optional<LabTracker> stopLab(String labId, String userId, String labTrackerId) {
        LOGGER.info("Stopping lab with id {} by user id {} and lab tracker id {}", labId, userId, labTrackerId);

        Lab lab = this.findLabById(labId).orElseThrow(() -> new IllegalArgumentException("Lab not found"));
        User user = this.userService.findUserById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Team userTeam = user.getTeam();

        Optional<LabTracker> existingLabTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);
        LocalDateTime now = LocalDateTime.now();
        String formattedDate = now.format(DateTimeFormatter.ofPattern("MMM dd, yyyy @ hh:mma"));

        boolean successfulStop = labHandlingService.stopLab(lab, amaterasuConfig);

        if (existingLabTrackerOptional.isPresent()) {
            LabTracker existingLabTracker = existingLabTrackerOptional.get();
            return handleLabStop(lab, user, existingLabTracker, successfulStop, now, formattedDate);
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
        Optional<Lab> stoppedLab = this.findLabById(labId);
        Optional<User> stoppedBy = this.userService.findUserById(userId);

        if (stoppedLab.isPresent() && stoppedBy.isPresent()) {
            Lab lab = stoppedLab.get();
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
        Optional<Lab> labOptional = findLabById(labId);

        if (labOptional.isEmpty()) {
            throw new LabReadinessException("Lab not found");
        }
        Lab lab = labOptional.get();

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

    public Map<String, Object> getLabSettings(String labId) {
        Optional<Lab> labOptional = findLabById(labId);

        if (labOptional.isEmpty()) {
            throw new LabReadinessException("Lab not found");
        }
        Lab lab = labOptional.get();

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
