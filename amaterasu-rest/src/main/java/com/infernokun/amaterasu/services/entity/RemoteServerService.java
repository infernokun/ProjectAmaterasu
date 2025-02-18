package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.repositories.RemoteServerRepository;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RemoteServerService extends BaseService {
    private final RemoteServerRepository remoteServerRepository;

    public RemoteServerService(RemoteServerRepository remoteServerRepository) {
        this.remoteServerRepository = remoteServerRepository;
    }

    public List<RemoteServer> getAllServers() {
        return remoteServerRepository.findAll();
    }

    public Optional<RemoteServer> getServerById(String id) {
        return remoteServerRepository.findById(id);
    }

    public RemoteServer addServer(RemoteServer remoteServer) {
        return remoteServerRepository.save(remoteServer);
    }

    public boolean deleteServer(String id) {
        if (remoteServerRepository.existsById(id)) {
            remoteServerRepository.deleteById(id);
            return true;
        }
        return false;
    }
}