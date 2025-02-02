package com.infernokun.amaterasu.services.alt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.FileUploadException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.Lab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

@Service
public class LabFileUploadService {
    private final RemoteCommandService remoteCommandService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Yaml yaml = new Yaml();

    private static final Logger LOGGER = LoggerFactory.getLogger(LabFileUploadService.class);


    public LabFileUploadService(RemoteCommandService remoteCommandService) {
        this.remoteCommandService = remoteCommandService;
    }

    public String uploadDockerComposeFile(Lab lab, AmaterasuConfig amaterasuConfig, String content) {
        try {
            JsonNode root = objectMapper.readTree(content);

            if (!root.has("content")) {
                throw new FileUploadException("JSON missing 'content' key");
            }

            String yamlContent = root.get("content").asText();

            try {
                Object parsedYaml = yaml.load(yamlContent);

                if (parsedYaml == null) {
                    throw new FileUploadException("Empty YAML content");
                }
            } catch (Exception e) {
                throw new FileUploadException("Provided content is not valid YAML: " +
                        e.getMessage(), e);
            }

            String command = String.format(
                    "DIR=%s/%s && mkdir -p $DIR && cd $DIR && " +
                            "echo \"%s\" > $DIR/%s && cat *",
                    amaterasuConfig.getUploadDir(),
                    lab.getId(),
                    yamlContent,
                    lab.getDockerFile()
            );
            RemoteCommandResponse remoteCommandResponse = remoteCommandService.handleRemoteCommand(command,
                    amaterasuConfig);

            String response = remoteCommandResponse.getBoth();

            LOGGER.info("Upload response: {}", response);
            return response;
        } catch (JsonProcessingException e) {
            throw new FileUploadException("Error processing JSON: " + e.getMessage(), e);
        }
    }
}
