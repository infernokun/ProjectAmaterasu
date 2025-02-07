package com.infernokun.amaterasu.services;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.RemoteServerStats;
import com.infernokun.amaterasu.repositories.RemoteServerStatsRepository;
import com.infernokun.amaterasu.services.base.BaseService;
import org.hibernate.dialect.lock.OptimisticEntityLockException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RemoteServerStatsService extends BaseService {
    private final RemoteServerStatsRepository remoteServerStatsRepository;

    public RemoteServerStatsService(RemoteServerStatsRepository remoteServerStatsRepository) {
        this.remoteServerStatsRepository = remoteServerStatsRepository;
    }

    /**
     * Fetches all RemoteServerStats records.
     *
     * @return List of RemoteServerStats.
     */
    public List<RemoteServerStats> findAllStats() {
        return remoteServerStatsRepository.findAll();
    }

    /**
     * Fetches a RemoteServerStats record by its ID.
     *
     * @param id The id of the record.
     * @return An Optional of RemoteServerStats.
     */
    public Optional<RemoteServerStats> findStatsById(String id) {
        return remoteServerStatsRepository.findById(id);
    }

    /**
     * Creates a new RemoteServerStats record.
     *
     * @param stats The RemoteServerStats entity to create.
     * @return The created RemoteServerStats.
     */
    public RemoteServerStats createStats(RemoteServerStats stats) {
        stats.setCreatedAt(LocalDateTime.now());
        LOGGER.info("Creating new RemoteServerStats at {}", stats.getCreatedAt());
        return remoteServerStatsRepository.save(stats);
    }

    /**
     * Updates an existing RemoteServerStats record.
     *
     * @param updatedStats The updated RemoteServerStats entity.
     * @return The updated RemoteServerStats.
     */
    public RemoteServerStats updateStats(RemoteServerStats updatedStats) {
        Optional<RemoteServerStats> existingStatsOptional = remoteServerStatsRepository.findById(
                updatedStats.getId());
        if (existingStatsOptional.isPresent()) {
            // Optional: update a modification timestamp
            updatedStats.setUpdatedAt(LocalDateTime.now());
            LOGGER.info("Updating RemoteServerStats with id {} at {}",
                    updatedStats.getId(), updatedStats.getUpdatedAt());
            return remoteServerStatsRepository.save(updatedStats);
        }
        throw new ResourceNotFoundException("RemoteServerStats not found with id " +
                updatedStats.getId());
    }

    /**
     * Deletes a RemoteServerStats record by its ID.
     *
     * @param id The id of the record.
     * @return boolean indicating if the deletion was successful.
     */
    public boolean deleteStats(String id) {
        try {
            remoteServerStatsRepository.deleteById(id);
            LOGGER.info("Deleted RemoteServerStats with id {}", id);
            return true;
        } catch (OptimisticEntityLockException ex) {
            LOGGER.error("Optimistic locking failure when deleting RemoteServerStats with id {} : {}",
                    id, ex.getMessage());
            return false;
        } catch (Exception ex) {
            LOGGER.error("Error deleting RemoteServerStats with id {} : {}", id, ex.getMessage());
            return false;
        }
    }
}
