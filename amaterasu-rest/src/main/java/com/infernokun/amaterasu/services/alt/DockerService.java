package com.infernokun.amaterasu.services.alt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.DockerServiceInfo;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

@Service
public class DockerService extends BaseService {
    private final RemoteCommandService remoteCommandService;
    private final AmaterasuConfig amaterasuConfig;

    public DockerService(RemoteCommandService remoteCommandService, AmaterasuConfig amaterasuConfig) {
        this.remoteCommandService = remoteCommandService;
        this.amaterasuConfig = amaterasuConfig;
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

            // Create the lab tracker-based Docker Compose file (base64-safe write, no shell injection)
            String trackerComposePath = String.format("%s/tracker-compose/%s",
                    amaterasuConfig.getUploadDir(),
                    labTracker.getId() + "_" + labTracker.getLabStarted().getDockerFile());
            RemoteCommandResponse createLabTrackerBasedFileOutput = remoteCommandService.writeRemoteFile(
                    trackerComposePath, modifiedYAML, remoteServer);
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
            String checkTrackerProcessCmd = String.format("cd %s/tracker-compose && docker-compose -p %s ps -qa | xargs docker inspect",
                    amaterasuConfig.getUploadDir(), labTracker.getId());
            RemoteCommandResponse checkTrackerProcessOutput = remoteCommandService.handleRemoteCommand(checkTrackerProcessCmd, remoteServer);

            if (checkTrackerProcessOutput.getExitCode() != 0) {
                return stopDockerComposeOnFail(labTracker, remoteServer,
                        "Failed to inspect Docker container. Exiting.");
            }

            // Parse stdout only: docker/compose routinely writes warnings to stderr, and
            // getBoth() would splice those into the JSON and break parsing (false failures).
            labTracker.setServices(parseDockerInspectOutput(checkTrackerProcessOutput.getOutput().trim()));

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

    /**
     * Builds a teardown command that works whether or not the tracker compose file
     * still exists. If the file is present we use {@code docker-compose down}; if a
     * failure happened before the file was written we fall back to removing any
     * containers labelled with the compose project id. The old command always passed
     * {@code -f <file>} and chained with {@code &&}, so a missing file (or a failed
     * {@code logs}) left containers orphaned on every failure.
     */
    private String buildTeardownCommand(LabTracker labTracker) {
        String composeFileName = labTracker.getId() + "_" + labTracker.getLabStarted().getDockerFile();
        return String.format(
                "cd %s/tracker-compose && if [ -f %s ]; then docker-compose -p %s -f %s down; " +
                        "else docker ps -aq --filter label=com.docker.compose.project=%s | xargs -r docker rm -f; fi",
                amaterasuConfig.getUploadDir(), composeFileName, labTracker.getId(), composeFileName,
                labTracker.getId());
    }

    public LabActionResult stopDockerComposeOnFail(LabTracker labTracker, RemoteServer remoteServer, String msg) {
        // Capture logs (best-effort, separated by ';' so a logs failure never blocks teardown),
        // then tear the project down.
        String stopTrackerComposeCmd = String.format("cd %s/tracker-compose && docker-compose -p %s logs 2>/dev/null; %s",
                amaterasuConfig.getUploadDir(), labTracker.getId(), buildTeardownCommand(labTracker));
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
            String stopTrackerComposeCmd = buildTeardownCommand(labTracker);
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
            Map<String, Object> finalVolumes = getMap(data, "volumes");
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

    public static List<DockerServiceInfo> parseDockerInspectOutput(String jsonOutput) {
        List<DockerServiceInfo> containerInfos = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // Parse the JSON output
            JsonNode rootNode = objectMapper.readTree(jsonOutput);

            for (JsonNode containerNode : rootNode) {
                DockerServiceInfo info = new DockerServiceInfo();

                // Extract Name (remove leading "/"). Use path() throughout so a missing
                // field yields a MissingNode instead of null -> no NPE on containers that
                // lack ExposedPorts, Mounts, networks, etc.
                info.setName(containerNode.path("Name").asText("").replaceFirst("^/", ""));
                // Extract State
                info.setState(containerNode.path("State").path("Status").asText("unknown"));

                // Extract IP addresses and network names in a single pass
                containerNode.path("NetworkSettings").path("Networks").fields().forEachRemaining(
                        entry -> {
                            String ip = entry.getValue().path("IPAddress").asText("");
                            if (!ip.isEmpty()) {
                                info.getIpAddresses().add(ip);
                            }
                            info.getNetworks().add(entry.getKey());
                        });

                // Extract Internal Ports (may be absent for containers with no exposed ports)
                JsonNode exposedPorts = containerNode.path("Config").path("ExposedPorts");
                if (exposedPorts.isObject()) {
                    exposedPorts.fieldNames().forEachRemaining(info.getPorts()::add);
                }

                // Extract Mounts
                JsonNode mounts = containerNode.path("Mounts");
                if (mounts.isArray()) {
                    mounts.forEach(mountNode -> {
                        Map<String, String> newMap = new HashMap<>();
                        newMap.put("hostVolume", mountNode.path("Source").asText(""));
                        newMap.put("containerVolume", mountNode.path("Destination").asText(""));

                        info.getVolumes().add(newMap);
                    });
                }

                containerInfos.add(info);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return containerInfos;
    }

}