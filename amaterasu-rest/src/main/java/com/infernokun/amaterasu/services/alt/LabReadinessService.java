package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.LabReadinessException;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class LabReadinessService extends BaseService {
    private final RemoteCommandService remoteCommandService;
    private final AmaterasuConfig amaterasuConfig;

    public LabReadinessService(RemoteCommandService remoteCommandService, AmaterasuConfig amaterasuConfig) {
        this.remoteCommandService = remoteCommandService;
        this.amaterasuConfig = amaterasuConfig;
    }

    public boolean checkDockerComposeReadiness(Lab lab, RemoteServer dockerServer) {
        try {
            String cmd = String.format("DIR=%s/%s && cd $DIR && docker-compose -f %s config",
                    amaterasuConfig.getUploadDir(), lab.getId(), lab.getDockerFile());

            RemoteCommandResponse remoteCommandResponse = remoteCommandService.handleRemoteCommand(cmd, dockerServer);

            int exitCode = remoteCommandResponse.getExitCode();
            if (exitCode != 0) {
                LOGGER.error("Error running checkDockerComposeReadiness: {}", remoteCommandResponse.getBoth());
            }
            return remoteCommandResponse.getExitCode() == 0;
        } catch (RemoteCommandException e) {
            throw new LabReadinessException(e.getMessage());
        }
    }

    public  Map<String, Object> getDockerComposeFile(Object object, RemoteServer dockerServer) {
        try {
            String cmd = getString(object);

            RemoteCommandResponse remoteCommandResponse = remoteCommandService.handleRemoteCommand(cmd, dockerServer);
            String yamlContent = remoteCommandResponse.getBoth();

            Yaml yaml = new Yaml();

            // Parse YAML into a Map
            Map<String, Object> yamlMap = yaml.load(yamlContent);

            // Create a response Map containing both JSON and raw YAML
            Map<String, Object> result = new HashMap<>();
            result.put("yml", yamlContent);
            result.put("json", yamlMap);

            // Convert Map to JSON string
            return result;
        } catch (RemoteCommandException e) {
            throw new LabReadinessException(e.getMessage());
        }
    }

    private String getString(Object object) {
        String cmd;
        if (object instanceof Lab) {
            cmd = String.format("DIR=%s/%s && cd $DIR && cat %s",
                    amaterasuConfig.getUploadDir(), ((Lab) object).getId(), ((Lab) object).getDockerFile());
        } else if (object instanceof LabTracker) {
            cmd = String.format("DIR=%s/tracker-compose && cd $DIR && cat %s",
                    amaterasuConfig.getUploadDir(), ((LabTracker) object).getId() + "_" + ((LabTracker) object).getLabStarted().getDockerFile());
        } else {
            throw new IllegalArgumentException("Unsupported lab type: " + object.getClass().getName());
        }
        return cmd;
    }

    public Map<String, Object> getTrackerComposeFile(LabTracker labTracker, RemoteServer dockerServer) {
        try {
            String cmd = String.format("DIR=%s/tracker-compose && cd $DIR && cat %s",
                    amaterasuConfig.getUploadDir(), labTracker.getId() + "_" +labTracker.getLabStarted().getDockerFile());

            RemoteCommandResponse remoteCommandResponse = remoteCommandService.handleRemoteCommand(cmd, dockerServer);
            String yamlContent = remoteCommandResponse.getBoth();

            Yaml yaml = new Yaml();

            // Parse YAML into a Map
            Map<String, Object> yamlMap = yaml.load(yamlContent);

            // Create a response Map containing both JSON and raw YAML
            Map<String, Object> result = new HashMap<>();
            result.put("yml", yamlContent);
            result.put("json", yamlMap);

            // Convert Map to JSON string
            return result;
        } catch (RemoteCommandException e) {
            throw new LabReadinessException(e.getMessage());
        }
    }
}
