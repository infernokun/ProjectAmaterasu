package com.infernokun.amaterasu.services.alt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.DockerServiceInfo;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.infernokun.amaterasu.services.alt.RemoteCommandService.shellQuote;

@Service
public class DockerService extends BaseService {
    // Prefer the Compose v2 plugin and fall back to the standalone v1 binary
    static final String COMPOSE_FN =
            "dc() { if docker compose version >/dev/null 2>&1; then docker compose \"$@\"; else docker-compose \"$@\"; fi; }; ";
    private static final Pattern SERVICE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");

    private final RemoteCommandService remoteCommandService;
    private final AmaterasuConfig amaterasuConfig;

    public DockerService(RemoteCommandService remoteCommandService, AmaterasuConfig amaterasuConfig) {
        this.remoteCommandService = remoteCommandService;
        this.amaterasuConfig = amaterasuConfig;
    }

    public record ComposeRewrite(String yaml, List<String> bindSources) {
    }

    public boolean dockerHealthCheck(RemoteServer remoteServer) {
        return remoteCommandService.validateConnection(remoteServer);
    }

    public String labFilePath(Lab lab) {
        return amaterasuConfig.getUploadDir() + "/" + lab.getId() + "/" + lab.getDockerFile();
    }

    public String trackerFilePath(LabTracker labTracker) {
        return amaterasuConfig.getUploadDir() + "/tracker-compose/" + labTracker.getId() + "_"
                + labTracker.getLabStarted().getDockerFile();
    }

    public String trackerVolumeDir(LabTracker labTracker) {
        return amaterasuConfig.getUploadDir() + "/tracker-volume/" + labTracker.getId();
    }

    private String compose(LabTracker labTracker, String args) {
        return COMPOSE_FN + "dc -p " + shellQuote(labTracker.getId()) + " -f "
                + shellQuote(trackerFilePath(labTracker)) + " " + args;
    }

