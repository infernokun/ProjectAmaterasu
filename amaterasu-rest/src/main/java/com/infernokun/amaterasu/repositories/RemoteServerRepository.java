package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.RemoteServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RemoteServerRepository extends JpaRepository<RemoteServer, String> {
}
