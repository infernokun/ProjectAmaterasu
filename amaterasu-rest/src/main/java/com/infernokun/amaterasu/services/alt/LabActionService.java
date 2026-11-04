package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.stereotype.Service;

/**
 * Routes lab actions to the Docker or Proxmox implementation.
 * <ul>
 *   <li>start: deploys the lab, or resumes it if it was paused</li>
 *   <li>pause: brings the lab down but keeps containers and VM disks</li>
 *   <li>teardown: removes everything, so the next start is a fresh copy</li>
 * </ul>
 */
@Service
public class LabActionService extends BaseService {
    private final DockerService dockerService;
    private final ProxmoxService proxmoxService;

    public LabActionService(DockerService dockerService, ProxmoxService proxmoxService) {
        this.dockerService = dockerService;
        this.proxmoxService = proxmoxService;
    }

    public LabActionResult startLab(LabTracker labTracker, RemoteServer remoteServer) {
        return switch (labTracker.getLabStarted().getLabType()) {
            case DOCKER_COMPOSE -> remoteServer.getServerType() != ServerType.DOCKER_HOST
                    ? new LabActionResult(false, labTracker, "Server NOT DockerHost")
                    : dockerService.startDockerCompose(labTracker, remoteServer);
            case VIRTUAL_MACHINE -> remoteServer.getServerType() != ServerType.PROXMOX
                    ? new LabActionResult(false, labTracker, "Server NOT Proxmox")
                    : labTracker.getVms().isEmpty()
                    ? proxmoxService.deployProxmoxLab(labTracker, remoteServer)
                    : proxmoxService.resumeProxmoxLab(labTracker, remoteServer);
            default -> notSupported(labTracker);
        };
    }

    public LabActionResult pauseLab(LabTracker labTracker, RemoteServer remoteServer) {
        return switch (labTracker.getLabStarted().getLabType()) {
            case DOCKER_COMPOSE -> dockerService.pauseDockerCompose(labTracker, remoteServer);
            case VIRTUAL_MACHINE -> proxmoxService.pauseProxmoxLab(labTracker, remoteServer);
            default -> notSupported(labTracker);
        };
    }

    public LabActionResult teardownLab(LabTracker labTracker, RemoteServer remoteServer) {
        return switch (labTracker.getLabStarted().getLabType()) {
            case DOCKER_COMPOSE -> dockerService.teardownDockerCompose(labTracker, remoteServer);
            case VIRTUAL_MACHINE -> proxmoxService.teardownProxmoxLab(labTracker, remoteServer);
            default -> notSupported(labTracker);
        };
    }

    private static LabActionResult notSupported(LabTracker labTracker) {
        return new LabActionResult(false, labTracker,
                "Lab type " + labTracker.getLabStarted().getLabType() + " is not supported yet");
    }
}
