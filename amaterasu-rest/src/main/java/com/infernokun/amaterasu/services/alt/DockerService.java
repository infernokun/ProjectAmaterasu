package com.infernokun.amaterasu.services.alt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.*;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.DockerServiceInfo;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.entity.LabTrackerService;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;

@Service
public class DockerService extends BaseService {
    private final RemoteCommandService remoteCommandService;
    private final AmaterasuConfig amaterasuConfig;

    private DockerClientConfig dockerClientConfig;
    private DockerClient dockerClient;

    public DockerService(RemoteCommandService remoteCommandService, AmaterasuConfig amaterasuConfig) {
        this.remoteCommandService = remoteCommandService;
        this.amaterasuConfig = amaterasuConfig;
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

    public boolean dockerHealthCheck(RemoteServer remoteServer) {
        return remoteCommandService.validateConnection(remoteServer);
    }

    public LabActionResult startDockerCompose(LabTracker labTracker, RemoteServer remoteServer) {
        try {
            // Fetch the original Docker Compose file content
            String catOriginalDockerComposeCmd = String.format("cat %s/%s/%s",
                    amaterasuConfig.getUploadDir(),
                    labTracker.getLabStarted().getId(),
                    labTracker.getLabStarted().getDockerFile());
            RemoteCommandResponse catOriginalDockerComposeOutput = remoteCommandService.handleRemoteCommand(catOriginalDockerComposeCmd, remoteServer);
            LOGGER.info("Fetched original Docker Compose: {}", catOriginalDockerComposeOutput.getBoth());

            if (catOriginalDockerComposeOutput.getExitCode() != 0) {
                return stopDockerComposeOnFail(labTracker, remoteServer,
                        "Failed to fetch Docker Compose file. Exiting.");
            }

            String composeYAML = catOriginalDockerComposeOutput.getBoth();
            String modifiedYAML = modifyDockerComposeYAML(composeYAML, labTracker.getId());
            LOGGER.info("Modified Docker Compose YAML for LabTracker: {}", modifiedYAML);

            // Create the lab tracker-based Docker Compose file
            String createLabTrackerBasedFileCmd = String.format("DIR=%s/tracker-compose && mkdir -p $DIR && echo \"%s\" | tee $DIR/%s",
                    amaterasuConfig.getUploadDir(), modifiedYAML, labTracker.getId() + "_" + labTracker.getLabStarted().getDockerFile());
            RemoteCommandResponse createLabTrackerBasedFileOutput = remoteCommandService.handleRemoteCommand(createLabTrackerBasedFileCmd, remoteServer);
            LOGGER.info("Docker Compose file creation response: {}", createLabTrackerBasedFileOutput.getBoth());

            if (createLabTrackerBasedFileOutput.getExitCode() != 0) {
                return stopDockerComposeOnFail(labTracker, remoteServer,
                        "Failed to create Docker Compose file for LabTracker. Exiting.");
            }

            // Start the Docker Compose container
            String startTrackerComposeCmd = String.format("cd %s/tracker-compose && docker-compose -p %s -f %s up -d ",
                    amaterasuConfig.getUploadDir(), labTracker.getId(), labTracker.getId() + "_" + labTracker.getLabStarted().getDockerFile());
            RemoteCommandResponse startTrackerComposeOutput = remoteCommandService.handleRemoteCommand(startTrackerComposeCmd, remoteServer);
            LOGGER.info("Docker Compose start output: {}", startTrackerComposeOutput.getBoth());

            if (startTrackerComposeOutput.getExitCode() != 0) {
                return stopDockerComposeOnFail(labTracker, remoteServer,
                        "Failed to start Docker Compose container. Exiting.");
            }

            // Check the Docker process status
            String checkTrackerProcessCmd = String.format("cd %s/tracker-compose && docker-compose -p %s ps -q | xargs docker inspect",
                    amaterasuConfig.getUploadDir(), labTracker.getId());
            RemoteCommandResponse checkTrackerProcessOutput = remoteCommandService.handleRemoteCommand(checkTrackerProcessCmd, remoteServer);
            LOGGER.info("Docker inspect output: {}", checkTrackerProcessOutput.getBoth());

            if (checkTrackerProcessOutput.getExitCode() != 0) {
                return stopDockerComposeOnFail(labTracker, remoteServer,
                        "Failed to inspect Docker container. Exiting.");
            }

            labTracker.setServices(parseDockerInspectOutput(checkTrackerProcessOutput.getBoth().trim()));

            return LabActionResult.builder()
                    .labTracker(labTracker)
                    .isSuccessful(true)
                    .output(labTracker.getServices().toString())
                    .build();
        } catch (Exception e) {
            return stopDockerComposeOnFail(labTracker, remoteServer,
                    "An error occurred while starting the Docker Compose: " + e.getMessage());
        }
    }

    public LabActionResult stopDockerComposeOnFail(LabTracker labTracker, RemoteServer remoteServer, String msg) {
        String stopTrackerComposeCmd = String.format("cd %s/tracker-compose && docker-compose -p %s logs && docker-compose -p %s -f %s down",
                amaterasuConfig.getUploadDir(), labTracker.getId(), labTracker.getId(), labTracker.getId() + "_" + labTracker.getLabStarted().getDockerFile());
        RemoteCommandResponse stopTrackerComposeOutput = remoteCommandService.handleRemoteCommand(stopTrackerComposeCmd, remoteServer);
        if (stopTrackerComposeOutput.getExitCode() != 0) {
            LOGGER.error("Critical failed: Failed to stop the docker compose!!! ({})", msg);
            return LabActionResult.builder()
                    .labTracker(labTracker)
                    .isSuccessful(false)
                    .output("Critical failed: Failed to stop the docker compose!!! (" + msg + stopTrackerComposeOutput.getBoth() + ")")
                    .build();
        }
        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(false)
                .output("Start failure: " + msg)
                .build();
    }

    public LabActionResult stopDockerCompose(LabTracker labTracker, RemoteServer remoteServer) {
        try {
            String stopTrackerComposeCmd = String.format("cd %s/tracker-compose && docker-compose -p %s -f %s down",
                    amaterasuConfig.getUploadDir(), labTracker.getId(), labTracker.getId() + "_" + labTracker.getLabStarted().getDockerFile());
            RemoteCommandResponse stopTrackerComposeOutput = remoteCommandService.handleRemoteCommand(stopTrackerComposeCmd, remoteServer);
            if (stopTrackerComposeOutput.getExitCode() != 0) {
                LOGGER.error("Failed to stop the docker compose!!!");
                return LabActionResult.builder()
                        .labTracker(labTracker)
                        .isSuccessful(false)
                        .output("Failed to stop the docker compose!!!")
                        .build();
            }
            return LabActionResult.builder()
                    .labTracker(labTracker)
                    .isSuccessful(true)
                    .output(stopTrackerComposeCmd + "\n" + stopTrackerComposeOutput.getBoth())
                    .build();
        } catch (Exception e) {
            LOGGER.error("An error occurred while stopping the Docker Compose: ", e);
            return LabActionResult.builder()
                    .labTracker(labTracker)
                    .isSuccessful(false)
                    .output("An error occurred: " + e.getMessage())
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    public String modifyDockerComposeYAML(String yaml, String labTrackerId) {
        Yaml yamlParser = new Yaml();
        Map<String, Object> data = yamlParser.load(yaml);

        Map<String, Object> services = getMap(data, "services");
        if (services != null) {
            services.forEach((serviceName, serviceConfig) -> {
                Map<String, Object> serviceMap = getMap(serviceConfig);
                if (serviceMap != null) {
                    if (serviceMap.containsKey("volumes")) {
                        List<String> volumes = (List<String>) serviceMap.get("volumes");
                        volumes.replaceAll(originalVolume -> modifyVolume(originalVolume, labTrackerId, serviceName));
                    }

                    serviceMap.remove("ports");
                }
            });
        }

        if (services != null && getMap(data, "volumes") != null) {
            Map<String, Object> finalVolumes = getMap(data, "volumes");;
            services.keySet().forEach(serviceName -> finalVolumes.put(serviceName + "_" + labTrackerId, null));
        }

        return new Yaml().dump(data);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Object obj) {
        return (obj instanceof Map) ? (Map<String, Object>) obj : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> parent, String key) {
        return (parent != null && parent.get(key) instanceof Map<?, ?>) ? (Map<String, Object>) parent.get(key) : null;
    }

    public String modifyVolume(String originalVolume, String labTrackerId, String serviceName) {
        String[] parts = originalVolume.split(":");
        String sourcePath = parts[0];
        String modifiedVolumePath;

        // If it starts with ./, replace it with <serviceName>_<labTrackerId>
        if (sourcePath.startsWith("./") || sourcePath.startsWith("/")) {
            modifiedVolumePath = amaterasuConfig.getUploadDir() + "/tracker-volume/"+ labTrackerId + "/" + serviceName + "/" + sourcePath.substring(1);
        } else {
            // Otherwise, treat it as a named volume
            modifiedVolumePath = serviceName + "_" + labTrackerId;
        }

        // Preserve the target path if available
        if (parts.length > 1) {
            String targetPath = parts[1];
            if (parts.length == 3) {
                return modifiedVolumePath + ":" + targetPath + ":" + parts[2]; // Preserve read-only flag
            }
            return modifiedVolumePath + ":" + targetPath;
        }

        return modifiedVolumePath;
    }

    public List<DockerServiceInfo> parseDockerInspectOutput(String jsonOutput) {
        List<DockerServiceInfo> containerInfos = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // Parse the JSON output
            JsonNode rootNode = objectMapper.readTree(jsonOutput);

            for (JsonNode containerNode : rootNode) {
                DockerServiceInfo info = new DockerServiceInfo();

                // Extract Name (remove leading "/")
                info.setName(containerNode.get("Name").asText().substring(1));
                // Extract State
                info.setState(containerNode.get("State").get("Status").asText());

                // Extract IP addresses
                containerNode.get("NetworkSettings").get("Networks").fields().forEachRemaining(
                        entry -> {
                            String ip = entry.getValue().get("IPAddress").asText();

                            if (!Objects.equals(ip, "")) {
                                info.getIpAddresses().add(entry.getValue().get("IPAddress").asText());
                            }
                        });

                containerNode.get("NetworkSettings").get("Networks").fields().forEachRemaining(
                        entry -> info.getNetworks().add(entry.getKey()));

                // Extract Internal Ports
                containerNode.get("Config").get("ExposedPorts").fieldNames().forEachRemaining(info.getPorts()::add);

                // Extract Mounts
                containerNode.get("Mounts").forEach(mountNode -> {
                    Map<String, String> newMap = new HashMap<>();
                    newMap.put("hostVolume", mountNode.get("Source").asText());
                    newMap.put("containerVolume", mountNode.get("Destination").asText());

                    info.getVolumes().add(newMap);
                });

                containerInfos.add(info);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return containerInfos;
    }
    public List<Container> listContainers() {
        return dockerClient.listContainersCmd().exec();
    }

}