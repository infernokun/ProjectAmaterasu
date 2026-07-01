package com.infernokun.amaterasu.services.alt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.*;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.proxmox.*;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.entity.lab.LabTrackerService;
import com.infernokun.amaterasu.utils.AESUtil;
import com.infernokun.amaterasu.utils.IpUtils;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.*;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProxmoxService extends BaseService {
    private final RestTemplate restTemplate;
    private final AESUtil aesUtil;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final LabTrackerService labTrackerService;

    // Constructor now uses the custom RestTemplate
    public ProxmoxService(AESUtil aesUtil, LabTrackerService labTrackerService) {
        this.aesUtil = aesUtil;
        this.labTrackerService = labTrackerService;
        this.restTemplate = createRestTemplate();
    }

    public boolean proxmoxHealthCheck(RemoteServer remoteServer) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        String url = String.format("https://%s:8006/api2/json/version", remoteServer.getIpAddress());

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() { }
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return false; // Invalid API token or server error
        } catch (ResourceAccessException e) {
            return false; // Connection issues (e.g., wrong IP, Proxmox not reachable)
        }
    }


    public List<ProxmoxVM> getVMs(RemoteServer remoteServer) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));

        String PROXMOX_API_URL_LIST = String.format("https://%s:8006/api2/json/nodes/%s/qemu",
                remoteServer.getIpAddress(), remoteServer.getNodeName());

        ResponseEntity<ProxmoxResponse> response = restTemplate.exchange(
                PROXMOX_API_URL_LIST, HttpMethod.GET, entity, ProxmoxResponse.class
        );

        if (response.getBody() != null && response.getBody().getData() != null) {
            List<ProxmoxVM> vms = response.getBody().getData();
            vms.forEach(vm -> {
                // Ensure template is always explicitly set
                vm.setTemplate(vm.isTemplate() ? 1 : 0);
            });
            return vms;
        }
        return List.of();
    }

    public List<ProxmoxVM> getVMTemplates(RemoteServer remoteServer) {
        return getVMs(remoteServer).stream()
                .filter(ProxmoxVM::isTemplate)
                .collect(Collectors.toList());
    }

    public ProxmoxVM getVmById(RemoteServer remoteServer, Integer vmid) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        String url = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%d/status/current",
                remoteServer.getIpAddress(), remoteServer.getNodeName(), vmid);

        ResponseEntity<Map<String, ProxmoxVM>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() { }
        );
        if (response.getBody() == null) {
            throw new ResourceNotFoundException("Proxmox id does not exist");
        }

        return response.getBody().get("data");
    }

    public List<ProxmoxVM> getVMsByIds(RemoteServer remoteServer, List<Integer> vmIds) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));

        List<ProxmoxVM> filteredVMs = new ArrayList<>();
        for (Integer vmid : vmIds) {
            String url = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%d/status/current",
                    remoteServer.getIpAddress(), remoteServer.getNodeName(), vmid);

            ResponseEntity<Map<String, ProxmoxVM>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() { }
            );

            // Guard against a null "data" payload (e.g. the VM was deleted out-of-band in the
            // Proxmox UI). Adding a null here would later NPE when reading vm.getVmid().
            if (response.getBody() != null && response.getBody().get("data") != null) {
                filteredVMs.add(response.getBody().get("data"));
            }
        }
        return filteredVMs;
    }

    public ProxmoxVMConfig getVmConfigById(RemoteServer remoteServer, Integer vmid) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        String url = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%d/config",
                remoteServer.getIpAddress(), remoteServer.getNodeName(), vmid);

        ResponseEntity<Map<String, ProxmoxVMConfig>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() { }
        );
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ResourceNotFoundException("Proxmox id does not exist");
        }

        return response.getBody().get("data");
    }

    public List<ProxmoxNetwork> getNodeNetworks(RemoteServer remoteServer) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        String url = String.format("https://%s:8006/api2/json/nodes/%s/network",
                remoteServer.getIpAddress(), remoteServer.getNodeName());

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ResourceNotFoundException("Proxmox id does not exist");
        }

        // Convert "data" from Object to List<ProxmoxNetwork>
        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.convertValue(
                response.getBody().get("data"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ProxmoxNetwork.class)
        );
    }

    public ProxmoxActionResponse createNodeNetworkBridge(RemoteServer remoteServer, Map<String, Object> payload, String bridgeName) {
        String createBridgeUrl = String.format("https://%s:8006/api2/json/nodes/%s/network",
                remoteServer.getIpAddress(), remoteServer.getNodeName());

        ResponseEntity<ProxmoxActionResponse> createResponse = sendProxmoxRequest(
                createBridgeUrl, payload, HttpMethod.POST, remoteServer);

        if (createResponse.getStatusCode().is2xxSuccessful()) {
            LOGGER.info("Created bridge {} on server {}", bridgeName, remoteServer.getIpAddress());
        } else {
            LOGGER.warn("Failed to create bridge {}: {}", bridgeName, createResponse.getStatusCode());

            // Log the detailed error response for debugging
            if (createResponse.getBody() != null) {
                LOGGER.debug("Bridge creation response: {}", createResponse.getBody().getData());
            }
        }
        return createResponse.getBody();
    }

    public ProxmoxActionResponse createVmNetworkBridge(RemoteServer remoteServer, Integer vmId, Map<String, Object> payload) {
        String createBridgeUrl = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%s/config",
                remoteServer.getIpAddress(), remoteServer.getNodeName(), vmId);

        ResponseEntity<ProxmoxActionResponse> createResponse = sendProxmoxRequest(
                createBridgeUrl, payload, HttpMethod.POST, remoteServer);

        if (createResponse.getStatusCode().is2xxSuccessful()) {
            LOGGER.info("Created bridge on vm ");
        } else {
            LOGGER.warn("Failed to create bridge: {}", createResponse.getStatusCode());

            // Log the detailed error response for debugging
            if (createResponse.getBody() != null) {
                LOGGER.debug("Bridge creation response...: {}", createResponse.getBody().getData());
            }
        }
        return createResponse.getBody();
    }

    public ProxmoxActionResponse applyNetworkChanges(RemoteServer remoteServer) {
        String applyUrl = String.format("https://%s:8006/api2/json/nodes/%s/network",
                remoteServer.getIpAddress(), remoteServer.getNodeName());

        Map<String, Object> payload = new HashMap<>();
        payload.put("node", remoteServer.getNodeName()); // Apply network changes without rebooting

        ResponseEntity<ProxmoxActionResponse> response = sendProxmoxRequest(
                applyUrl, payload, HttpMethod.PUT, remoteServer);

        if (response.getStatusCode().is2xxSuccessful()) {
            LOGGER.info("Successfully applied network changes on {}", remoteServer.getIpAddress());
        } else {
            LOGGER.warn("Failed to apply network changes: {}", response.getStatusCode());
        }
        return response.getBody();
    }

    public ProxmoxActionResponse deleteVmNetworkInterface(RemoteServer remoteServer, Integer vmId, String netDevice) {
        // URL for the VM config endpoint
        String url = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%d/config",
                remoteServer.getIpAddress(), remoteServer.getNodeName(), vmId);

        // Create payload with delete parameter
        Map<String, Object> payload = new HashMap<>();
        payload.put("delete", netDevice);

        // Send the request
        ResponseEntity<ProxmoxActionResponse> response = sendProxmoxRequest(
                url, payload, HttpMethod.PUT, remoteServer);

        if (response.getStatusCode().is2xxSuccessful()) {
            LOGGER.info("Successfully deleted network interface {} from VM {} on {}",
                    netDevice, vmId, remoteServer.getIpAddress());
        } else {
            LOGGER.warn("Failed to delete network interface {} from VM {}: {}",
                    netDevice, vmId, response.getStatusCode());

            // Log the detailed error response for debugging
            if (response.getBody() != null) {
                LOGGER.debug("Network interface deletion response: {}", response.getBody().getData());
            }
        }

        return response.getBody();
    }

    private ProxmoxActionResponse updateVmNetworkConfig(RemoteServer remoteServer, Integer vmId, Map<String, String> updatedNetworks) {
        String updateUrl = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%d/config",
                remoteServer.getIpAddress(), remoteServer.getNodeName(), vmId);

        Map<String, Object> payload = new HashMap<>(updatedNetworks);

        ResponseEntity<ProxmoxActionResponse> response = sendProxmoxRequest(
                updateUrl, payload, HttpMethod.POST, remoteServer);

        if (response.getStatusCode().is2xxSuccessful()) {
            LOGGER.info("Successfully updated VM {} network config on {}", vmId, remoteServer.getIpAddress());
        } else {
            LOGGER.warn("Failed to update VM {} network config: {}", vmId, response.getStatusCode());

            // Log the detailed error response for debugging
            if (response.getBody() != null) {
                LOGGER.debug("Network update response: {}", response.getBody().getData());
            }
        }
        return response.getBody();
    }

    public boolean deleteAllVmNetworkInterfaces(RemoteServer remoteServer, Integer vmId) {
        try {
            // Get current VM configuration to find network devices
            ProxmoxVMConfig vmConfig = getVmConfigById(remoteServer, vmId);

            if (vmConfig == null || vmConfig.getNetworks() == null || vmConfig.getNetworks().isEmpty()) {
                LOGGER.info("No network interfaces found for VM {} on {}",
                        vmId, remoteServer.getIpAddress());
                return true;
            }

            boolean allSuccessful = true;

            // Delete each network interface
            for (String netDevice : vmConfig.getNetworks().keySet()) {
                ProxmoxActionResponse response = deleteVmNetworkInterface(remoteServer, vmId, netDevice);

                if (response == null) {
                    allSuccessful = false;
                    LOGGER.error("Failed to delete network interface {} from VM {}",
                            netDevice, vmId);
                }
            }

            return allSuccessful;
        } catch (Exception e) {
            LOGGER.error("Error deleting network interfaces from VM {}: {}",
                    vmId, e.getMessage(), e);
            return false;
        }
    }

    @Transactional
    public LabActionResult startAndCloneProxmoxLab(LabTracker labTracker, RemoteServer remoteServer) {
        if (remoteServer.getServerType() != ServerType.PROXMOX) {
            throw new RuntimeException("Invalid server type");
        }
        LOGGER.info("Stage: START AND CLONE");

        AtomicInteger failures = new AtomicInteger();
        List<String> responses = new ArrayList<>();

        // Index the deploy-time network selections by the template VM they apply to.
        Map<Integer, LabNetworkConfig> networkByTemplate = new HashMap<>();
        if (labTracker.getNetworkConfig() != null) {
            labTracker.getNetworkConfig().stream()
                    .filter(cfg -> cfg.getVmid() != null)
                    .forEach(cfg -> networkByTemplate.put(cfg.getVmid(), cfg));
        }

        List<ProxmoxVM> vms = getVMTemplates(remoteServer).stream()
                .filter(v -> labTracker.getLabStarted().getVmIds().contains(v.getVmid()))
                .toList();

        // Remember which template each clone came from so we can look up its bridge/IP.
        List<ProxmoxVM> clonedVMs = new ArrayList<>();
        Map<Integer, LabNetworkConfig> networkByClone = new HashMap<>();

        vms.forEach(vm -> {
            LOGGER.info("Stage: CLONE START {}", vm.getName());
            String newVmName = sanitizeName(labTracker.getLabOwner().getName() +
                    "-" + labTracker.getLabStarted().getName() + "-" + vm.getName());

            try {
                int newVmid = pickFreeVmid(remoteServer);

                // Clone VM Request
                String cloneUrl = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%s/clone",
                        remoteServer.getIpAddress(), remoteServer.getNodeName(), vm.getVmid());

                Map<String, Object> payloadClone = Map.of(
                        "name", newVmName,
                        "newid", newVmid,
                        "node", remoteServer.getNodeName(),
                        "description", labTracker.getLabStarted().getName() + " created for " + labTracker.getLabOwner().getName()
                );

                ResponseEntity<ProxmoxActionResponse> responseClone = sendProxmoxRequest(
                        cloneUrl, payloadClone, HttpMethod.POST, remoteServer);

                responses.add("Clone response " + vm.getName() + ": " + responseClone.getStatusCode());

                if (!responseClone.getStatusCode().is2xxSuccessful()) {
                    failures.incrementAndGet();
                    return;
                }

                LabNetworkConfig cfg = networkByTemplate.get(vm.getVmid());
                if (cfg != null) {
                    networkByClone.put(newVmid, cfg);
                }

                clonedVMs.add(new ProxmoxVM(newVmid, vm));
            } catch (Exception e) {
                LOGGER.error("Error processing VM {} ({}): {}", vm.getName(), newVmName, e.getMessage());
                failures.incrementAndGet();
            }
        });

        // Attach each clone to the operator-selected bridge and assign its IP, rather than
        // shunting every VM onto an isolated, DHCP-less bridge (the cause of clones coming up
        // with no working network). With no selection we leave the template's networking intact.
        clonedVMs.forEach(clonedVM ->
                applyVmNetworkConfig(clonedVM.getVmid(), remoteServer, networkByClone.get(clonedVM.getVmid())));

        // Now start the cloned VMs
        clonedVMs.forEach(vm -> startProxmoxVM(vm, remoteServer, responses, failures));

        boolean allVMsStarted = waitForVMsToReachStatus(clonedVMs, remoteServer, "running");

        List<ProxmoxVM> filteredVMs =
                getVMs(remoteServer).stream()
                        .filter(vm -> clonedVMs.stream().anyMatch(v -> v.getVmid() == vm.getVmid()))
                        .toList();

        labTracker.setVms(filteredVMs);
        LOGGER.info("Stage: COMPLETE AND RETURN");

        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(failures.get() == 0 && allVMsStarted)
                .output(String.join("\n", responses + "\n" + filteredVMs))
                .build();
    }

    /**
     * Picks a VM id that is not already in use on the node. The previous approach used an
     * unbounded random id with no collision check; here we retry a bounded number of times
     * against the live id set before giving up.
     */
    private int pickFreeVmid(RemoteServer remoteServer) {
        Set<Integer> used = getVMs(remoteServer).stream()
                .map(ProxmoxVM::getVmid)
                .collect(Collectors.toSet());
        for (int attempt = 0; attempt < 50; attempt++) {
            int candidate = 100 + ThreadLocalRandom.current().nextInt(999999900);
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate a free VM id on " + remoteServer.getIpAddress());
    }

    /**
     * Applies the operator's bridge + IP choice to a freshly-cloned VM. Bridge remapping and
     * the guest-agent flag are sent first; the cloud-init {@code ipconfig0} is sent as a
     * separate request so a template without a cloud-init drive fails only that step instead
     * of dropping the working bridge change. A no-op when {@code cfg} is null (keep template net).
     */
    private void applyVmNetworkConfig(Integer vmId, RemoteServer remoteServer, LabNetworkConfig cfg) {
        ProxmoxVMConfig vmConfig = pollForVmConfig(remoteServer, vmId);

        Map<String, String> updates = new HashMap<>();
        // Always enable the guest agent so stats/IP reporting works.
        updates.put("agent", "1");

        String gateway = null;
        Integer prefix = null;

        if (cfg != null && cfg.getBridge() != null && !cfg.getBridge().isBlank()) {
            // Resolve the selected bridge's addressing for the cloud-init gateway/prefix.
            Optional<ProxmoxNetwork> bridge = getNodeNetworks(remoteServer).stream()
                    .filter(n -> cfg.getBridge().equals(n.getIface()))
                    .findFirst();
            if (bridge.isPresent()) {
                gateway = IpUtils.firstIpv4(bridge.get().getAddress());
                prefix = IpUtils.prefixFromCidr(bridge.get().getCidr());
            }

            // Re-point every existing adapter at the chosen bridge, preserving model/MAC/firewall.
            if (vmConfig != null && vmConfig.getNetworks() != null) {
                for (Map.Entry<String, String> entry : vmConfig.getNetworks().entrySet()) {
                    String rewritten = entry.getValue().replaceAll("bridge=[^,]+", "bridge=" + cfg.getBridge());
                    updates.put(entry.getKey(), rewritten);
                }
            }
        }

        updateVmNetworkConfig(remoteServer, vmId, updates);

        // Static IP via cloud-init. Sent separately so a non-cloud-init template doesn't abort
        // the bridge change above; updateVmNetworkConfig already isolates/logs failures.
        if (cfg != null && cfg.getIpAddress() != null && !cfg.getIpAddress().isBlank()) {
            int cidrPrefix = (prefix != null) ? prefix : 24;
            StringBuilder ipconfig = new StringBuilder("ip=")
                    .append(cfg.getIpAddress().trim()).append('/').append(cidrPrefix);
            if (gateway != null) {
                ipconfig.append(",gw=").append(gateway);
            }
            updateVmNetworkConfig(remoteServer, vmId, Map.of("ipconfig0", ipconfig.toString()));
        }

        verifyVmNetworkConfig(remoteServer, vmId, cfg);
    }

    /** Reads the VM config back after an update and logs whether the bridge/IP actually stuck. */
    private void verifyVmNetworkConfig(RemoteServer remoteServer, Integer vmId, LabNetworkConfig cfg) {
        if (cfg == null) {
            return;
        }
        try {
            ProxmoxVMConfig applied = getVmConfigById(remoteServer, vmId);
            if (applied == null) {
                LOGGER.warn("Could not read back config for VM {} to verify network settings", vmId);
                return;
            }
            if (cfg.getBridge() != null) {
                boolean onBridge = applied.getNetworks().values().stream()
                        .anyMatch(v -> v.contains("bridge=" + cfg.getBridge()));
                if (!onBridge) {
                    LOGGER.warn("VM {} not attached to expected bridge {} after update", vmId, cfg.getBridge());
                }
            }
            if (cfg.getIpAddress() != null && applied.getIpConfigs().values().stream()
                    .noneMatch(v -> v.contains(cfg.getIpAddress()))) {
                LOGGER.warn("VM {} did not receive expected IP {} (template may lack a cloud-init drive)",
                        vmId, cfg.getIpAddress());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to verify network config for VM {}: {}", vmId, e.getMessage());
        }
    }

    /**
     * Lists the node's selectable bridges for the deploy dropdown, annotated with a best-effort
     * count of free host addresses. Only bridges that expose a CIDR (i.e. carry a subnet we can
     * address into) are returned.
     */
    public List<ProxmoxNetworkAdapter> listNetworkAdapters(RemoteServer remoteServer) {
        Set<String> used = collectUsedIps(remoteServer);
        return getNodeNetworks(remoteServer).stream()
                .filter(n -> n.getIface() != null && n.getCidr() != null)
                .filter(n -> n.getIface().startsWith("vmbr") || n.getIface().startsWith("abr")
                        || n.getIface().startsWith("amaterasu"))
                .map(n -> {
                    List<String> hosts = IpUtils.hostAddresses(n.getCidr(), 0);
                    String gateway = IpUtils.firstIpv4(n.getAddress());
                    long free = hosts.stream()
                            .filter(ip -> !ip.equals(gateway) && !used.contains(ip))
                            .count();
                    return ProxmoxNetworkAdapter.builder()
                            .iface(n.getIface())
                            .cidr(n.getCidr())
                            .gateway(gateway)
                            .availableIpCount((int) free)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns up to {@code count} free host addresses on the given bridge, excluding the bridge's
     * own gateway address and any IP already assigned to a VM on the node.
     */
    public List<String> getAvailableIps(RemoteServer remoteServer, String bridge, int count) {
        Optional<ProxmoxNetwork> network = getNodeNetworks(remoteServer).stream()
                .filter(n -> bridge != null && bridge.equals(n.getIface()))
                .findFirst();
        if (network.isEmpty() || network.get().getCidr() == null) {
            return List.of();
        }
        String gateway = IpUtils.firstIpv4(network.get().getAddress());
        Set<String> used = collectUsedIps(remoteServer);

        List<String> available = new ArrayList<>();
        for (String host : IpUtils.hostAddresses(network.get().getCidr(), 0)) {
            if (host.equals(gateway) || used.contains(host)) {
                continue;
            }
            available.add(host);
            if (count > 0 && available.size() >= count) {
                break;
            }
        }
        return available;
    }

    /** Gathers every IPv4 address currently assigned via ipconfig* across the node's VMs. */
    private Set<String> collectUsedIps(RemoteServer remoteServer) {
        Set<String> used = new HashSet<>();
        for (ProxmoxVM vm : getVMs(remoteServer)) {
            try {
                ProxmoxVMConfig config = getVmConfigById(remoteServer, vm.getVmid());
                if (config == null) {
                    continue;
                }
                config.getIpConfigs().values().forEach(ipconfig -> {
                    String ip = IpUtils.firstIpv4(ipconfig);
                    if (ip != null) {
                        used.add(ip);
                    }
                });
            } catch (Exception e) {
                LOGGER.debug("Skipping used-IP scan for VM {}: {}", vm.getVmid(), e.getMessage());
            }
        }
        return used;
    }

    public LabActionResult startProxmoxLab(LabTracker labTracker, RemoteServer remoteServer) {
        if (remoteServer.getServerType() != ServerType.PROXMOX) {
            throw new RuntimeException("Invalid server type");
        }

        LOGGER.info("Stage: START VMS");

        AtomicInteger failures = new AtomicInteger();
        List<String> responses = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        List<ProxmoxVM> vms = labTracker.getVms();
        vms.forEach(vm -> startProxmoxVM(vm, remoteServer, responses, failures));

        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(failures.get() == 0)
                .output(String.join("\n", responses))
                .build();
    }

    public LabActionResult stopProxmoxLab(LabTracker labTracker, RemoteServer remoteServer) {
        if (remoteServer.getServerType() != ServerType.PROXMOX) {
            throw new RuntimeException("Invalid server type");
        }

        AtomicInteger failures = new AtomicInteger();
        List<String> responses = new ArrayList<>();

        List<ProxmoxVM> vms = labTracker.getVms();
        vms.forEach(vm -> stopProxmoxVM(vm, remoteServer, responses, failures));

        boolean allVMsStopped = waitForVMsToReachStatus(vms, remoteServer, "stopped");

        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(failures.get() == 0 && allVMsStopped)
                .output(String.join("\n", responses))
                .build();
    }

    public LabActionResult deleteProxmoxLab( LabTracker labTracker, RemoteServer remoteServer) {
        if (remoteServer.getServerType() != ServerType.PROXMOX) {
            throw new RuntimeException("Invalid server type");
        }

        AtomicInteger failures = new AtomicInteger();
        List<String> responses = new ArrayList<>();

        List<ProxmoxVM> vms = new ArrayList<>(labTracker.getVms());
        vms.forEach(vm -> deleteProxmoxVM(vm, remoteServer, responses, failures));

        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(failures.get() == 0)
                .output(String.join("\n", responses))
                .build();
    }

    private void startProxmoxVM(ProxmoxVM vm, RemoteServer remoteServer, List<String> responses,
                                AtomicInteger failures) {
        String startUrl = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%d/status/start",
                remoteServer.getIpAddress(), remoteServer.getNodeName(), vm.getVmid());

        Map<String, Object> payloadStart = Map.of(
                "vmid", vm.getVmid(),
                "node", remoteServer.getNodeName()
        );

        ResponseEntity<ProxmoxActionResponse> responseStart = sendProxmoxRequest(
                startUrl, payloadStart, HttpMethod.POST, remoteServer);

        responses.add("Start response " + vm.getName() + ": " + responseStart.getStatusCode());

        if (!responseStart.getStatusCode().is2xxSuccessful()) {
            failures.incrementAndGet();
        }
    }

    private void stopProxmoxVM(ProxmoxVM vm, RemoteServer remoteServer, List<String> responses,
                               AtomicInteger failures) {
        // Stop VM Request
        String stopUrl = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%d/status/stop",
                remoteServer.getIpAddress(), remoteServer.getNodeName(), vm.getVmid());

        Map<String, Object> payloadStop = Map.of(
                "vmid", vm.getVmid(),
                "node", remoteServer.getNodeName()
        );

        ResponseEntity<ProxmoxActionResponse> responseStop = sendProxmoxRequest(
                stopUrl, payloadStop, HttpMethod.POST, remoteServer);

        responses.add("Stop response " + vm.getName() + ": " + responseStop.getStatusCode());

        if (!responseStop.getStatusCode().is2xxSuccessful()) {
            failures.incrementAndGet();
        }
    }

    private void deleteProxmoxVM(ProxmoxVM vm, RemoteServer remoteServer, List<String> responses,
                                 AtomicInteger failures) {
        try {
            // Delete VM Request
            String deleteUrl = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%d",
                    remoteServer.getIpAddress(), remoteServer.getNodeName(), vm.getVmid());

            ResponseEntity<ProxmoxActionResponse> responseDelete = sendProxmoxRequest(
                    deleteUrl, null, HttpMethod.DELETE, remoteServer);

            responses.add("Delete response " + vm.getName() + ": " + responseDelete.getStatusCode());

            if (!responseDelete.getStatusCode().is2xxSuccessful()) {
                failures.incrementAndGet();
            }
        } catch (Exception e) {
            LOGGER.error("Error removing VM {} ({}): {}", vm.getName(), vm.getVmid(), e.getMessage());
            failures.incrementAndGet();
        }
    }

    private ProxmoxVMConfig pollForVmConfig(RemoteServer remoteServer, Integer vmId) {
        ProxmoxVMConfig proxmoxVMConfig;
        int maxRetries = 15; // 30 seconds max (15 * 2s)
        int retryCount = 0;

        // Poll until networks are available or timeout
        do {
            proxmoxVMConfig = getVmConfigById(remoteServer, vmId);
            if (proxmoxVMConfig != null && !proxmoxVMConfig.getNetworks().isEmpty()) {
                break;
            }

            LOGGER.warn("VM {} network config not available yet, retrying... ({}/{})", vmId, retryCount + 1, maxRetries);
            try {
                Thread.sleep(2000); // Wait 2 seconds before retrying
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("Polling interrupted while waiting for VM {} network config.", vmId);
            }

            retryCount++;
        } while (retryCount < maxRetries);

        // Final check before proceeding
        if (proxmoxVMConfig == null || proxmoxVMConfig.getNetworks().isEmpty()) {
            LOGGER.warn("No network configuration found for VM {} on server {} after waiting.", vmId, remoteServer.getIpAddress());
        }
        return proxmoxVMConfig;
    }

    private void ensureNodeBridgeExists(RemoteServer remoteServer, String bridgeName, String ipAddress, String netmask) {
        // Get existing networks to check if bridge already exists
        List<ProxmoxNetwork> networks = getNodeNetworks(remoteServer);

        // Check if bridge already exists
        boolean bridgeExists = networks.stream()
                .anyMatch(network -> bridgeName.equals(network.getIface()));

        if (bridgeExists) {
            LOGGER.info("Bridge {} already exists on server {}", bridgeName, remoteServer.getIpAddress());
            return;
        }

        // Create bridge if it doesn't exist
        Map<String, Object> payload = new HashMap<>();
        payload.put("iface", bridgeName);
        payload.put("node", remoteServer.getNodeName());
        payload.put("type", "bridge");
        payload.put("autostart", 1); // Ensure bridge starts automatically

        // Only add address and netmask if both are provided
        if (ipAddress != null && netmask != null) {
            payload.put("address", ipAddress);
            payload.put("netmask", netmask);
        }

        // Use the specialized method to create the bridge
        createNodeNetworkBridge(remoteServer, payload, bridgeName);

        // Apply network changes to activate the bridge
        applyNetworkChanges(remoteServer);
    }

    private ResponseEntity<ProxmoxActionResponse> sendProxmoxRequest(String url, Map<String, Object> payload,
                                                                     HttpMethod httpMethod, RemoteServer remoteServer) {
        try {
            HttpHeaders headers = createHeaders(remoteServer);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity;

            if (payload != null && (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT)) {
                // Only include payload for POST and PUT requests
                String jsonPayload = objectMapper.writeValueAsString(payload);
                headers.setContentLength(jsonPayload.getBytes(StandardCharsets.UTF_8).length);
                entity = new HttpEntity<>(jsonPayload, headers);
            } else {
                // Do not include a body for DELETE or GET requests
                entity = new HttpEntity<>(headers);
            }

            return restTemplate.exchange(url, httpMethod, entity, ProxmoxActionResponse.class);
        } catch (JsonProcessingException e) {
            LOGGER.error("Issue with json payload: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error sending request to Proxmox: {}", e.getMessage());
        }
        return ResponseEntity.badRequest().body(new ProxmoxActionResponse(""));
    }

    private boolean waitForVMsToReachStatus(List<ProxmoxVM> vms, RemoteServer remoteServer, String targetStatus) {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < 30000L) {
            List<ProxmoxVM> matchingVMs = getVMs(remoteServer).stream()
                    .filter(vm -> vms.stream().anyMatch(v -> v.getVmid() == vm.getVmid()))
                    .filter(vm -> targetStatus.equalsIgnoreCase(vm.getStatus()))
                    .toList();

            if (matchingVMs.size() == vms.size()) {
                return true; // All VMs reached the desired status
            }

            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false; // Indicate that the waiting was interrupted
            }
        }
        return false; // Timeout reached
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
        return name.replaceAll("[^a-zA-Z0-9-]", "-")
                .replaceAll("^-+|-+$", "");
    }

    public static RestTemplate createRestTemplate() {
        try {
            // Create SSL context that ignores certificate verification
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial((X509Certificate[] chain, String authType) -> true)
                    .build();

            // Create SSL Socket Factory
            TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(
                    sslContext,
                    HostnameVerificationPolicy.CLIENT,
                    NoopHostnameVerifier.INSTANCE);


            // Create a connection manager with the registry
            PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setTlsSocketStrategy(tlsSocketStrategy)
                    .build();

            // Optional: Set timeouts
            connectionManager.setDefaultConnectionConfig(
                    ConnectionConfig.custom().setSocketTimeout(Timeout.ofSeconds(30)).build()
            );

            // Create HttpClient with the custom connection manager
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();

            // Use HttpClient in RestTemplate
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            return new RestTemplate(factory);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL-ignoring RestTemplate", e);
        }
    }

    public List<ProxmoxVM> convertFromJson(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(json, new TypeReference<List<ProxmoxVM>>() {});
        } catch (Exception e) {
            System.err.println("Error deserializing JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String convertToJson(List<ProxmoxVM> vms) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(vms);
        } catch (Exception e) {
            System.err.println("Error serializing JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String cidrToNetmask(String cidr) {
        try {
            // Split the CIDR string; we only need the prefix length.
            String[] parts = cidr.split("/");
            int prefixLength = Integer.parseInt(parts[1]);

            // Create the netmask as a 32-bit integer.
            int mask = 0xffffffff << (32 - prefixLength);
            // Convert the integer mask into four octets.
            return String.format("%d.%d.%d.%d",
                    (mask >>> 24) & 0xff,
                    (mask >> 16) & 0xff,
                    (mask >> 8) & 0xff,
                    mask & 0xff);
        } catch (Exception e) {
            LOGGER.error("Error converting CIDR {} to netmask: {}", cidr, e.getMessage());
            return null;
        }
    }

    public String generateRandomMac() {
        Random random = new Random();
        return String.format("52:54:%02x:%02x:%02x:%02x",
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256)
        );
    }
}
