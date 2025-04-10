package com.infernokun.amaterasu.services.alt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.*;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.proxmox.*;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.entity.LabTrackerService;
import com.infernokun.amaterasu.utils.AESUtil;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

            if (response.getBody() != null) {
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

        // Ensure amaterasu0 bridge exists
        ensureNodeBridgeExists(remoteServer, "amaterasu0", "10.254.0.1", "255.255.0.0");

        AtomicInteger failures = new AtomicInteger();
        List<String> responses = new ArrayList<>();

        List<ProxmoxVM> vms = getVMTemplates(remoteServer).stream()
                .filter(v -> labTracker.getLabStarted().getVmIds().contains(v.getVmid()))
                .toList();
        List<ProxmoxVM> clonedVMs = new ArrayList<>();

        vms.forEach(vm -> {
            LOGGER.info("Stage: CLONE START {}", vm.getName());
            String newVmName = sanitizeName(labTracker.getLabOwner().getName() +
                    "-" + labTracker.getLabStarted().getName() + "-" + vm.getName());

            try {
                int newVmid = 100 + ThreadLocalRandom.current().nextInt(999999900);

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

                vm.setVmid(newVmid);
                ProxmoxVM clonedVm = new ProxmoxVM(newVmid, vm);
                clonedVMs.add(clonedVm);

            } catch (Exception e) {
                LOGGER.error("Error processing VM {} ({}): {}", vm.getName(), newVmName, e.getMessage());
                failures.incrementAndGet();
            }
        });

        int nextAbrIndex = getNodeNetworks(remoteServer).stream()
                .map(ProxmoxNetwork::getIface)
                .filter(iface -> iface.startsWith("abr"))
                .map(iface -> iface.replace("abr", ""))
                .filter(num -> num.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(-1) + 1;

        clonedVMs.forEach(clonedVM -> modifyProxmoxVMConfig(clonedVM.getVmid(), remoteServer, nextAbrIndex));

        // Now start the cloned VMs
        vms.forEach(vm -> startProxmoxVM(vm, remoteServer, responses, failures));

        boolean allVMsStarted = waitForVMsToReachStatus(vms, remoteServer, "running");

        List<ProxmoxVM> filteredVMs =
                getVMs(remoteServer).stream()
                        .filter(vm -> vms.stream().anyMatch(v -> v.getVmid() == vm.getVmid()))
                        .toList();

        labTracker.setVms(filteredVMs);
        LOGGER.info("Stage: COMPLETE AND RETURN");

        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(failures.get() == 0 && allVMsStarted)
                .output(String.join("\n", responses + "\n" + filteredVMs))
                .build();
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

    private void modifyProxmoxVMConfig(Integer vmId, RemoteServer remoteServer, int startingIndex) {
        ProxmoxVMConfig proxmoxVMConfig = pollForVmConfig(remoteServer, vmId);
        LOGGER.info("VM {} network config found: {}", vmId, proxmoxVMConfig);

        if (proxmoxVMConfig == null || proxmoxVMConfig.getNetworks().isEmpty()) {
            LOGGER.warn("No network configuration found for VM {} on server {}", vmId, remoteServer.getIpAddress());
            return;
        }

        Map<String, String> updatedNetworks = new HashMap<>();

        // Get the node's network interfaces so we can look up IP and CIDR details
        List<ProxmoxNetwork> nodeNetworks = getNodeNetworks(remoteServer);

        // Build a mapping from the original bridge (e.g., "vmbr1") to a new bridge name (e.g., "abr0", "abr1", etc.)
        Map<String, String> bridgeMapping = new HashMap<>();

        // Iterate through each network interface from the VM config
        for (Map.Entry<String, String> entry : proxmoxVMConfig.getNetworks().entrySet()) {
            String netKey = entry.getKey();    // e.g., "net0", "net1"
            String netValue = entry.getValue();  // e.g., "virtio=MAC,bridge=vmbr1,firewall=1"

            // Look for a "bridge=vmbrX" pattern
            Matcher matcher = Pattern.compile("bridge=(vmbr\\d+)").matcher(netValue);
            if (matcher.find()) {
                String originalBridge = matcher.group(1);

                // If we haven't already mapped this original bridge, assign the next available abrN
                if (!bridgeMapping.containsKey(originalBridge)) {
                    LOGGER.error("{} on {}: Found a bridge to replace.. ", originalBridge, vmId);
                    String newBridge = "abr" + startingIndex;
                    bridgeMapping.put(originalBridge, newBridge);
                    startingIndex++;

                    // Look up the original bridge's details (IP and CIDR) from the node's network list
                    Optional<ProxmoxNetwork> origNetOpt = nodeNetworks.stream()
                            .filter(n -> originalBridge.equals(n.getIface()))
                            .findFirst();
                    String ipAddress = null;
                    String cidr = null;
                    if (origNetOpt.isPresent()) {
                        ProxmoxNetwork origNet = origNetOpt.get();
                        ipAddress = origNet.getAddress();
                        cidr = origNet.getCidr();
                    }
                    // Convert CIDR to netmask if available
                    String netmask = (cidr != null) ? cidrToNetmask(cidr) : null;

                    LOGGER.error("{} on {}: Found ip and cidr {}/{} ", originalBridge, vmId, ipAddress, netmask);

                    // Ensure the new team-specific bridge exists on the node
                    ensureNodeBridgeExists(remoteServer, newBridge, ipAddress, netmask);
                }
                // Replace the original bridge in the VM's network configuration with the new bridge name
                String newBridge = bridgeMapping.get(originalBridge);
                String updatedNetValue = netValue.replace("bridge=" + originalBridge, "bridge=" + newBridge);
                        //.replaceAll("virtio=([0-9A-Fa-f:]+)", "virtio=" + generateRandomMac());
                updatedNetworks.put(netKey, updatedNetValue);
            }
        }

        int maxNetIndex = updatedNetworks.keySet().stream()
                .map(key -> key.replace("net", ""))
                .filter(num -> num.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(-1);

        updatedNetworks.put("net" + (maxNetIndex + 1),
                "virtio=" + generateRandomMac() + ",bridge=amaterasu0,firewall=1");
        updatedNetworks.put("agent", "1");

        // Update the VM configuration in Proxmox with the modified network settings
        updateVmNetworkConfig(remoteServer, vmId, updatedNetworks);
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
            e.printStackTrace(); // VERY important for debugging
            return null; // Or throw an exception, depending on your needs
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
