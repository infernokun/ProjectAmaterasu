package com.infernokun.amaterasu.services;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.repositories.LabRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class LabService {
    private final LabRepository labRepository;

    private final UserService userService;
    private final TeamService teamService;
    private final LabTrackerService labTrackerService;
    private final DockerImageService dockerImageService;
    private final DockerComposeService dockerComposeService;

    private final AmaterasuConfig amaterasuConfig;

    private final Logger LOGGER = LoggerFactory.getLogger(LabService.class);


    public LabService(LabRepository labRepository, UserService userService, TeamService teamService, LabTrackerService labTrackerService, DockerImageService dockerImageService, DockerComposeService dockerComposeService, AmaterasuConfig amaterasuConfig) {
        this.labRepository = labRepository;
        this.userService = userService;
        this.teamService = teamService;
        this.labTrackerService = labTrackerService;
        this.dockerImageService = dockerImageService;
        this.dockerComposeService = dockerComposeService;
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
        return labRepository.findById(updatedLabData.getId()).map(existingLab -> {
            if (updatedLabData.getName() != null) existingLab.setName(updatedLabData.getName());
            if (updatedLabData.getDescription() != null) existingLab.setDescription(updatedLabData.getDescription());
            if (updatedLabData.getStatus() != null) existingLab.setStatus(updatedLabData.getStatus());
            if (updatedLabData.getCreatedBy() != null) existingLab.setCreatedBy(updatedLabData.getCreatedBy());
            if (updatedLabData.getVersion() != null) existingLab.setVersion(updatedLabData.getVersion());
            if (updatedLabData.getCapacity() != null) existingLab.setCapacity(updatedLabData.getCapacity());
            if (updatedLabData.getDockerFile() != null) existingLab.setDockerFile(updatedLabData.getDockerFile());

            existingLab.setUpdatedAt(LocalDateTime.now());
            return labRepository.save(existingLab);
        }).orElseThrow(() -> new IllegalArgumentException("Lab with ID " + updatedLabData.getId() + " not found."));
    }

    public Optional<LabTracker> startLab(String labId, String userId) {
        Optional<Lab> startedLab = this.findLabById(labId);
        Optional<User> startedBy = this.userService.findUserById(userId);

        if (startedLab.isPresent() && startedBy.isPresent()) {
            Lab lab = startedLab.get();
            User user = startedBy.get();
            Team userTeam = user.getTeam();
            LOGGER.info("Starting lab...");

            dockerImageService.startDockerContainer("some-docker-image");
            dockerComposeService.startDockerComposeEnvironment("some-docker-compose");

            Optional<String> existingLabTrackerIdOptional = userTeam.getTeamActiveLabs().stream()
                    .filter(labTracker -> labTracker.equals(labId))
                    .findFirst();

            if (existingLabTrackerIdOptional.isPresent()) {
                Optional <LabTracker> existingLabTrackerOptional = labTrackerService.findLabTrackerByLabStartedAndLabOwner(lab, userTeam);

                LOGGER.info("Labtracker ID present");
                if (existingLabTrackerOptional.isPresent()) {
                    LabTracker existingLabTracker = existingLabTrackerOptional.get();
                    LOGGER.info("Existing Labtracker Present");

                    existingLabTracker.setLabStatus(LabStatus.ACTIVE);
                    existingLabTracker.setUpdatedAt(LocalDateTime.now());
                    existingLabTracker.setUpdatedBy(user.getId());

                    labTrackerService.updateLabTracker(existingLabTracker);

                    String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy @ hh:mma"));
                    LOGGER.info("{} started again by {} at {}, status is {}", lab.getName(), user.getUsername(), formattedDate, LabStatus.ACTIVE);
                    return Optional.of(existingLabTracker);
                }
            }

            LOGGER.info("Creating new labtracker..");
            LabTracker labTracker = LabTracker.builder()
                    .labStarted(lab)
                    .labStatus(LabStatus.ACTIVE)
                    .labOwner(userTeam)
                    .build();

            labTracker.setCreatedBy(user.getId());

            LabTracker newLabtracker = labTrackerService.createLabTracker(labTracker);

            userTeam.getTeamActiveLabs().add(newLabtracker.getLabStarted().getId());
            teamService.updateTeam(userTeam);

            String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy @ hh:mma"));
            LOGGER.info("{} started by {} at {} in team {}, status is {}", lab.getName(), user.getUsername(), formattedDate, userTeam.getName(), LabStatus.ACTIVE);
            return Optional.of(labTracker);
        }
        return Optional.empty();
    }

    public Optional<LabTracker> stopLab(String labId, String userId) {
        Optional<Lab> stoppedLab = this.findLabById(labId);
        Optional<User> stoppedBy = this.userService.findUserById(userId);

        if (stoppedLab.isPresent() && stoppedBy.isPresent()) {
            Lab lab = stoppedLab.get();
            User user = stoppedBy.get();
            Team userTeam = user.getTeam();

            Optional<String> existingLabTrackerIdOptional = userTeam.getTeamActiveLabs().stream()
                    .filter(labTrackerId -> labTrackerId.equals(labId))
                    .findFirst();

            dockerImageService.stopDockerContainer("some-docker-image");
            dockerComposeService.stopDockerComposeEnvironment("some-docker-compose");
            LOGGER.info("Stopping lab...");

            if (existingLabTrackerIdOptional.isPresent()) {
                Optional<LabTracker> existingLabTrackerOptional = labTrackerService.findLabTrackerByLabStartedAndLabOwner(lab, userTeam);

                LOGGER.info("Labtracker id found...");

                if (existingLabTrackerOptional.isPresent()) {
                    LOGGER.info("Labtracker found...");

                    LabTracker existingLabTracker = existingLabTrackerOptional.get();

                    existingLabTracker.setLabStatus(LabStatus.STOPPED);
                    existingLabTracker.setUpdatedAt(LocalDateTime.now());
                    this.labTrackerService.updateLabTracker(existingLabTracker);

                    String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy @ hh:mma"));
                    LOGGER.info("{} stopped by {} at {}, status is {}", lab.getName(), user.getUsername(), formattedDate, existingLabTracker.getLabStatus());
                    return Optional.of(existingLabTracker);
                }
            }
        }
        return Optional.empty();
    }
}
