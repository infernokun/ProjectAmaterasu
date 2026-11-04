package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.exceptions.LabReadinessException;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.Map;

import static com.infernokun.amaterasu.services.alt.RemoteCommandService.shellQuote;

@Service
public class LabReadinessService extends BaseService {
    private final RemoteCommandService remoteCommandService;
    private final DockerService dockerService;

    public LabReadinessService(RemoteCommandService remoteCommandService, DockerService dockerService) {
        this.remoteCommandService = remoteCommandService;
        this.dockerService = dockerService;
    }

    public boolean checkDockerComposeReadiness(Lab lab, RemoteServer dockerServer) {
        try {
            String cmd = DockerService.COMPOSE_FN + "dc -f " + shellQuote(dockerService.labFilePath(lab)) + " config -q";
            RemoteCommandResponse response = remoteCommandService.handleRemoteCommand(cmd, dockerServer);
            if (!response.isSuccess()) {
                LOGGER.error("Error running checkDockerComposeReadiness: {}", response.getBoth());
            }
            return response.isSuccess();
        } catch (RemoteCommandException e) {
            throw new LabReadinessException(e.getMessage());
        }
    }

    public Map<String, Object> getDockerComposeFile(Object object, RemoteServer dockerServer) {
        String path;
        if (object instanceof Lab lab) {
            path = dockerService.labFilePath(lab);
        } else if (object instanceof LabTracker labTracker) {
            path = dockerService.trackerFilePath(labTracker);
        } else {
            throw new IllegalArgumentException("Unsupported lab type: " + object.getClass().getName());
        }

        try {
            RemoteCommandResponse response = remoteCommandService.handleRemoteCommand("cat " + shellQuote(path), dockerServer);
            if (!response.isSuccess()) {
                throw new LabReadinessException("Could not read compose file: " + response.getError());
            }
            String yamlContent = response.getOutput();
            Map<String, Object> result = new HashMap<>();
            result.put("yml", yamlContent);
            result.put("json", new Yaml().load(yamlContent));
            return result;
        } catch (RemoteCommandException e) {
            throw new LabReadinessException(e.getMessage());
        }
    }
}
