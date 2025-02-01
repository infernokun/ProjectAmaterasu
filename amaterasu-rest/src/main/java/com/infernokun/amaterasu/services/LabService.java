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
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class LabService {
    private final LabRepository labRepository;

    private final UserService userService;
    private final TeamService teamService;
    private final LabTrackerService labTrackerService;
    private final DockerService dockerService;

    private final AmaterasuConfig amaterasuConfig;

    private final Logger LOGGER = LoggerFactory.getLogger(LabService.class);


    public LabService(
            LabRepository labRepository,
            UserService userService,
            TeamService teamService,
            LabTrackerService labTrackerService,
            DockerService dockerService,
            AmaterasuConfig amaterasuConfig) {
        this.labRepository = labRepository;
        this.userService = userService;
        this.teamService = teamService;
        this.labTrackerService = labTrackerService;
        this.dockerService = dockerService;
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

    public Optional<LabTracker> startLab(String labId, String userId, String labTrackerId) throws URISyntaxException {
        LOGGER.info("lab id {} user id {}, lab tracker id {}", labId, userId, labTrackerId);

        Optional<Lab> startedLab = this.findLabById(labId);
        Optional<User> startedBy = this.userService.findUserById(userId);

        if (startedLab.isPresent() && startedBy.isPresent()) {
            Lab lab = startedLab.get();
            User user = startedBy.get();
            Team userTeam = user.getTeam();
            LOGGER.info("Starting lab...");

            //dockerService.startDockerContainer("hello-world", amaterasuConfig.getDockerHost());

            Optional<LabTracker> existingLabTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);

            if (existingLabTrackerOptional.isPresent()) {
                LabTracker existingLabTracker = existingLabTrackerOptional.get();

                existingLabTracker.setLabStatus(LabStatus.ACTIVE);
                existingLabTracker.setUpdatedAt(LocalDateTime.now());
                existingLabTracker.setUpdatedBy(user.getId());

                labTrackerService.updateLabTracker(existingLabTracker);

                String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy @ hh:mma"));
                LOGGER.info("{} started again by {} at {}, status is {}", lab.getName(), user.getUsername(), formattedDate, LabStatus.ACTIVE);
                return Optional.of(existingLabTracker);

            }

            LOGGER.info("Creating new labtracker..");
            LabTracker labTracker = LabTracker.builder()
                    .labStarted(lab)
                    .labStatus(LabStatus.ACTIVE)
                    .labOwner(userTeam)
                    .build();

            labTracker.setCreatedBy(user.getId());

            LabTracker newLabtracker = labTrackerService.createLabTracker(labTracker);

            userTeam.getTeamActiveLabs().add(newLabtracker.getId());
            teamService.updateTeam(userTeam);

            String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy @ hh:mma"));
            LOGGER.info("{} started by {} at {} in team {}, status is {}", lab.getName(), user.getUsername(), formattedDate, userTeam.getName(), LabStatus.ACTIVE);
            return Optional.of(labTracker);
        }
        return Optional.empty();
    }

    public Optional<LabTracker> stopLab(String labId, String userId, String labTrackerId) {
        Optional<Lab> stoppedLab = this.findLabById(labId);
        Optional<User> stoppedBy = this.userService.findUserById(userId);

        if (stoppedLab.isPresent() && stoppedBy.isPresent()) {
            Lab lab = stoppedLab.get();
            User user = stoppedBy.get();
            Team userTeam = user.getTeam();

            Optional<LabTracker> existingLabTrackerOptional = labTrackerService.findLabTrackerById(labTrackerId);

            //dockerService.startDockerContainer("hello-world");

            if (existingLabTrackerOptional.isPresent()) {
                LabTracker existingLabTracker = existingLabTrackerOptional.get();

                existingLabTracker.setLabStatus(LabStatus.STOPPED);
                existingLabTracker.setUpdatedAt(LocalDateTime.now());
                existingLabTracker.setUpdatedBy(user.getId());
                this.labTrackerService.updateLabTracker(existingLabTracker);

                String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy @ hh:mma"));
                LOGGER.info("{} stopped by {} at {}, status is {}", lab.getName(), user.getUsername(), formattedDate, existingLabTracker.getLabStatus());
                return Optional.of(existingLabTracker);
            }
        }
        return Optional.empty();
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
