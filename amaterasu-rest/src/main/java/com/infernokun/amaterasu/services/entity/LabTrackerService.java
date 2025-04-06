package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.repositories.LabTrackerRepository;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class LabTrackerService extends BaseService {
    private final LabTrackerRepository labTrackerRepository;

    public LabTrackerService(LabTrackerRepository labTrackerRepository) {
        this.labTrackerRepository = labTrackerRepository;
    }

    @Cacheable(value = "allLabTrackers")
    public List<LabTracker> findAllLabTrackers() {
        return labTrackerRepository.findAll();
    }

    @Cacheable(value = "labTrackers", key = "#id")
    public LabTracker findLabTrackerById(String id) {
        return this.labTrackerRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Lab Tracker not found!")
        );
    }

    @Cacheable(value = "activeLabTrackers", key = "#labStarted.id + '_' + #labOwner.id")
    public Optional<LabTracker> findLabTrackerByLabStartedAndLabOwnerAndStatusNotDeleted(Lab labStarted, Team labOwner) {
        return this.labTrackerRepository.findLabTrackerByLabStartedAndLabOwnerAndLabStatusNot(labStarted, labOwner, LabStatus.DELETED);
    }

    @CacheEvict(value = {"labTrackers", "activeLabTrackers", "allLabTrackers"}, allEntries = true)
    public LabTracker createLabTracker(LabTracker labTracker) {
        return labTrackerRepository.save(labTracker);
    }

    @CacheEvict(value = {"labTrackers", "activeLabTrackers", "allLabTrackers"}, allEntries = true)
    public List<LabTracker> createManyLabTrackers(List<LabTracker> labTrackers) {
        return labTrackerRepository.saveAll(labTrackers);
    }

    @CacheEvict(value = {"labTrackers", "activeLabTrackers", "allLabTrackers"}, key = "#id")
    public LabTracker deleteLabTracker(String id) {
        LabTracker deletedLabTracker = findLabTrackerById(id);
        labTrackerRepository.deleteById(id);
        return deletedLabTracker;
    }

    @CacheEvict(value = {"labTrackers", "activeLabTrackers", "allLabTrackers"}, key = "#labTracker.id")
    public LabTracker updateLabTracker(LabTracker labTracker) {
        findLabTrackerById(labTracker.getId());
        labTracker.setUpdatedAt(LocalDateTime.now());
        return labTrackerRepository.save(labTracker);
    }

    @CacheEvict(value = {"labTrackers", "activeLabTrackers", "allLabTrackers"}, allEntries = true)
    public void deleteAll() {
        this.labTrackerRepository.deleteAll();
    }
}
