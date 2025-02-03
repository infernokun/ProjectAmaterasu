package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LabHandlingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LabHandlingService.class);

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
                String vm;
                return new LabActionResult();
            }
            case KUBERNETES -> {
                boolean kubernetes;
                return new LabActionResult();
            }
            case NONE -> {
                float none;
                return new LabActionResult();
            }
            default -> {
                int uhh;
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
                String vm;
                return false;
            }
            case KUBERNETES -> {
                boolean kubernetes;
                return false;
            }
            case NONE -> {
                float none;
                return false;
            }
            default -> {
                int uhh;
                return false;
            }
        }
    }
}
