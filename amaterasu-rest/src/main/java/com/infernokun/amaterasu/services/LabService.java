package com.infernokun.amaterasu.services;

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
import java.util.Optional;

@Service
public class LabService {

    private final LabRepository labRepository;
    private final UserService userService;
    private final LabTrackerService labTrackerService;
    private final Logger LOGGER = LoggerFactory.getLogger(LabService.class);


    public LabService(LabRepository labRepository, UserService userService, LabTrackerService labTrackerService) {
        this.labRepository = labRepository;
        this.userService = userService;
        this.labTrackerService = labTrackerService;
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

    public boolean deleteLab(String id) {
        try {
            labRepository.deleteById(id);
            return true; // Return true if deletion is successful
        } catch (OptimisticLockingFailureException e) {
            return false; // Return false if there was a conflict
        } catch (Exception e) {
            return false; // Return false for any other exceptions
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

            existingLab.setLastModifiedDate(LocalDateTime.now());
            return labRepository.save(existingLab);
        }).orElseThrow(() -> new IllegalArgumentException("Lab with ID " + updatedLabData.getId() + " not found."));
    }

    public List<Lab> createManyLabs(List<Lab> labs) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        for (Lab lab : labs) {
            lab.setDockerFile(lab.getName().toLowerCase().replace(" ", "-") + "_" + timestamp + ".yml");
        }
        return labRepository.saveAll(labs);
    }

    public Optional<LabTracker> startLab(String labId, String userId) {
        Optional<Lab> startedLab = this.findLabById(labId);
        Optional<User> startedBy = this.userService.findUserById(userId);

        if (startedLab.isPresent() && startedBy.isPresent()) {
            Lab lab = startedLab.get();
            User user = startedBy.get();
            Team userTeam = user.getTeam();

            try {
                Thread.sleep(5000);
                if (Math.random() < 0.5) {
                    throw new InterruptedException("Randomly generated exception");
                }
            } catch (InterruptedException e) {
                return Optional.empty();
            }

            LabTracker labTracker = LabTracker.builder()
                    .labStarted(lab)
                    .labStatus(LabStatus.ACTIVE)
                    .labOwner(userTeam)
                    .build();

            labTracker.setCreatedBy(user.getId());

            this.labTrackerService.createLabTracker(labTracker);

            String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy @ hh:mma"));
            LOGGER.info(String.format("%s started by %s at %s, status is %s",
                    lab.getName(),
                    user.getUsername(),
                    formattedDate,
                    LabStatus.ACTIVE));
            return Optional.of(labTracker);
        }
        return Optional.empty();
    }
}
