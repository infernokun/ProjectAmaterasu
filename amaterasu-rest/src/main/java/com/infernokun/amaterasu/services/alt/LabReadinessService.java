package com.infernokun.amaterasu.services.alt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.LabReadinessException;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.Lab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.Map;

@Service
public class LabReadinessService {
    private final RemoteCommandService remoteCommandService;

    private static final Logger LOGGER = LoggerFactory.getLogger(LabReadinessService.class);

    public LabReadinessService(RemoteCommandService remoteCommandService) {
        this.remoteCommandService = remoteCommandService;
    }

    public boolean checkDockerComposeReadiness(Lab lab, AmaterasuConfig amaterasuConfig) {
        try {
            String cmd = String.format("DIR=%s/%s && cd $DIR && docker-compose -f %s config",
                    amaterasuConfig.getUploadDir(), lab.getId(), lab.getDockerFile());

            RemoteCommandResponse remoteCommandResponse = remoteCommandService.handleRemoteCommand(cmd, amaterasuConfig);

            String response = remoteCommandResponse.getBoth();

            return !response.contains("did not find expected key") && !response.contains("not allowed") &&
                    !response.contains("No such file or directory");
        } catch (RemoteCommandException e) {
            throw new LabReadinessException(e.getMessage());
        }
    }

    public Map<String, Object> getDockerComposeFile(Lab lab, AmaterasuConfig amaterasuConfig) {
        try {
            String cmd = String.format("DIR=%s/%s && cd $DIR && cat %s",
                    amaterasuConfig.getUploadDir(), lab.getId(), lab.getDockerFile());

            RemoteCommandResponse remoteCommandResponse = remoteCommandService.handleRemoteCommand(cmd, amaterasuConfig);
            String yamlContent = remoteCommandResponse.getBoth();

            Yaml yaml = new Yaml();
            ObjectMapper objectMapper = new ObjectMapper();

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
