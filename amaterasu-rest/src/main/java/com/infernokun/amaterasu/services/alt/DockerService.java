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
import com.infernokun.amaterasu.services.base.BaseService;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;

@Service
public class DockerService extends BaseService {
    private DockerClientConfig dockerClientConfig;
    private DockerClient dockerClient;
    private final RemoteCommandService remoteCommandService;

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
        /*String dockerComposeUpCmd = String.format("cd %s/%s && docker-compose -p %s -f %s up -d",
                amaterasuConfig.getUploadDir(), lab.getId(), labTracker.getId() , lab.getDockerFile());

        RemoteCommandResponse upOutput = remoteCommandService.handleRemoteCommand(dockerComposeUpCmd, amaterasuConfig);

        String dockerComposeDownCmd = String.format("cd %s/%s && docker-compose -p %s -f %s down\n",
                amaterasuConfig.getUploadDir(), lab.getId(), labTracker.getId() , lab.getDockerFile());

        RemoteCommandResponse downOutput = remoteCommandService.handleRemoteCommand(dockerComposeDownCmd, amaterasuConfig);*/

        String dindContainerName = labTracker.getId();

        String startDockerInDockerCmd = String.format(
                "docker run --privileged --name %s -d docker:dind && docker exec %s sh -c 'mkdir -p " +
                        "/app/amaterasu/'",
                dindContainerName, dindContainerName);

        // Copy files into the DinD container
        String startComposeCmd = String.format("docker cp %s/%s/ %s:/app/amaterasu/ && docker exec %s sh -c 'cd /app/amaterasu/%s && docker-compose -p %s -f %s up -d'",
                amaterasuConfig.getUploadDir(), lab.getId(), dindContainerName, dindContainerName, lab.getId(), dindContainerName, lab.getDockerFile());

        RemoteCommandResponse dockerInDockerOutput = remoteCommandService.handleRemoteCommand(startDockerInDockerCmd, amaterasuConfig);
        RemoteCommandResponse composeOutput = remoteCommandService.handleRemoteCommand(startComposeCmd, amaterasuConfig);

        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(false)
                .output(startDockerInDockerCmd + "\n" + dockerInDockerOutput.getBoth() + "\n" + startComposeCmd + "\n" + composeOutput.getBoth())
                .build();

        /*String command = String.format("cd /home/%s/app/amaterasu/%s && docker-compose up -d",
                amaterasuConfig.getDockerUser(), lab.getId());*

        CommandResult result = executeRemoteCommand(session, command);

        String output = result.getError() + " " + result.getOutput();
        LOGGER.info("Output: {}", output);*/

    }

    public boolean stopDockerCompose(Lab lab, AmaterasuConfig amaterasuConfig) {
        Session session = null;
        try {

            String command = String.format("cd /home/%s/app/amaterasu/%s && docker-compose down",
                    amaterasuConfig.getDockerUser(), lab.getId());

            RemoteCommandResponse output = remoteCommandService.handleRemoteCommand(command, amaterasuConfig);
        } catch (Exception e) {
            LOGGER.error("Exception while stopping Docker Compose: ", e);
            return false;
        }
        return false;
    }

    public List<Container> listContainers() {
        return dockerClient.listContainersCmd().exec();
    }

}