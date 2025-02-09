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
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        /*String dindContainerName = labTracker.getId();

        String startDockerInDockerCmd = String.format(
                "docker run --privileged --cap-add=SYS_ADMIN --security-opt apparmor=unconfined -v /lib/modules:/lib/modules:ro" +
                        " --name %s -p 0:2376 -d docker:dind && until docker exec %s docker info; do sleep 1; done && " +
                        "docker exec %s sh -c 'mkdir -p /app/amaterasu/'",
                dindContainerName, dindContainerName, dindContainerName);

        // Copy files into the DinD container
        String startComposeCmd = String.format("docker cp %s/%s/ %s:/app/amaterasu/ && docker exec %s sh -c 'cd /app/amaterasu/%s && docker-compose -p %s -f %s up -d'",
                amaterasuConfig.getUploadDir(), lab.getId(), dindContainerName, dindContainerName, lab.getId(), dindContainerName, lab.getDockerFile());

        RemoteCommandResponse dockerInDockerOutput = remoteCommandService.handleRemoteCommand(startDockerInDockerCmd, amaterasuConfig);
        RemoteCommandResponse composeOutput = remoteCommandService.handleRemoteCommand(startComposeCmd, amaterasuConfig);*/

        String catOriginalDockerComposeCmd = String.format("cat %s/%s/%s " , amaterasuConfig.getUploadDir(), lab.getId(), lab.getDockerFile());
        RemoteCommandResponse catOriginalDockerComposeOutput = remoteCommandService.handleRemoteCommand(catOriginalDockerComposeCmd, amaterasuConfig);

        String composeYAML = catOriginalDockerComposeOutput.getBoth();
        String modifiedYAML = modifyDockerComposeYAML(composeYAML, labTracker.getId(), amaterasuConfig);

        String createLabTrackerBasedFileCmd = String.format("DIR=%s/tracker-compose && mkdir -p $DIR && echo \"%s\" > $DIR/%s",
                amaterasuConfig.getUploadDir(), modifiedYAML, labTracker.getId() + "_" + lab.getDockerFile());
        RemoteCommandResponse createLabTrackerBasedFileOutput = remoteCommandService.handleRemoteCommand(createLabTrackerBasedFileCmd, amaterasuConfig);

        String startTrackerComposeCmd = String.format("cd %s/tracker-compose && docker-compose -p %s -f %s up -d",
                amaterasuConfig.getUploadDir(), labTracker.getId(), labTracker.getId() + "_" + lab.getDockerFile());
        RemoteCommandResponse startTrackerComposeOutput = remoteCommandService.handleRemoteCommand(startTrackerComposeCmd, amaterasuConfig);

        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(true)
                .output(startTrackerComposeOutput.getBoth())
                .build();

        /*String command = String.format("cd /home/%s/app/amaterasu/%s && docker-compose up -d",
                amaterasuConfig.getDockerUser(), lab.getId());*

        CommandResult result = executeRemoteCommand(session, command);

        String output = result.getError() + " " + result.getOutput();
        LOGGER.info("Output: {}", output);*/
    }

    public LabActionResult stopDockerCompose(Lab lab, LabTracker labTracker, AmaterasuConfig amaterasuConfig) {

        String stopTrackerComposeCmd = String.format("cd %s/tracker-compose && docker-compose -p %s -f %s down",
                amaterasuConfig.getUploadDir(), labTracker.getId(), labTracker.getId() + "_" + lab.getDockerFile());
        RemoteCommandResponse stopTrackerComposeOutput = remoteCommandService.handleRemoteCommand(stopTrackerComposeCmd, amaterasuConfig);

        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(true)
                .output(stopTrackerComposeCmd + "\n" + stopTrackerComposeOutput.getBoth())
                .build();
    }

    public static String modifyDockerComposeYAML(String yaml, String labTrackerId, AmaterasuConfig amaterasuConfig) {
        Yaml yamlParser = new Yaml();
        Map<String, Object> data = yamlParser.load(yaml);

        Map<String, Object> services = getMap(data, "services");
        if (services != null) {
            services.forEach((serviceName, serviceConfig) -> {
                Map<String, Object> serviceMap = getMap(serviceConfig);
                if (serviceMap != null) {
                    if (serviceMap.containsKey("volumes")) {
                        List<String> volumes = (List<String>) serviceMap.get("volumes");
                        volumes.replaceAll(originalVolume -> modifyVolume(originalVolume, labTrackerId, serviceName, amaterasuConfig));
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

    private static Map<String, Object> getMap(Object obj) {
        return (obj instanceof Map) ? (Map<String, Object>) obj : null;
    }

    private static Map<String, Object> getMap(Map<String, Object> parent, String key) {
        return (parent != null && parent.get(key) instanceof Map) ? (Map<String, Object>) parent.get(key) : null;
    }

    public static String modifyVolume(String originalVolume, String labTrackerId, String serviceName, AmaterasuConfig amaterasuConfig) {
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

    public List<Container> listContainers() {
        return dockerClient.listContainersCmd().exec();
    }

}