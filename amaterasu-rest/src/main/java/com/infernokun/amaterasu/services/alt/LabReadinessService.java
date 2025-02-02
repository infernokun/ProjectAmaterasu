package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.LabReadinessException;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.Lab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

            LOGGER.info(response);

            return !response.contains("did not find expected key") && !response.contains("not allowed") &&
                    !response.contains("No such file or directory");
        } catch (RemoteCommandException e) {
            throw new LabReadinessException(e.getMessage());
        }
    }
}
