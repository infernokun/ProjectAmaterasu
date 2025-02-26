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
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.DockerServiceInfo;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.models.enums.ServerType;
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
    private final LabTrackerService labTrackerService;
    private final AmaterasuConfig amaterasuConfig;

    private DockerClientConfig dockerClientConfig;
    private DockerClient dockerClient;

    public DockerService(RemoteCommandService remoteCommandService, LabTrackerService labTrackerService, AmaterasuConfig amaterasuConfig) {
        this.remoteCommandService = remoteCommandService;
        this.labTrackerService = labTrackerService;
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

    public LabActionResult startDockerCompose(LabTracker labTracker, RemoteServer remoteServer) {
        String catOriginalDockerComposeCmd = String.format("cat %s/%s/%s", amaterasuConfig.getUploadDir(), labTracker.getLabStarted().getId(), labTracker.getLabStarted().getDockerFile());
        RemoteCommandResponse catOriginalDockerComposeOutput = remoteCommandService.handleRemoteCommand(catOriginalDockerComposeCmd, remoteServer);

        String composeYAML = catOriginalDockerComposeOutput.getBoth();
        String modifiedYAML = modifyDockerComposeYAML(composeYAML, labTracker.getId());

        String createLabTrackerBasedFileCmd = String.format("DIR=%s/tracker-compose && mkdir -p $DIR && echo \"%s\" > $DIR/%s",
                amaterasuConfig.getUploadDir(), modifiedYAML, labTracker.getId() + "_" + labTracker.getLabStarted().getDockerFile());
        RemoteCommandResponse createLabTrackerBasedFileOutput = remoteCommandService.handleRemoteCommand(createLabTrackerBasedFileCmd, remoteServer);

        String startTrackerComposeCmd = String.format("cd %s/tracker-compose && docker-compose -p %s -f %s up -d ",
                amaterasuConfig.getUploadDir(), labTracker.getId(), labTracker.getId() + "_" + labTracker.getLabStarted().getDockerFile());
        RemoteCommandResponse startTrackerComposeOutput = remoteCommandService.handleRemoteCommand(startTrackerComposeCmd, remoteServer);

        //String checkTrackerProcessCmd = String.format("cd %s/tracker-compose && docker-compose -p %s -f %s ps -a",
        //       amaterasuConfig.getUploadDir(), labTracker.getId(), labTracker.getId() + "_" + lab.getDockerFile());
        //RemoteCommandResponse checkTrackerProcessOutput = remoteCommandService.handleRemoteCommand(checkTrackerProcessCmd, amaterasuConfig);

        String checkTrackerProcessCmd = String.format("cd %s/tracker-compose && docker-compose -p %s -f %s ps -q | xargs docker inspect",
                amaterasuConfig.getUploadDir(), labTracker.getId(), labTracker.getId() + "_" + labTracker.getLabStarted().getDockerFile());
        RemoteCommandResponse checkTrackerProcessOutput = remoteCommandService.handleRemoteCommand(checkTrackerProcessCmd, remoteServer);

        labTracker.setServices(parseDockerInspectOutput(checkTrackerProcessOutput.getBoth().trim()));

        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(true)
                .output(labTracker.getServices().toString())
                .build();
    }

    public LabActionResult stopDockerCompose(String labTrackerId, RemoteServer remoteServer) {
        LabTracker labTracker = labTrackerService.findLabTrackerById(labTrackerId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTracker not found"));

        String stopTrackerComposeCmd = String.format("cd %s/tracker-compose && docker-compose -p %s -f %s down",
                amaterasuConfig.getUploadDir(), labTracker.getId(), labTracker.getId() + "_" + labTracker.getLabStarted().getDockerFile());
        RemoteCommandResponse stopTrackerComposeOutput = remoteCommandService.handleRemoteCommand(stopTrackerComposeCmd, remoteServer);

        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(true)
                .output(stopTrackerComposeCmd + "\n" + stopTrackerComposeOutput.getBoth())
                .build();
    }

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

        /* Ensure volumes section is updated
        Map<String, Object> volumes = getMap(data, "volumes");
        if (volumes == null) {
            volumes = new LinkedHashMap<>();
            data.put("volumes", volumes);
        }*/

        if (services != null && getMap(data, "volumes") != null) {
            Map<String, Object> finalVolumes = getMap(data, "volumes");;
            services.keySet().forEach(serviceName -> finalVolumes.put(serviceName + "_" + labTrackerId, null));
        }

        return new Yaml().dump(data);
    }
    private Map<String, Object> getMap(Object obj) {
        return (obj instanceof Map) ? (Map<String, Object>) obj : null;
    }

    private Map<String, Object> getMap(Map<String, Object> parent, String key) {
        return (parent != null && parent.get(key) instanceof Map) ? (Map<String, Object>) parent.get(key) : null;
    }

    public String modifyVolume(String originalVolume, String labTrackerId, String serviceName) {
        String[] parts = originalVolume.split(":");
        String sourcePath = parts[0];
        String modifiedVolumePath;

        // If it starts with ./, replace it with <serviceName>_<labTrackerId>
        if (sourcePath.startsWith("./") || sourcePath.startsWith("/")) {
            modifiedVolumePath = amaterasuConfig.getUploadDir() + "/tracker-volume/"+ labTrackerId + "/" + serviceName + sourcePath.substring(1);
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
                    info.getVolumes().put("hostVolume", mountNode.get("Source").asText());
                    info.getVolumes().put("containerVolume", mountNode.get("Destination").asText());
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