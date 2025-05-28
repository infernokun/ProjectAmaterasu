package com.infernokun.amaterasu.repositories.lab;

import com.infernokun.amaterasu.models.entities.lab.RemoteServerStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RemoteServerStatsRepository extends JpaRepository<RemoteServerStats, String> {

    public Optional<RemoteServerStats> findByRemoteServerId(String remoteServerId);
}
