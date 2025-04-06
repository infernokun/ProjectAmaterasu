package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.models.entities.RemoteServerStats;
import com.infernokun.amaterasu.repositories.RemoteServerStatsRepository;
import com.infernokun.amaterasu.services.BaseService;
import org.hibernate.dialect.lock.OptimisticEntityLockException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RemoteServerStatsService extends BaseService {
    private final RemoteServerStatsRepository remoteServerStatsRepository;
    private final RemoteServerService remoteServerService;

    public RemoteServerStatsService(RemoteServerStatsRepository remoteServerStatsRepository, RemoteServerService remoteServerService) {
        this.remoteServerStatsRepository = remoteServerStatsRepository;
        this.remoteServerService = remoteServerService;
    }

    @Cacheable(value = "serverStats", key = "'all'")
    public List<RemoteServerStats> findAllStats() {
        return remoteServerStatsRepository.findAll();
    }

    @Cacheable(value = "serverStats", key = "#id")
    public RemoteServerStats findStatsById(String id) {
        return remoteServerStatsRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Remote server stats not found."));
    }

    @CacheEvict(value = "serverStats", allEntries = true)
    public RemoteServerStats createStats(RemoteServerStats stats) {
        stats.setCreatedAt(LocalDateTime.now());
        LOGGER.info("Creating new RemoteServerStats at {}", stats.getCreatedAt());
        return remoteServerStatsRepository.save(stats);
    }

    @CacheEvict(value = "serverStats", key = "#updatedStats.id")
    public RemoteServerStats updateStats(RemoteServerStats updatedStats) {
        findStatsById(updatedStats.getId());
        updatedStats.setUpdatedAt(LocalDateTime.now());
        RemoteServerStats savedRemoteServerStats = remoteServerStatsRepository.save(updatedStats);

        RemoteServer remoteServer = updatedStats.getRemoteServer();
        remoteServer.setRemoteServerStats(savedRemoteServerStats);

        remoteServerService.modifyStatus(remoteServer);
        return savedRemoteServerStats;
    }

    @CacheEvict(value = "serverStats", key = "#id")
    public RemoteServerStats deleteStats(String id) {
        RemoteServerStats remoteServerStats = findStatsById(id);
        try {
            remoteServerStatsRepository.deleteById(id);
            return remoteServerStats;
        } catch (OptimisticEntityLockException ex) {
            String error = String.format("Optimistic locking failure when deleting RemoteServerStats with id %s : %s",
                    id, ex.getMessage());
            LOGGER.error(error);
            throw new RuntimeException(error);
        } catch (Exception ex) {
            String error = String.format("Error deleting RemoteServerStats with id %s : %s", id, ex.getMessage());
            LOGGER.error(error);
            throw new RuntimeException(error);
        }
    }
}
