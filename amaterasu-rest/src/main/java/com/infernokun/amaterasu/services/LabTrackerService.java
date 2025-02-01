package com.infernokun.amaterasu.services;

import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker; // Assuming you have a LabTracker entity
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.repositories.LabTrackerRepository; // Assuming you have a LabTrackerRepository
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LabTrackerService {

    private final LabTrackerRepository labTrackerRepository;

    @Autowired
    public LabTrackerService(LabTrackerRepository labTrackerRepository) {
        this.labTrackerRepository = labTrackerRepository;
    }

    // Retrieve all lab trackers
    public List<LabTracker> findAllLabTrackers() {
        return labTrackerRepository.findAll();
    }

    public Optional<LabTracker> findLabTrackerById(String id) {
        return this.labTrackerRepository.findById(id);
    }

    public Optional<LabTracker> findLabTrackerByLabStartedAndLabOwnerAndStatusNotDeleted(Lab labStarted, Team labOwner) {
        return this.labTrackerRepository.findLabTrackerByLabStartedAndLabOwnerAndLabStatusNot(labStarted, labOwner, LabStatus.DELETED);
    }

    // Create a new lab tracker
    public LabTracker createLabTracker(LabTracker labTracker) {
        return labTrackerRepository.save(labTracker);
    }

    // Create multiple lab trackers
    public List<LabTracker> createManyLabTrackers(List<LabTracker> labTrackers) {
        return labTrackerRepository.saveAll(labTrackers);
    }

    // Delete a lab tracker by ID
    public boolean deleteLabTracker(String id) {
        try {
            labTrackerRepository.deleteById(id);
            return true; // Deletion successful
        } catch (Exception e) {
            return false; // Deletion failed (e.g., lab tracker not found)
        }
    }

    // Update an existing lab tracker
    public LabTracker updateLabTracker(LabTracker labTracker) {
        Optional<LabTracker> existingLabTrackerOpt = labTrackerRepository.findById(labTracker.getId());
        if (existingLabTrackerOpt.isPresent()) {
            return labTrackerRepository.save(labTracker); // Save the updated lab tracker
        }
        return null; // Lab tracker not found
    }
}
