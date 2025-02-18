package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.FileUploadException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

@Service
public class LabFileUploadService extends BaseService {
    private final RemoteCommandService remoteCommandService;
    private final Yaml yaml = new Yaml();

    public LabFileUploadService(RemoteCommandService remoteCommandService) {
        this.remoteCommandService = remoteCommandService;
    }

    public String uploadDockerComposeFile(Lab lab, AmaterasuConfig amaterasuConfig, String content) {
        try {
            Object parsedYaml = yaml.load(content);

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
                content,
                lab.getDockerFile()
        );
        RemoteCommandResponse remoteCommandResponse = remoteCommandService.handleRemoteCommand(command,
                amaterasuConfig);

        String response = remoteCommandResponse.getBoth();

        LOGGER.info("Upload response: {}", response);
        return response;
    }
}