    public LabActionResult startDockerCompose(LabTracker labTracker, RemoteServer remoteServer) {
        try {
            RemoteCommandResponse labFile = remoteCommandService.handleRemoteCommand(
                    "cat " + shellQuote(labFilePath(labTracker.getLabStarted())), remoteServer);
            if (!labFile.isSuccess()) {
                return stopDockerComposeOnFail(labTracker, remoteServer,
                        "Failed to fetch Docker Compose file: " + labFile.getError());
            }

            ComposeRewrite rewrite = rewriteCompose(labFile.getOutput(), labTracker);
            LOGGER.info("Modified Docker Compose YAML for LabTracker {}: {}", labTracker.getId(), rewrite.yaml());

            RemoteCommandResponse writeResponse = remoteCommandService.writeRemoteFile(
                    trackerFilePath(labTracker), rewrite.yaml(), remoteServer);
            if (!writeResponse.isSuccess()) {
                return stopDockerComposeOnFail(labTracker, remoteServer,
                        "Failed to create Docker Compose file for LabTracker: " + writeResponse.getError());
            }

            if (!rewrite.bindSources().isEmpty()) {
                RemoteCommandResponse prepare = remoteCommandService.handleRemoteCommand(
                        prepareBindSourcesCommand(rewrite.bindSources()), remoteServer);
                if (!prepare.isSuccess()) {
                    return stopDockerComposeOnFail(labTracker, remoteServer,
                            "Failed to prepare lab volumes: " + prepare.getError());
                }
            }

            RemoteCommandResponse up = remoteCommandService.handleRemoteCommand(
                    compose(labTracker, "up -d --remove-orphans"), remoteServer);
            LOGGER.info("Docker Compose start output: {}", up.getBoth());
            if (!up.isSuccess()) {
                return stopDockerComposeOnFail(labTracker, remoteServer,
                        "Failed to start Docker Compose: " + up.getError());
            }

            Optional<List<DockerServiceInfo>> services = inspectServices(labTracker, remoteServer);
            if (services.isEmpty()) {
                return stopDockerComposeOnFail(labTracker, remoteServer, "Failed to inspect Docker containers.");
            }
            labTracker.setServices(services.get());

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

    /** Stops the containers but keeps them, so the next start picks up where the lab left off. */
    public LabActionResult pauseDockerCompose(LabTracker labTracker, RemoteServer remoteServer) {
        RemoteCommandResponse response = remoteCommandService.handleRemoteCommand(
                compose(labTracker, "stop"), remoteServer);
        if (response.isSuccess()) {
            inspectServices(labTracker, remoteServer).ifPresent(labTracker::setServices);
        }
        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(response.isSuccess())
                .output(response.isSuccess() ? "Lab paused" : "Failed to pause the lab: " + response.getError())
                .build();
    }

    /** Removes the containers, volumes, generated compose file and uploaded volume files. */
    public LabActionResult teardownDockerCompose(LabTracker labTracker, RemoteServer remoteServer) {
        try {
            RemoteCommandResponse response = remoteCommandService.handleRemoteCommand(
                    buildTeardownCommand(labTracker), remoteServer);
            if (!response.isSuccess()) {
                LOGGER.error("Failed to tear down docker compose for {}: {}", labTracker.getId(), response.getError());
                return LabActionResult.builder()
                        .labTracker(labTracker)
                        .isSuccessful(false)
                        .output("Failed to tear down the lab: " + response.getError())
                        .build();
            }
            labTracker.setServices(new ArrayList<>());
            return LabActionResult.builder()
                    .labTracker(labTracker)
                    .isSuccessful(true)
                    .output(response.getBoth())
                    .build();
        } catch (Exception e) {
            LOGGER.error("An error occurred while tearing down the Docker Compose: ", e);
            return LabActionResult.builder()
                    .labTracker(labTracker)
                    .isSuccessful(false)
                    .output("An error occurred: " + e.getMessage())
                    .build();
        }
    }

    public LabActionResult stopDockerComposeOnFail(LabTracker labTracker, RemoteServer remoteServer, String msg) {
        String logs = remoteCommandService.handleRemoteCommand(
                compose(labTracker, "logs --no-color --tail 200"), remoteServer).getBoth();
        RemoteCommandResponse teardown = remoteCommandService.handleRemoteCommand(
                buildTeardownCommand(labTracker), remoteServer);
        if (!teardown.isSuccess()) {
            LOGGER.error("Critical failure: failed to tear down the docker compose ({})", msg);
            return LabActionResult.builder()
                    .labTracker(labTracker)
                    .isSuccessful(false)
                    .output("Critical failure: failed to tear down the docker compose (" + msg + ") " + teardown.getBoth())
                    .build();
        }
        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(false)
                .output("Start failure: " + msg + (logs.isBlank() ? "" : "\n" + logs))
                .build();
    }

    private String buildTeardownCommand(LabTracker labTracker) {
        String project = shellQuote(labTracker.getId());
        String file = shellQuote(trackerFilePath(labTracker));
        String label = shellQuote("label=com.docker.compose.project=" + labTracker.getId());
        return COMPOSE_FN
                + "if [ -f " + file + " ]; then dc -p " + project + " -f " + file + " down -v --remove-orphans; "
                + "else docker ps -aq --filter " + label + " | xargs -r docker rm -f"
                + " && docker volume ls -q --filter " + label + " | xargs -r docker volume rm; fi"
                // Containers may leave root-owned files behind; cleanup must not fail the teardown
                + " && { rm -rf " + shellQuote(trackerVolumeDir(labTracker)) + " " + file + " 2>/dev/null || true; }";
    }

    public Optional<List<DockerServiceInfo>> inspectServices(LabTracker labTracker, RemoteServer remoteServer) {
        RemoteCommandResponse response = remoteCommandService.handleRemoteCommand(
                compose(labTracker, "ps -qa | xargs -r docker inspect"), remoteServer);
        if (!response.isSuccess()) {
            return Optional.empty();
        }
        String output = response.getOutput().trim();
        return Optional.of(output.isEmpty() ? new ArrayList<>() : parseDockerInspectOutput(output));
    }

    public String getLogs(LabTracker labTracker, RemoteServer remoteServer, String service) {
        String args = "logs --no-color --tail 1000";
        if (!"all".equals(service)) {
            requireServiceName(service);
            args += " " + shellQuote(service);
        }
        return remoteCommandService.handleRemoteCommand(compose(labTracker, args), remoteServer).getBoth();
    }

    public RemoteCommandResponse restartServices(LabTracker labTracker, RemoteServer remoteServer, Collection<String> services) {
        services.forEach(DockerService::requireServiceName);
        String names = services.stream().map(RemoteCommandService::shellQuote).collect(Collectors.joining(" "));
        return remoteCommandService.handleRemoteCommand(compose(labTracker, "restart " + names), remoteServer);
    }

    private static void requireServiceName(String service) {
        if (service == null || !SERVICE_NAME.matcher(service).matches()) {
            throw new IllegalArgumentException("Invalid service name: " + service);
        }
    }

    private String prepareBindSourcesCommand(List<String> sources) {
        // Docker creates a directory for a missing bind source, which breaks file mounts,
        // so sources that look like files are created as empty files up front
        return sources.stream()
                .map(source -> looksLikeFile(source)
                        ? "mkdir -p \"$(dirname " + shellQuote(source) + ")\" && { [ -e " + shellQuote(source)
                        + " ] || touch " + shellQuote(source) + "; }"
                        : "mkdir -p " + shellQuote(source))
                .collect(Collectors.joining(" && "));
    }

    private static boolean looksLikeFile(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1);
        return last.lastIndexOf('.') > 0;
    }

    /**
     * Prepares a lab's compose file for one deployment: drops published ports and fixed names so
     * several teams can share a host, and moves bind mounts into the tracker's volume directory.
     */
    @SuppressWarnings("unchecked")
    public ComposeRewrite rewriteCompose(String yaml, LabTracker labTracker) {
        Map<String, Object> data = new Yaml().load(yaml);
        if (data == null) {
            throw new IllegalArgumentException("Compose file is empty");
        }
        List<String> bindSources = new ArrayList<>();

        Map<String, Object> services = getMap(data, "services");
        if (services != null) {
            services.forEach((serviceName, serviceConfig) -> {
                Map<String, Object> serviceMap = getMap(serviceConfig);
                if (serviceMap == null) {
                    return;
                }
                serviceMap.remove("ports");
                serviceMap.remove("container_name");

                if (serviceMap.get("volumes") instanceof List<?> volumes) {
                    List<Object> rewritten = new ArrayList<>();
                    for (Object volume : volumes) {
                        rewritten.add(rewriteVolume(volume, labTracker, serviceName, bindSources));
                    }
                    serviceMap.put("volumes", rewritten);
                }
            });
        }

        for (String section : List.of("volumes", "networks")) {
            Map<String, Object> definitions = getMap(data, section);
            if (definitions == null) {
                continue;
            }
            definitions.values().forEach(definition -> {
                Map<String, Object> definitionMap = getMap(definition);
                if (definitionMap != null && !Boolean.TRUE.equals(definitionMap.get("external"))) {
                    definitionMap.remove("name");
                }
            });
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new ComposeRewrite(new Yaml(options).dump(data), bindSources);
    }

    @SuppressWarnings("unchecked")
    private Object rewriteVolume(Object volume, LabTracker labTracker, String serviceName, List<String> bindSources) {
        if (volume instanceof String shortSyntax) {
            String[] parts = shortSyntax.split(":", 3);
            if (parts.length < 2 || !isHostPath(parts[0])) {
                return shortSyntax;
            }
            String source = trackerBindPath(labTracker, serviceName, parts[0]);
            bindSources.add(source);
            parts[0] = source;
            return String.join(":", parts);
        }
        if (volume instanceof Map<?, ?> longSyntax) {
            Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) longSyntax);
            Object source = map.get("source");
            boolean bind = "bind".equals(map.get("type"));
            if (source instanceof String path && (bind || isHostPath(path))) {
                String rewritten = trackerBindPath(labTracker, serviceName, path);
                bindSources.add(rewritten);
                map.put("source", rewritten);
            }
            return map;
        }
        return volume;
    }

