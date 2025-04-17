package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.models.enums.ServerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RemoteServerRepository extends JpaRepository<RemoteServer, String> {
    List<RemoteServer> findByServerType(ServerType serverType);
}
