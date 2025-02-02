package com.infernokun.amaterasu.services.alt;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.*;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.Lab;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    public boolean startDockerCompose(Lab lab, AmaterasuConfig amaterasuConfig) {
        String catDockerCompose = String.format("cd %s/%s && docker-compose -f %s up -d ",
                amaterasuConfig.getUploadDir(), lab.getId(), lab.getDockerFile());

        RemoteCommandResponse output = remoteCommandService.handleRemoteCommand(catDockerCompose, amaterasuConfig);

        LOGGER.info("Output: \n{}", output.getBoth());

        /*String command = String.format("cd /home/%s/app/amaterasu/%s && docker-compose up -d",
                amaterasuConfig.getDockerUser(), lab.getId());*

        CommandResult result = executeRemoteCommand(session, command);

        String output = result.getError() + " " + result.getOutput();
        LOGGER.info("Output: {}", output);*/
        return false;
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

            CommandResult result = executeRemoteCommand(session, command);

            if (result.isSuccess()) {
                LOGGER.info("Docker Compose stopped successfully: {}", result.getOutput());
                return true;
            } else {
                LOGGER.error("Failed to stop Docker Compose: {}", result.getError());
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Exception while stopping Docker Compose: ", e);
            return false;
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    public List<Container> listContainers() {
        return dockerClient.listContainersCmd().exec();
    }

    private CommandResult executeRemoteCommand(Session session, String command) throws JSchException, IOException {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        InputStream inputStream = channel.getInputStream();
        InputStream errorStream = channel.getErrStream();

        channel.connect();

        String output = readStream(inputStream);
        String errorOutput = readStream(errorStream);

        channel.disconnect();

        return new CommandResult(output, errorOutput);
    }

    private String readStream(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString().trim();
    }

    private static class CommandResult {
        private final String output;
        private final String error;

        public CommandResult(String output, String error) {
            this.output = output;
            this.error = error;
        }

        public String getOutput() {
            return output;
        }

        public String getError() {
            return error;
        }

        public boolean isSuccess() {
            return error.isEmpty();
        }
    }
}