    private static boolean isHostPath(String source) {
        return source.equals(".") || source.startsWith("./") || source.startsWith("../")
                || source.startsWith("/") || source.startsWith("~");
    }

    private String trackerBindPath(LabTracker labTracker, String serviceName, String source) {
        String relative = Arrays.stream(source.split("/"))
                .filter(segment -> !segment.isEmpty() && !segment.equals(".") && !segment.equals("..") && !segment.equals("~"))
                .collect(Collectors.joining("/"));
        String base = trackerVolumeDir(labTracker) + "/" + serviceName;
        return relative.isEmpty() ? base : base + "/" + relative;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Object obj) {
        return (obj instanceof Map) ? (Map<String, Object>) obj : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> parent, String key) {
        return (parent != null && parent.get(key) instanceof Map<?, ?>) ? (Map<String, Object>) parent.get(key) : null;
    }

    public static List<DockerServiceInfo> parseDockerInspectOutput(String jsonOutput) {
        List<DockerServiceInfo> containerInfos = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            JsonNode rootNode = objectMapper.readTree(jsonOutput);

            for (JsonNode containerNode : rootNode) {
                DockerServiceInfo info = new DockerServiceInfo();

                // Extract Name (remove leading "/")
                info.setName(containerNode.path("Name").asText("").replaceFirst("^/", ""));
                info.setState(containerNode.path("State").path("Status").asText("unknown"));

                containerNode.path("NetworkSettings").path("Networks").fields().forEachRemaining(
                        entry -> {
                            String ip = entry.getValue().path("IPAddress").asText("");
                            if (!ip.isEmpty()) {
                                info.getIpAddresses().add(ip);
                            }
                            info.getNetworks().add(entry.getKey());
                        });

                info.setService(containerNode.path("Config").path("Labels").path("com.docker.compose.service").asText(null));
                info.setImage(containerNode.path("Config").path("Image").asText(null));

                JsonNode exposedPorts = containerNode.path("Config").path("ExposedPorts");
                if (exposedPorts.isObject()) {
                    exposedPorts.fieldNames().forEachRemaining(info.getPorts()::add);
                }

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
