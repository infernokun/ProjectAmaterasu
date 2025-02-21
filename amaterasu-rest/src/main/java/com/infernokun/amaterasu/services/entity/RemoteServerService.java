package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.repositories.RemoteServerRepository;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.utils.AESUtil;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;

@Service
public class RemoteServerService extends BaseService {
    private final RemoteServerRepository remoteServerRepository;
    private final AESUtil aesUtil;

    public RemoteServerService(RemoteServerRepository remoteServerRepository, AESUtil aesUtil) {
        this.remoteServerRepository = remoteServerRepository;
        this.aesUtil = aesUtil;
    }

    public List<RemoteServer> getAllServers() {
        return remoteServerRepository.findAll();
    }

    public RemoteServer getServerById(String id) {
        return remoteServerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Did not find remote server"));
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

    public RemoteServer modifyStatus(RemoteServer remoteServer) {
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