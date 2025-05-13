package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.stereotype.Service;

@Service
public class LabActionService extends BaseService {
    private final DockerService dockerService;
    private final ProxmoxService proxmoxService;

    public LabActionService(DockerService dockerService, ProxmoxService proxmoxService) {
        this.dockerService = dockerService;
        this.proxmoxService = proxmoxService;
    }

    public LabActionResult startLab(LabTracker labTracker, RemoteServer remoteServer) {
        switch (labTracker.getLabStarted().getLabType()) {
            case DOCKER_CONTAINER ->  {
                return new LabActionResult();
            }
            case DOCKER_COMPOSE -> {
                if (remoteServer.getServerType() != ServerType.DOCKER_HOST)
                    return new LabActionResult(false, labTracker, "Server NOT DockerHost");
                return dockerService.startDockerCompose(labTracker, remoteServer);
            }
            case VIRTUAL_MACHINE -> {
                if (remoteServer.getServerType() != ServerType.PROXMOX)
                    return new LabActionResult(false, labTracker, "Server NOT Proxmox");
                return labTracker.getVms().isEmpty() ? proxmoxService.startAndCloneProxmoxLab(labTracker, remoteServer) :
                        proxmoxService.startProxmoxLab(labTracker, remoteServer);
            }
            case KUBERNETES -> {
                String type = "kubernetes";
                return new LabActionResult();
            }
            case UNKNOWN -> {
                String type = "unknown";
                return new LabActionResult();
            }
            default -> {
                String type = "uhh";
                return new LabActionResult();
            }
        }
    }

    public LabActionResult stopLab(Lab lab, LabTracker labTracker, RemoteServer remoteServer) {
        switch (lab.getLabType()) {
            case DOCKER_CONTAINER ->  {
                return new LabActionResult();
            }
            case DOCKER_COMPOSE -> {
                return dockerService.stopDockerCompose(labTracker, remoteServer);
            }
            case VIRTUAL_MACHINE -> {
                return proxmoxService.stopProxmoxLab(labTracker, remoteServer);
            }
            case KUBERNETES -> {
                String type = "kubernetes";
                return new LabActionResult();

            }
            case UNKNOWN -> {
                String type = "unknown";
                return new LabActionResult();
            }
            default -> {
                String type = "uhh";
                return new LabActionResult();
            }
        }
    }

    public LabActionResult deleteLab(LabTracker labTracker, RemoteServer remoteServer) {
        switch (labTracker.getLabStarted().getLabType()) {
            case DOCKER_CONTAINER ->  {
                return new LabActionResult();
            }
            case DOCKER_COMPOSE -> {
                return dockerService.stopDockerCompose(labTracker, remoteServer);
            }
            case VIRTUAL_MACHINE -> {
                return proxmoxService.deleteProxmoxLab(labTracker, remoteServer);
            }
            case KUBERNETES -> {
                String type = "kubernetes";
                return new LabActionResult();
            }
            case UNKNOWN -> {
                String type = "unknown";
                return new LabActionResult();
            }
            default -> {
                String type = "uhh";
                return new LabActionResult();
            }
        }
    }
}
