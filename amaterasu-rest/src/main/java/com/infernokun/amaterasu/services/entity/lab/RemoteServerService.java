package com.infernokun.amaterasu.services.entity.lab;

import com.infernokun.amaterasu.exceptions.ResourceInUseException;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.entities.lab.dto.RemoteServerRequest;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.repositories.lab.RemoteServerRepository;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.alt.DockerService;
import com.infernokun.amaterasu.services.alt.ProxmoxService;
import com.infernokun.amaterasu.utils.AESUtil;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RemoteServerService extends BaseService {
    private final ProxmoxService proxmoxService;
    private final DockerService dockerService;
    private final RemoteServerRepository remoteServerRepository;
    private final AESUtil aesUtil;
    private final LabTrackerRepository labTrackerRepository;

    private static final Set<LabStatus> NOT_DEPLOYED = Set.of(LabStatus.STOPPED, LabStatus.DELETED, LabStatus.NONE);
    // A host name, IPv4 or bracketed IPv6 address
    private static final Pattern ADDRESS = Pattern.compile("[A-Za-z0-9.-]+|\\[[0-9A-Fa-f:.]+]");

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

    /** What an admin edits on a saved server. Credentials are never sent back, only whether one is stored. */
    public record Settings(String id, String name, ServerType serverType, String ipAddress, Integer port,
                           String username, String nodeName, boolean hasPassword, boolean hasApiToken) {
    }

    public Settings settings(String id) {
        RemoteServer server = findServerById(id);
        return new Settings(server.getId(), server.getName(), server.getServerType(), server.getIpAddress(),
                server.getPort(), server.getUsername(), server.getNodeName(),
                server.getPassword() != null && !server.getPassword().isBlank(),
                server.getApiToken() != null && !server.getApiToken().isBlank());
    }

    /**
     * The saved server with the edits applied, not stored, for a connection test. Credentials left
     * blank are the stored ones.
     */
    public RemoteServer withChanges(String id, RemoteServerRequest changes) {
        RemoteServer saved = findServerById(id);
        return RemoteServer.builder()
                .name(changes.getName())
                .serverType(saved.getServerType())
                .ipAddress(trimmed(changes.getIpAddress()))
                .port(changes.getPort())
                .username(trimmed(changes.getUsername()))
                .nodeName(trimmed(changes.getNodeName()))
                .password(isBlank(changes.getPassword()) ? decrypt(saved.getPassword()) : changes.getPassword())
                .apiToken(isBlank(changes.getApiToken()) ? decrypt(saved.getApiToken()) : changes.getApiToken().trim())
                .build();
    }

    /**
     * Saves a server's name, address, port, username or node, and a new password or API token when one
     * is given. The type cannot change: labs on it were built for that type. Returns what changed.
     */
    public List<String> updateServer(String id, RemoteServerRequest changes) {
        RemoteServer server = findServerById(id);
        if (changes == null) {
            throw new IllegalArgumentException("Nothing to save");
        }
        if (changes.getServerType() != null && changes.getServerType() != server.getServerType()) {
            throw new IllegalArgumentException("A server's type cannot change. Add a new server instead.");
        }
        String name = trimmed(changes.getName());
        String address = trimmed(changes.getIpAddress());
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Server name required");
        }
        if (address.isEmpty() || !ADDRESS.matcher(address).matches()) {
            throw new IllegalArgumentException("Enter a host name or IP address, without a scheme or path");
        }
        if (changes.getPort() < 1 || changes.getPort() > 65535) {
            throw new IllegalArgumentException("Port must be from 1 to 65535");
        }
        boolean docker = server.getServerType() == ServerType.DOCKER_HOST;
        String username = trimmed(changes.getUsername());
        if (docker && username.isEmpty()) {
            throw new IllegalArgumentException("SSH username required");
        }
        String nodeName = trimmed(changes.getNodeName());
        if (!docker && nodeName.isEmpty()) {
            throw new IllegalArgumentException("Proxmox node name required");
        }
        if (!docker && !isBlank(changes.getApiToken()) && !changes.getApiToken().contains("=")) {
            throw new IllegalArgumentException("The API token looks like TOKEN_ID=SECRET");
        }
        boolean taken = remoteServerRepository.findAll().stream()
                .anyMatch(other -> !other.getId().equals(id) && name.equalsIgnoreCase(other.getName()));
        if (taken) {
            throw new IllegalArgumentException("A server named " + name + " already exists");
        }

        List<String> changed = new ArrayList<>();
        if (!name.equals(server.getName())) {
            changed.add("name " + server.getName() + " to " + name);
            server.setName(name);
        }
        if (!address.equals(server.getIpAddress())) {
            changed.add("address " + server.getIpAddress() + " to " + address);
            server.setIpAddress(address);
        }
        if (server.getPort() == null || changes.getPort() != server.getPort()) {
            changed.add("port " + server.getPort() + " to " + changes.getPort());
            server.setPort(changes.getPort());
        }
        if (docker && !username.equals(server.getUsername())) {
            changed.add("username");
            server.setUsername(username);
        }
        if (!docker && !nodeName.equals(server.getNodeName())) {
            changed.add("node " + server.getNodeName() + " to " + nodeName);
            server.setNodeName(nodeName);
        }
        if (docker && !isBlank(changes.getPassword())) {
            changed.add("password");
            server.setPassword(aesUtil.encrypt(Normalizer.normalize(changes.getPassword(), Normalizer.Form.NFKC)));
        }
        if (!docker && !isBlank(changes.getApiToken())) {
            changed.add("API token");
            server.setApiToken(aesUtil.encrypt(Normalizer.normalize(changes.getApiToken().trim(), Normalizer.Form.NFKC)));
        }
        if (!changed.isEmpty()) {
            server.setUpdatedAt(LocalDateTime.now());
            remoteServerRepository.save(server);
        }
        return changed;
    }

    private String decrypt(String stored) {
        return stored == null || stored.isBlank() ? null : aesUtil.decrypt(stored);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    public static boolean takesNoNewLabs(RemoteServer server) {
        return server != null && Boolean.TRUE.equals(server.getNoNewLabs());
    }

    /** Stops or allows new deployments on a server; labs already on it are left alone. */
    public RemoteServer setNoNewLabs(String id, boolean noNewLabs) {
        RemoteServer server = findServerById(id);
        server.setNoNewLabs(noNewLabs);
        server.setUpdatedAt(LocalDateTime.now());
        return remoteServerRepository.save(server);
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