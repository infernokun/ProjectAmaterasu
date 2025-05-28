package com.infernokun.amaterasu.services.entity.lab;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.repositories.lab.RemoteServerRepository;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.alt.DockerService;
import com.infernokun.amaterasu.services.alt.ProxmoxService;
import com.infernokun.amaterasu.utils.AESUtil;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;

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

    public List<RemoteServer> findAllServers() {
        return remoteServerRepository.findAll();
    }

    public RemoteServer findServerById(String id) {
        return remoteServerRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Did not find remote server"));
    }

    public List<RemoteServer> findByServerType(ServerType serverType) {
        return remoteServerRepository.findByServerType(serverType);
    }

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

    public RemoteServer modifyStatus(RemoteServer remoteServer) {
        remoteServer.setUpdatedAt(LocalDateTime.now());
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