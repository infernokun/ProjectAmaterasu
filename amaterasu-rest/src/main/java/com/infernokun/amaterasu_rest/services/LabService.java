package com.infernokun.amaterasu_rest.services;

import com.infernokun.amaterasu_rest.models.entities.Lab;
import com.infernokun.amaterasu_rest.repositories.LabRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class LabService {

    private final LabRepository labRepository;

    public LabService(LabRepository labRepository) {
        this.labRepository = labRepository;
    }

    public List<Lab> findAllLabs() {
        return labRepository.findAll();
    }

    public Optional<Lab> findLabById(String id) {
        return labRepository.findById(id);
    }

    public Lab saveLab(Lab lab) {
        return labRepository.save(lab);
    }

    public void deleteLab(String id) {
        labRepository.deleteById(id);
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
}
