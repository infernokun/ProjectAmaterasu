package com.infernokun.amaterasu.services.alt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.proxmox.*;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.utils.AESUtil;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProxmoxService extends BaseService {
    private static final Duration CLONE_TIMEOUT = Duration.ofMinutes(20);
    private static final Duration TASK_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration STATUS_TIMEOUT = Duration.ofMinutes(3);
    private static final Pattern BRIDGE = Pattern.compile("bridge=([^,]+)");
    private static final Pattern LAB_BRIDGE = Pattern.compile("abr(\\d+)");
    // Bridge numbers are picked from what exists on the node, so two deploys must not pick at once
    private static final Object BRIDGE_LOCK = new Object();

    private final RestTemplate restTemplate;
    private final AESUtil aesUtil;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ProxmoxService(AESUtil aesUtil) {
        this(aesUtil, createRestTemplate());
    }

    ProxmoxService(AESUtil aesUtil, RestTemplate restTemplate) {
        this.aesUtil = aesUtil;
        this.restTemplate = restTemplate;
    }

    private String api(RemoteServer remoteServer, String path) {
        return String.format("https://%s:8006/api2/json%s", remoteServer.getIpAddress(), path);
    }

    private String nodeApi(RemoteServer remoteServer, String path) {
        return api(remoteServer, "/nodes/" + remoteServer.getNodeName() + path);
    }

    public boolean proxmoxHealthCheck(RemoteServer remoteServer) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    api(remoteServer, "/version"), HttpMethod.GET, entity, new ParameterizedTypeReference<>() { });
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            return false;
        }
    }

    public List<ProxmoxVM> getVMs(RemoteServer remoteServer) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        ResponseEntity<ProxmoxResponse> response = restTemplate.exchange(
                nodeApi(remoteServer, "/qemu"), HttpMethod.GET, entity, ProxmoxResponse.class);

        if (response.getBody() != null && response.getBody().getData() != null) {
            return response.getBody().getData();
        }
        return List.of();
    }

    public List<ProxmoxVM> getVMTemplates(RemoteServer remoteServer) {
        return getVMs(remoteServer).stream()
                .filter(ProxmoxVM::isTemplate)
                .collect(Collectors.toList());
    }

    /** Returns the current state of the given VMs, skipping any that no longer exist. */
    public List<ProxmoxVM> getVMsByIds(RemoteServer remoteServer, List<Integer> vmIds) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        List<ProxmoxVM> found = new ArrayList<>();
        for (Integer vmid : vmIds) {
            try {
                ResponseEntity<Map<String, ProxmoxVM>> response = restTemplate.exchange(
                        nodeApi(remoteServer, "/qemu/" + vmid + "/status/current"), HttpMethod.GET, entity,
                        new ParameterizedTypeReference<>() { });
                if (response.getBody() != null && response.getBody().get("data") != null) {
                    found.add(response.getBody().get("data"));
                }
            } catch (RestClientException e) {
                LOGGER.warn("Could not read VM {} on {}: {}", vmid, remoteServer.getIpAddress(), e.getMessage());
            }
        }
        return found;
    }

    public ProxmoxVMConfig getVmConfigById(RemoteServer remoteServer, Integer vmid) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        ResponseEntity<Map<String, ProxmoxVMConfig>> response = restTemplate.exchange(
                nodeApi(remoteServer, "/qemu/" + vmid + "/config"), HttpMethod.GET, entity,
                new ParameterizedTypeReference<>() { });

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ResourceNotFoundException("Proxmox id does not exist");
        }
        return response.getBody().get("data");
    }

    public List<ProxmoxNetwork> getNodeNetworks(RemoteServer remoteServer) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                nodeApi(remoteServer, "/network"), HttpMethod.GET, entity, new ParameterizedTypeReference<>() { });

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ResourceNotFoundException("Could not read node networks");
        }
        return objectMapper.convertValue(
                response.getBody().get("data"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ProxmoxNetwork.class));
    }

    /**
     * Asks the node for a VNC ticket for one VM. The ticket opens the node's vncwebsocket once,
     * for the same API token, and doubles as the VNC password.
     */
    public ProxmoxVncTicket createVncProxy(RemoteServer remoteServer, int vmid) {
        requireProxmox(remoteServer);
        HttpHeaders headers = createHeaders(remoteServer);
        String body = "{\"websocket\":1}";
        headers.setContentLength(body.getBytes(StandardCharsets.UTF_8).length);
        ResponseEntity<Map<String, Map<String, Object>>> response = restTemplate.exchange(
                nodeApi(remoteServer, "/qemu/" + vmid + "/vncproxy"), HttpMethod.POST, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() { });
        Map<String, Object> data = response.getBody() != null ? response.getBody().get("data") : null;
        if (data == null || data.get("ticket") == null || data.get("port") == null) {
            throw new IllegalStateException("Proxmox did not return a console ticket");
        }
        return new ProxmoxVncTicket(String.valueOf(data.get("port")), String.valueOf(data.get("ticket")),
                data.get("user") != null ? String.valueOf(data.get("user")) : null);
    }

    /** The node's websocket for a ticket from {@link #createVncProxy}. */
    public java.net.URI vncWebSocketUri(RemoteServer remoteServer, int vmid, ProxmoxVncTicket ticket) {
        return java.net.URI.create(String.format("wss://%s:8006/api2/json/nodes/%s/qemu/%d/vncwebsocket?port=%s&vncticket=%s",
                remoteServer.getIpAddress(), remoteServer.getNodeName(), vmid,
                java.net.URLEncoder.encode(ticket.port(), StandardCharsets.UTF_8),
                java.net.URLEncoder.encode(ticket.ticket(), StandardCharsets.UTF_8)));
    }

    /** The Authorization header value for the node's API token. */
    public String authorization(RemoteServer remoteServer) {
        return createHeaders(remoteServer).getFirst(HttpHeaders.AUTHORIZATION);
    }

    /**
     * Clones the lab's templates onto private bridges and starts them. Clones and bridges are
     * recorded on the tracker as they are created, so a failed deploy can still be torn down.
     */
    public LabActionResult deployProxmoxLab(LabTracker labTracker, RemoteServer remoteServer) {
        requireProxmox(remoteServer);
        List<String> responses = new ArrayList<>();
        int failures = 0;

        try {
            List<Integer> templateIds = labTracker.getLabStarted().getVmIds();
            Map<Integer, ProxmoxVM> templates = getVMTemplates(remoteServer).stream()
                    .collect(Collectors.toMap(ProxmoxVM::getVmid, vm -> vm, (a, b) -> a));
            List<Integer> missing = templateIds.stream().filter(id -> !templates.containsKey(id)).toList();
            if (missing.isEmpty() && templateIds.isEmpty()) {
                return failed(labTracker, "Lab has no VM templates");
            }
            if (!missing.isEmpty()) {
                return failed(labTracker, "Templates not found on " + remoteServer.getName() + ": " + missing);
            }

            Map<Integer, ProxmoxVMConfig> templateConfigs = new LinkedHashMap<>();
            for (Integer id : templateIds) {
                templateConfigs.put(id, getVmConfigById(remoteServer, id));
            }

            Set<String> sourceBridges = new LinkedHashSet<>();
            templateConfigs.values().forEach(config -> config.getNetworks().values().forEach(net -> {
                Matcher matcher = BRIDGE.matcher(net);
                if (matcher.find()) {
                    sourceBridges.add(matcher.group(1));
                }
            }));

            Map<String, String> bridgeMap = createLabBridges(remoteServer, sourceBridges, labTracker, responses);
            if (bridgeMap.size() != sourceBridges.size()) {
                return failed(labTracker, "Failed to create lab networks\n" + String.join("\n", responses));
            }

            List<ProxmoxVM> clones = new ArrayList<>();
            labTracker.setVms(clones);

            for (Integer templateId : templateIds) {
                ProxmoxVM template = templates.get(templateId);
                int newVmid = nextVmid(remoteServer);
                String name = sanitizeName(labTracker.getLabOwner().getName() + "-"
                        + labTracker.getLabStarted().getName() + "-" + template.getName());

                ResponseEntity<ProxmoxActionResponse> clone = sendProxmoxRequest(
                        nodeApi(remoteServer, "/qemu/" + templateId + "/clone"),
                        Map.of("newid", newVmid, "name", name,
                                "description", labTracker.getLabStarted().getName() + " for " + labTracker.getLabOwner().getName()),
                        HttpMethod.POST, remoteServer);
                responses.add("Clone " + template.getName() + " -> " + newVmid + ": " + describe(clone));
                if (!clone.getStatusCode().is2xxSuccessful()) {
                    failures++;
                    continue;
                }
                clones.add(new ProxmoxVM(newVmid, template));

                if (!waitForTask(remoteServer, upid(clone), CLONE_TIMEOUT)) {
                    responses.add("Clone of " + template.getName() + " did not finish");
                    failures++;
                    continue;
                }

                Map<String, Object> config = new HashMap<>();
                templateConfigs.get(templateId).getNetworks().forEach((key, value) -> {
                    Matcher matcher = BRIDGE.matcher(value);
                    if (matcher.find()) {
                        config.put(key, matcher.replaceFirst("bridge=" + bridgeMap.get(matcher.group(1))));
                    }
                });
                config.put("agent", "1");
                ResponseEntity<ProxmoxActionResponse> update = sendProxmoxRequest(
                        nodeApi(remoteServer, "/qemu/" + newVmid + "/config"), config, HttpMethod.PUT, remoteServer);
                responses.add("Network config " + newVmid + ": " + describe(update));
                if (!update.getStatusCode().is2xxSuccessful()) {
                    failures++;
                }
            }

            boolean running = false;
            if (failures == 0) {
                failures += startVMs(clones, remoteServer, responses);
                running = failures == 0 && waitForVMsToReachStatus(clones, remoteServer, "running");
            }
            refreshVMs(labTracker, remoteServer);

            return LabActionResult.builder()
                    .labTracker(labTracker)
                    .isSuccessful(failures == 0 && running)
                    .output(String.join("\n", responses))
                    .build();
        } catch (Exception e) {
            LOGGER.error("Deploy of lab tracker {} failed", labTracker.getId(), e);
            responses.add("Deploy failed: " + e.getMessage());
            return failed(labTracker, String.join("\n", responses));
        }
    }

    /** Starts the lab's existing VMs, for example after it was paused. */
    public LabActionResult resumeProxmoxLab(LabTracker labTracker, RemoteServer remoteServer) {
        requireProxmox(remoteServer);
        List<String> responses = new ArrayList<>();
        try {
            List<ProxmoxVM> current = getVMsByIds(remoteServer, vmIds(labTracker));
            if (current.size() != labTracker.getVms().size()) {
                return failed(labTracker, "Some lab VMs no longer exist; stop the lab to redeploy it");
            }
            List<ProxmoxVM> notRunning = current.stream().filter(vm -> !"running".equals(vm.getStatus())).toList();
            int failures = startVMs(notRunning, remoteServer, responses);
            boolean running = failures == 0 && waitForVMsToReachStatus(labTracker.getVms(), remoteServer, "running");
            refreshVMs(labTracker, remoteServer);
            return LabActionResult.builder()
                    .labTracker(labTracker)
                    .isSuccessful(running)
                    .output(String.join("\n", responses))
                    .build();
        } catch (Exception e) {
            return failed(labTracker, "Resume failed: " + e.getMessage());
        }
    }

    /** Shuts the VMs down gracefully and keeps their disks, so progress survives. */
    public LabActionResult pauseProxmoxLab(LabTracker labTracker, RemoteServer remoteServer) {
        requireProxmox(remoteServer);
        List<String> responses = new ArrayList<>();
        int failures = 0;
        try {
            for (ProxmoxVM vm : getVMsByIds(remoteServer, vmIds(labTracker))) {
                if (!"running".equals(vm.getStatus())) {
                    continue;
                }
                ResponseEntity<ProxmoxActionResponse> shutdown = sendProxmoxRequest(
                        nodeApi(remoteServer, "/qemu/" + vm.getVmid() + "/status/shutdown"),
                        Map.of("timeout", 120, "forceStop", 1), HttpMethod.POST, remoteServer);
                responses.add("Shutdown " + vm.getVmid() + ": " + describe(shutdown));
                if (!shutdown.getStatusCode().is2xxSuccessful() || !waitForTask(remoteServer, upid(shutdown), TASK_TIMEOUT)) {
                    failures++;
                }
            }
            boolean stopped = failures == 0 && waitForVMsToReachStatus(labTracker.getVms(), remoteServer, "stopped");
            refreshVMs(labTracker, remoteServer);
            return LabActionResult.builder()
                    .labTracker(labTracker)
                    .isSuccessful(stopped)
                    .output(String.join("\n", responses))
                    .build();
        } catch (Exception e) {
            return failed(labTracker, "Pause failed: " + e.getMessage());
        }
    }

    /** Destroys the lab's VMs and private bridges. The next start deploys fresh clones. */
    public LabActionResult teardownProxmoxLab(LabTracker labTracker, RemoteServer remoteServer) {
        requireProxmox(remoteServer);
        List<String> responses = new ArrayList<>();
        int failures = 0;
        try {
            for (ProxmoxVM vm : getVMsByIds(remoteServer, vmIds(labTracker))) {
                if (!"stopped".equals(vm.getStatus())) {
                    ResponseEntity<ProxmoxActionResponse> stop = sendProxmoxRequest(
                            nodeApi(remoteServer, "/qemu/" + vm.getVmid() + "/status/stop"), Map.of(),
                            HttpMethod.POST, remoteServer);
                    responses.add("Stop " + vm.getVmid() + ": " + describe(stop));
                    if (!stop.getStatusCode().is2xxSuccessful() || !waitForTask(remoteServer, upid(stop), TASK_TIMEOUT)) {
                        failures++;
                        continue;
                    }
                }
                ResponseEntity<ProxmoxActionResponse> destroy = sendProxmoxRequest(
                        nodeApi(remoteServer, "/qemu/" + vm.getVmid() + "?purge=1&destroy-unreferenced-disks=1"),
                        null, HttpMethod.DELETE, remoteServer);
                responses.add("Destroy " + vm.getVmid() + ": " + describe(destroy));
                if (!destroy.getStatusCode().is2xxSuccessful() || !waitForTask(remoteServer, upid(destroy), TASK_TIMEOUT)) {
                    failures++;
                }
            }

            if (failures == 0) {
                labTracker.setVms(new ArrayList<>());
                if (removeLabBridges(remoteServer, labTracker, responses)) {
                    labTracker.setBridges(new ArrayList<>());
                } else {
                    failures++;
                }
            } else {
                refreshVMs(labTracker, remoteServer);
            }

            return LabActionResult.builder()
                    .labTracker(labTracker)
                    .isSuccessful(failures == 0)
                    .output(String.join("\n", responses))
                    .build();
        } catch (Exception e) {
            return failed(labTracker, "Teardown failed: " + e.getMessage());
        }
    }

    private Map<String, String> createLabBridges(RemoteServer remoteServer, Set<String> sourceBridges,
                                                 LabTracker labTracker, List<String> responses) {
        Map<String, String> bridgeMap = new LinkedHashMap<>();
        if (sourceBridges.isEmpty()) {
            return bridgeMap;
        }
        synchronized (BRIDGE_LOCK) {
            int next = getNodeNetworks(remoteServer).stream()
                    .map(ProxmoxNetwork::getIface)
                    .filter(Objects::nonNull)
                    .map(LAB_BRIDGE::matcher)
                    .filter(Matcher::matches)
                    .mapToInt(m -> Integer.parseInt(m.group(1)))
                    .max()
                    .orElse(-1) + 1;

            List<String> created = new ArrayList<>(labTracker.getBridges());
            for (String source : sourceBridges) {
                String bridge = "abr" + next++;
                // No address and no ports: an isolated segment that only this lab's VMs join
                Map<String, Object> payload = Map.of("iface", bridge, "type", "bridge", "autostart", 1,
                        "comments", "amaterasu lab " + labTracker.getId());
                ResponseEntity<ProxmoxActionResponse> response = sendProxmoxRequest(
                        nodeApi(remoteServer, "/network"), payload, HttpMethod.POST, remoteServer);
                responses.add("Create bridge " + bridge + " for " + source + ": " + describe(response));
                if (!response.getStatusCode().is2xxSuccessful()) {
                    break;
                }
                bridgeMap.put(source, bridge);
                created.add(bridge);
            }
            labTracker.setBridges(created);

            if (!applyNetworkChanges(remoteServer, responses)) {
                bridgeMap.clear();
            }
        }
        return bridgeMap;
    }

    private boolean removeLabBridges(RemoteServer remoteServer, LabTracker labTracker, List<String> responses) {
        List<String> bridges = labTracker.getBridges().stream()
                .filter(bridge -> LAB_BRIDGE.matcher(bridge).matches())
                .toList();
        if (bridges.isEmpty()) {
            return true;
        }
        synchronized (BRIDGE_LOCK) {
            Set<String> existing = getNodeNetworks(remoteServer).stream()
                    .map(ProxmoxNetwork::getIface)
                    .collect(Collectors.toSet());
            boolean ok = true;
            for (String bridge : bridges) {
                if (!existing.contains(bridge)) {
                    continue;
                }
                ResponseEntity<ProxmoxActionResponse> response = sendProxmoxRequest(
                        nodeApi(remoteServer, "/network/" + bridge), null, HttpMethod.DELETE, remoteServer);
                responses.add("Remove bridge " + bridge + ": " + describe(response));
                ok &= response.getStatusCode().is2xxSuccessful();
            }
            return applyNetworkChanges(remoteServer, responses) && ok;
        }
    }

    private boolean applyNetworkChanges(RemoteServer remoteServer, List<String> responses) {
        ResponseEntity<ProxmoxActionResponse> response = sendProxmoxRequest(
                nodeApi(remoteServer, "/network"), Map.of(), HttpMethod.PUT, remoteServer);
        responses.add("Apply network changes: " + describe(response));
        return response.getStatusCode().is2xxSuccessful() && waitForTask(remoteServer, upid(response), TASK_TIMEOUT);
    }

    private int startVMs(List<ProxmoxVM> vms, RemoteServer remoteServer, List<String> responses) {
        int failures = 0;
        for (ProxmoxVM vm : vms) {
            ResponseEntity<ProxmoxActionResponse> start = sendProxmoxRequest(
                    nodeApi(remoteServer, "/qemu/" + vm.getVmid() + "/status/start"), Map.of(),
                    HttpMethod.POST, remoteServer);
            responses.add("Start " + vm.getVmid() + ": " + describe(start));
            if (!start.getStatusCode().is2xxSuccessful() || !waitForTask(remoteServer, upid(start), TASK_TIMEOUT)) {
                failures++;
            }
        }
        return failures;
    }

    private int nextVmid(RemoteServer remoteServer) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                api(remoteServer, "/cluster/nextid"), HttpMethod.GET, entity, new ParameterizedTypeReference<>() { });
        if (response.getBody() == null || response.getBody().get("data") == null) {
            throw new IllegalStateException("Proxmox did not return a free VM id");
        }
        return Integer.parseInt(response.getBody().get("data").toString());
    }

    /** Waits for an asynchronous Proxmox task. Calls that return no task id finished synchronously. */
    private boolean waitForTask(RemoteServer remoteServer, String upid, Duration timeout) {
        if (upid == null || !upid.startsWith("UPID:")) {
            return true;
        }
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                ResponseEntity<Map<String, Map<String, Object>>> response = restTemplate.exchange(
                        nodeApi(remoteServer, "/tasks/" + upid + "/status"), HttpMethod.GET, entity,
                        new ParameterizedTypeReference<>() { });
                Map<String, Object> data = response.getBody() != null ? response.getBody().get("data") : null;
                if (data != null && "stopped".equals(data.get("status"))) {
                    boolean ok = "OK".equals(data.get("exitstatus"));
                    if (!ok) {
                        LOGGER.warn("Proxmox task {} ended with {}", upid, data.get("exitstatus"));
                    }
                    return ok;
                }
            } catch (RestClientException e) {
                LOGGER.warn("Could not read Proxmox task {}: {}", upid, e.getMessage());
            }
            if (!sleep(2000)) {
                return false;
            }
        }
        LOGGER.warn("Timed out waiting for Proxmox task {}", upid);
        return false;
    }

    private boolean waitForVMsToReachStatus(List<ProxmoxVM> vms, RemoteServer remoteServer, String targetStatus) {
        Set<Integer> ids = vms.stream().map(ProxmoxVM::getVmid).collect(Collectors.toSet());
        long deadline = System.nanoTime() + STATUS_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            long matching = getVMs(remoteServer).stream()
                    .filter(vm -> ids.contains(vm.getVmid()))
                    .filter(vm -> targetStatus.equalsIgnoreCase(vm.getStatus()))
                    .count();
            if (matching == ids.size()) {
                return true;
            }
            if (!sleep(2000)) {
                return false;
            }
        }
        return false;
    }

    private void refreshVMs(LabTracker labTracker, RemoteServer remoteServer) {
        try {
            List<ProxmoxVM> current = getVMsByIds(remoteServer, vmIds(labTracker));
            if (current.size() == labTracker.getVms().size()) {
                labTracker.setVms(new ArrayList<>(current));
            }
        } catch (Exception e) {
            LOGGER.warn("Could not refresh VMs for lab tracker {}: {}", labTracker.getId(), e.getMessage());
        }
    }

    private static List<Integer> vmIds(LabTracker labTracker) {
        return labTracker.getVms().stream().map(ProxmoxVM::getVmid).toList();
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void requireProxmox(RemoteServer remoteServer) {
        if (remoteServer.getServerType() != ServerType.PROXMOX) {
            throw new IllegalArgumentException("Invalid server type");
        }
    }

    private static LabActionResult failed(LabTracker labTracker, String output) {
        return LabActionResult.builder().labTracker(labTracker).isSuccessful(false).output(output).build();
    }

    private static String upid(ResponseEntity<ProxmoxActionResponse> response) {
        return response.getBody() != null ? response.getBody().getData() : null;
    }

    private static String describe(ResponseEntity<ProxmoxActionResponse> response) {
        String body = response.getBody() != null ? response.getBody().getData() : null;
        return response.getStatusCode().value() + (body != null && !body.isBlank() ? " " + body : "");
    }

    private ResponseEntity<ProxmoxActionResponse> sendProxmoxRequest(String url, Map<String, Object> payload,
                                                                     HttpMethod httpMethod, RemoteServer remoteServer) {
        try {
            HttpHeaders headers = createHeaders(remoteServer);
            HttpEntity<String> entity;
            if (payload != null && (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT)) {
                String jsonPayload = objectMapper.writeValueAsString(payload);
                headers.setContentLength(jsonPayload.getBytes(StandardCharsets.UTF_8).length);
                entity = new HttpEntity<>(jsonPayload, headers);
            } else {
                entity = new HttpEntity<>(headers);
            }
            return restTemplate.exchange(url, httpMethod, entity, ProxmoxActionResponse.class);
        } catch (HttpStatusCodeException e) {
            LOGGER.error("Proxmox {} {} failed: {} {}", httpMethod, url, e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(new ProxmoxActionResponse(e.getResponseBodyAsString()));
        } catch (Exception e) {
            LOGGER.error("Error sending request to Proxmox: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ProxmoxActionResponse(e.getMessage()));
        }
    }

    private HttpHeaders createHeaders(RemoteServer remoteServer) {
        HttpHeaders headers = new HttpHeaders();
        String apiToken = remoteServer.getId() != null ? aesUtil.decrypt(remoteServer.getApiToken()) : remoteServer.getApiToken();
        headers.set("Authorization", "PVEAPIToken " + apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    public String sanitizeName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        return sanitized.length() > 63 ? sanitized.substring(0, 63).replaceAll("-+$", "") : sanitized;
    }

    public static RestTemplate createRestTemplate() {
        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial((X509Certificate[] chain, String authType) -> true)
                    .build();

            TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(
                    sslContext,
                    HostnameVerificationPolicy.CLIENT,
                    NoopHostnameVerifier.INSTANCE);

            PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setTlsSocketStrategy(tlsSocketStrategy)
                    .build();

            connectionManager.setDefaultConnectionConfig(
                    ConnectionConfig.custom()
                            .setConnectTimeout(Timeout.ofSeconds(10))
                            .setSocketTimeout(Timeout.ofSeconds(30))
                            .build());

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();

            return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL-ignoring RestTemplate", e);
        }
    }
}
