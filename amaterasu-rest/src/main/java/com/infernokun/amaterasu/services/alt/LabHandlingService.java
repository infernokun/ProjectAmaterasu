package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.services.base.BaseService;
import org.springframework.stereotype.Service;

@Service
public class LabHandlingService extends BaseService {
    private final DockerService dockerService;

    public LabHandlingService(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    public LabActionResult startLab(Lab lab, LabTracker labTracker, AmaterasuConfig amaterasuConfig) {
        switch (lab.getLabType()) {
            case DOCKER_CONTAINER ->  {
                return new LabActionResult();
            }
            case DOCKER_COMPOSE -> {
                return dockerService.startDockerCompose(lab, labTracker, amaterasuConfig);
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

    public boolean stopLab(Lab lab, AmaterasuConfig amaterasuConfig) {
        switch (lab.getLabType()) {
            case DOCKER_CONTAINER ->  {
                return false;
            }
            case DOCKER_COMPOSE -> {
                return dockerService.stopDockerCompose(lab, amaterasuConfig);
            }
            case VIRTUAL_MACHINE -> {
                String type = "vm";
                return false;
            }
            case KUBERNETES -> {
                String type = "kubernetes";
                return false;

            }
            case NONE -> {
                String type = "none";
                return false;
            }
            default -> {
                String type = "uhh";
                return false;
            }
        }
    }
}
