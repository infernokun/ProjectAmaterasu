package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.RemoteServer;
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

    public LabActionResult startLab(Lab lab, LabTracker labTracker, RemoteServer remoteServer) {
        switch (lab.getLabType()) {
            case DOCKER_CONTAINER ->  {
                return new LabActionResult();
            }
            case DOCKER_COMPOSE -> {
                return dockerService.startDockerCompose(lab, labTracker, remoteServer);
            }
            case VIRTUAL_MACHINE -> {
                return proxmoxService.startProxmoxLab(lab, labTracker, remoteServer);
            }
            case KUBERNETES -> {
                String type = "kubernetes";
                return new LabActionResult();
            }
            case NONE -> {
                String type = "none";
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
                return dockerService.stopDockerCompose(lab, labTracker, remoteServer);
            }
            case VIRTUAL_MACHINE -> {
                String type = "vm";
                return new LabActionResult();
            }
            case KUBERNETES -> {
                String type = "kubernetes";
                return new LabActionResult();

            }
            case NONE -> {
                String type = "none";
                return new LabActionResult();
            }
            default -> {
                String type = "uhh";
                return new LabActionResult();
            }
        }
    }
}
