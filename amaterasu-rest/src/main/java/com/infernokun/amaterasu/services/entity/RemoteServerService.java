package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.repositories.RemoteServerRepository;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.alt.DockerService;
import com.infernokun.amaterasu.services.alt.ProxmoxService;
import com.infernokun.amaterasu.utils.AESUtil;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RemoteServerService extends BaseService {
    private final ProxmoxService proxmoxService;
    private final DockerService dockerService;
    private final RemoteServerRepository remoteServerRepository;
    private final AESUtil aesUtil;

    public RemoteServerService(ProxmoxService proxmoxService, DockerService dockerService, RemoteServerRepository remoteServerRepository, AESUtil aesUtil) {
        this.proxmoxService = proxmoxService;
        this.dockerService = dockerService;
        this.remoteServerRepository = remoteServerRepository;
        this.aesUtil = aesUtil;
    }

    @Cacheable(value = "servers", key = "'all'")
    public List<RemoteServer> getAllServers() {
        return remoteServerRepository.findAll();
    }

    @Cacheable(value = "servers", key = "#id")
    public RemoteServer getServerById(String id) {
        return remoteServerRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Did not find remote server"));
    }

    @CacheEvict(value = "servers", allEntries = true)
    public RemoteServer addServer(RemoteServer remoteServer) {
        String password = remoteServer.getPassword();
        String apiToken = remoteServer.getApiToken();

        switch (remoteServer.getServerType()) {
            case DOCKER_HOST:
                // Normalize the password before encryption
                if (password != null) {
                    password = Normalizer.normalize(password, Normalizer.Form.NFKC);
                    remoteServer.setPassword(aesUtil.encrypt(password));
                }
                break;
            case PROXMOX:
                // Normalize the API token before encryption
                if (apiToken != null) {
                    apiToken = Normalizer.normalize(apiToken, Normalizer.Form.NFKC);
                    remoteServer.setApiToken(aesUtil.encrypt(apiToken));
                }
                break;
            default:
                throw new RuntimeException("Not available");
        }
        return remoteServerRepository.save(remoteServer);
    }

    public boolean validateRemoteServer(RemoteServer remoteServer) {
        switch (remoteServer.getServerType()) {
            case PROXMOX -> {
                return proxmoxService.proxmoxHealthCheck(remoteServer);
            }
            case DOCKER_HOST -> {
                return dockerService.dockerHealthCheck(remoteServer);
            }
        }
        return false;
    }

    @CacheEvict(value = "servers", allEntries = true)
    public RemoteServer modifyStatus(RemoteServer remoteServer) {
        remoteServer.setUpdatedAt(LocalDateTime.now());
        return remoteServerRepository.save(remoteServer);
    }

    @CacheEvict(value = {"servers", "serverValidation"}, allEntries = true)
    public boolean deleteServer(String id) {
        if (remoteServerRepository.existsById(id)) {
            remoteServerRepository.deleteById(id);
            return true;
        }
        return false;
    }
}