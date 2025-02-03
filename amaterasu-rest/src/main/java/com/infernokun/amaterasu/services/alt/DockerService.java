package com.infernokun.amaterasu.services.alt;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.*;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;

@Service
public class DockerService {
    private DockerClientConfig dockerClientConfig;
    private DockerClient dockerClient;
    private final RemoteCommandService remoteCommandService;

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerService.class);

    public DockerService(RemoteCommandService remoteCommandService) {
        this.remoteCommandService = remoteCommandService;
    }

    public void startDockerContainer(String imageName, String host) throws URISyntaxException {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://" + host + ":2375")
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(new URI("tcp://" + host + ":2375"))
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);

        try {
            // Try pinging the Docker daemon
            dockerClient.pingCmd().exec();
            LOGGER.info("Docker daemon is responsive and running.");
        } catch (Exception e) {
            // Log any errors that occur when pinging the Docker daemon
            LOGGER.error("Error while pinging Docker daemon: {}", e.getMessage());
        }

        CreateContainerResponse containerResponse = dockerClient.createContainerCmd(imageName)
                .withName("some-docker-container")
                .exec();

        dockerClient.startContainerCmd(containerResponse.getId()).exec();
    }

    public void stopDockerContainer(String containerId) {
        dockerClient.stopContainerCmd(containerId).exec();
    }

    public LabActionResult startDockerCompose(Lab lab, LabTracker labTracker, AmaterasuConfig amaterasuConfig) {
        String dockerComposeUpCmd = String.format("cd %s/%s && docker-compose -p %s -f %s up -d",
                amaterasuConfig.getUploadDir(), lab.getId(), labTracker.getId() , lab.getDockerFile());

        RemoteCommandResponse output = remoteCommandService.handleRemoteCommand(dockerComposeUpCmd, amaterasuConfig);

        LOGGER.info("Output: \n{}", output.getBoth());

        if (output.getBoth() != null) {
            return LabActionResult.builder()
                    .labTracker(labTracker)
                    .isSuccessful(false)
                    .output(output.getBoth())
                    .build();
        } else {
            return LabActionResult.builder()
                    .labTracker(labTracker)
                    .isSuccessful(false)
                    .output(output.getBoth())
                    .build();
        }

        /*String command = String.format("cd /home/%s/app/amaterasu/%s && docker-compose up -d",
                amaterasuConfig.getDockerUser(), lab.getId());*

        CommandResult result = executeRemoteCommand(session, command);

        String output = result.getError() + " " + result.getOutput();
        LOGGER.info("Output: {}", output);*/

    }

    public boolean stopDockerCompose(Lab lab, AmaterasuConfig amaterasuConfig) {
        Session session = null;
        try {
            JSch jSch = new JSch();
            session = jSch.getSession(amaterasuConfig.getDockerUser(), amaterasuConfig.getDockerHost(), 22);
            session.setPassword(amaterasuConfig.getDockerPass());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            String command = String.format("cd /home/%s/app/amaterasu/%s && docker-compose down",
                    amaterasuConfig.getDockerUser(), lab.getId());

            RemoteCommandResponse output = remoteCommandService.handleRemoteCommand(command, amaterasuConfig);
        } catch (Exception e) {
            LOGGER.error("Exception while stopping Docker Compose: ", e);
            return false;
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        return false;
    }

    public List<Container> listContainers() {
        return dockerClient.listContainersCmd().exec();
    }

}