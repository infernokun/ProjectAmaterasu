package com.infernokun.amaterasu.services.entity.lab;

import com.infernokun.amaterasu.exceptions.ResourceInUseException;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
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
import java.util.Set;

@Service
public class RemoteServerService extends BaseService {
    private final ProxmoxService proxmoxService;
    private final DockerService dockerService;
    private final RemoteServerRepository remoteServerRepository;
    private final AESUtil aesUtil;
    private final LabTrackerRepository labTrackerRepository;

    private static final Set<LabStatus> NOT_DEPLOYED = Set.of(LabStatus.STOPPED, LabStatus.DELETED, LabStatus.NONE);

    public RemoteServerService(ProxmoxService proxmoxService, DockerService dockerService,
                               RemoteServerRepository remoteServerRepository, AESUtil aesUtil,
                               LabTrackerRepository labTrackerRepository) {
        this.proxmoxService = proxmoxService;
        this.dockerService = dockerService;
        this.remoteServerRepository = remoteServerRepository;
        this.aesUtil = aesUtil;
        this.labTrackerRepository = labTrackerRepository;
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

    /**
     * Deletes a server that has no labs deployed on it. Stopped labs that last ran there forget the
     * server and start fresh wherever the team starts them next.
     */
    public RemoteServer deleteServer(String id) {
        RemoteServer server = findServerById(id);
        List<LabTracker> trackers = labTrackerRepository.findByRemoteServer(server);
        long deployed = trackers.stream()
                .filter(tracker -> tracker.getLabStatus() != null && !NOT_DEPLOYED.contains(tracker.getLabStatus()))
                .count();
        if (deployed > 0) {
            throw new ResourceInUseException(server.getName() + " still has " + deployed
                    + (deployed == 1 ? " lab" : " labs") + " deployed. Stop them or let teams finish first.");
        }
        trackers.forEach(tracker -> tracker.setRemoteServer(null));
        labTrackerRepository.saveAll(trackers);
        remoteServerRepository.delete(server);
        return server;
    }

    public void requireValidNewServer(RemoteServer server) {
        if (server.getServerType() == null || server.getServerType() == ServerType.UNKNOWN) {
            throw new IllegalArgumentException("Choose a Docker host or a Proxmox node");
        }
        if (server.getName() == null || server.getName().isBlank()) {
            throw new IllegalArgumentException("Server name required");
        }
        if (server.getIpAddress() == null || server.getIpAddress().isBlank()) {
            throw new IllegalArgumentException("Server address required");
        }
        boolean taken = remoteServerRepository.findAll().stream()
                .anyMatch(other -> server.getName().trim().equalsIgnoreCase(other.getName()));
        if (taken) {
            throw new IllegalArgumentException("A server named " + server.getName().trim() + " already exists");
        }
    }
}