package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.repositories.LabTrackerRepository;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class LabTrackerService extends BaseService {
    private final TeamService teamService;
    private final LabTrackerRepository labTrackerRepository;

    public LabTrackerService(TeamService teamService, LabTrackerRepository labTrackerRepository) {
        this.teamService = teamService;
        this.labTrackerRepository = labTrackerRepository;
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

    public void deleteAll() {
        this.labTrackerRepository.deleteAll();
    }
}
