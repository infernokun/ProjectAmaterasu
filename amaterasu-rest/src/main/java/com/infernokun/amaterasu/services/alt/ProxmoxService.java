package com.infernokun.amaterasu.services.alt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.*;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.repositories.LabTrackerRepository;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.utils.AESUtil;
import jakarta.persistence.EntityManager;
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
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
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
    private final AmaterasuConfig amaterasuConfig;
    private final AESUtil aesUtil;
    private final EntityManager entityManager;
    private final LabTrackerRepository labTrackerRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    //private static final String PROXMOX_API_URL = "https://10.0.0.250:8006/api2/json/nodes/inferno/qemu";

    // Constructor now uses the custom RestTemplate
    public ProxmoxService(AmaterasuConfig amaterasuConfig, AESUtil aesUtil, EntityManager entityManager, LabTrackerRepository labTrackerRepository) {
        this.aesUtil = aesUtil;
        this.entityManager = entityManager;
        this.labTrackerRepository = labTrackerRepository;
        this.restTemplate = createRestTemplate();
        this.amaterasuConfig = amaterasuConfig;
    }

    public List<ProxmoxVM> getVMs(RemoteServer remoteServer) {
        if (remoteServer.getServerType() != ServerType.PROXMOX) throw new RuntimeException();

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

    public LabActionResult startProxmoxLab(Lab lab, LabTracker labTracker, RemoteServer remoteServer) {
        if (remoteServer.getServerType() != ServerType.PROXMOX) {
            throw new RuntimeException("Invalid server type");
        }

        AtomicInteger failures = new AtomicInteger();
        List<String> responses = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        List<ProxmoxVM> vms = getVMTemplates(remoteServer).stream()
                .filter(v -> lab.getVmIds().contains(v.getVmid()))
                .toList();

        vms.forEach(vm -> {
            String newVmName = sanitizeName(labTracker.getLabOwner().getName() + "-" + lab.getName() + "-" + vm.getName());
            try {
                int newVmid = 100 + ThreadLocalRandom.current().nextInt(999999900);

                // Clone VM Request
                String cloneUrl = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%s/clone",
                        remoteServer.getIpAddress(), remoteServer.getNodeName(), vm.getVmid());

                Map<String, Object> payloadClone = Map.of(
                        "name", newVmName,
                        "newid", newVmid,
                        "node", remoteServer.getNodeName(),
                        "description", lab.getName() + " created for " + labTracker.getLabOwner().getName()
                );

                ResponseEntity<ProxmoxActionResponse> responseClone = sendProxmoxRequest(
                        cloneUrl, payloadClone, objectMapper, HttpMethod.POST, remoteServer);

                responses.add("Clone response " + vm.getName() + ": " + responseClone.getStatusCode());

                if (!responseClone.getStatusCode().is2xxSuccessful()) {
                    failures.incrementAndGet();
                    return;
                }

                vm.setVmid(newVmid);

                // Start Cloned VM Request
                String startUrl = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%d/status/start",
                        remoteServer.getIpAddress(), remoteServer.getNodeName(), newVmid);


                Map<String, Object> payloadStart = Map.of(
                        "vmid", newVmid,
                        "node", remoteServer.getNodeName()
                );

                ResponseEntity<ProxmoxActionResponse> responseStart = sendProxmoxRequest(
                        startUrl, payloadStart, objectMapper, HttpMethod.POST, remoteServer);

                responses.add("Start response " + vm.getName() + ": " + responseStart.getStatusCode());

                if (!responseStart.getStatusCode().is2xxSuccessful()) {
                    failures.incrementAndGet();
                }
            } catch (Exception e) {
                LOGGER.error("Error processing VM {} ({}): {}", vm.getName(), newVmName, e.getMessage());
                failures.incrementAndGet();
            }
        });

        boolean allVMsStarted = waitForVMsToStart(vms, remoteServer, 30); // Wait for 30 seconds

        List<ProxmoxVM> filteredVMs =
                getVMs(remoteServer).stream()
                        .filter(vm -> vms.stream().anyMatch(v -> v.getVmid() == vm.getVmid()))
                        .toList();

        labTracker.setVms(filteredVMs);

        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(failures.get() == 0 && allVMsStarted)
                .output(String.join("\n", responses + "\n" + filteredVMs))
                .build();
    }

    @Transactional
    public LabActionResult stopProxmoxLab(Lab lab, LabTracker labTracker, RemoteServer remoteServer) {
        if (remoteServer.getServerType() != ServerType.PROXMOX) {
            throw new RuntimeException("Invalid server type");
        }

        AtomicInteger failures = new AtomicInteger();
        List<String> responses = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        List<ProxmoxVM> vms = labTracker.getVms().stream()
                .filter(v -> lab.getVmIds().contains(v.getVmid()))
                .toList();

        vms.forEach(vm -> {
            try {
                // Stop VM Request
                String stopUrl = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%d/status/stop",
                        remoteServer.getIpAddress(), remoteServer.getNodeName(), vm.getVmid());

                Map<String, Object> payloadStop = Map.of(
                        "vmid", vm.getVmid(),
                        "node", remoteServer.getNodeName()
                );

                ResponseEntity<ProxmoxActionResponse> responseStop = sendProxmoxRequest(
                        stopUrl, payloadStop, objectMapper, HttpMethod.POST, remoteServer);

                responses.add("Stop response " + vm.getName() + ": " + responseStop.getStatusCode());

                if (!responseStop.getStatusCode().is2xxSuccessful()) {
                    failures.incrementAndGet();
                    return;
                }

                // Delete VM Request
                String deleteUrl = String.format("https://%s:8006/api2/json/nodes/%s/qemu/%d",
                        remoteServer.getIpAddress(), remoteServer.getNodeName(), vm.getVmid());

                ResponseEntity<ProxmoxActionResponse> responseDelete = sendProxmoxRequest(
                        deleteUrl, Map.of(), objectMapper, HttpMethod.DELETE, remoteServer);

                responses.add("Delete response " + vm.getName() + ": " + responseDelete.getStatusCode());

                if (!responseDelete.getStatusCode().is2xxSuccessful()) {
                    failures.incrementAndGet();
                    return;
                }

                labTracker.getVms().remove(vm);

            } catch (Exception e) {
                LOGGER.error("Error stopping/removing VM {}: {}", vm.getName(), e.getMessage());
                failures.incrementAndGet();
            }
        });

        return LabActionResult.builder()
                .labTracker(labTracker)
                .isSuccessful(failures.get() == 0)
                .output(String.join("\n", responses))
                .build();
    }

    /**
     * Sends an HTTP POST request to the Proxmox API.
     */
    private ResponseEntity<ProxmoxActionResponse> sendProxmoxRequest(String url, Map<String, Object> payload,
                                                                     ObjectMapper objectMapper, HttpMethod httpMethod,
                                                                     RemoteServer remoteServer)  {
        String jsonPayload = null;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);

            HttpHeaders headers = createHeaders(remoteServer);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentLength(jsonPayload.getBytes(StandardCharsets.UTF_8).length);

            HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);
            return restTemplate.exchange(url, httpMethod, entity, ProxmoxActionResponse.class);
        } catch (JsonProcessingException e) {
            LOGGER.error("Issue with json payload: {}", e.getMessage());
        }
        return ResponseEntity.badRequest().body(new ProxmoxActionResponse(""));
    }

    private boolean waitForVMsToStart(List<ProxmoxVM> vms, RemoteServer remoteServer, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000L) {
            List<ProxmoxVM> runningVMs = getVMs(remoteServer).stream()
                    .filter(vm -> vms.stream().anyMatch(v -> v.getVmid() == vm.getVmid()))
                    .filter(vm -> "running".equalsIgnoreCase(vm.getStatus())) // Assuming ProxmoxVM has a getStatus() method
                    .toList();

            if (runningVMs.size() == vms.size()) {
                // All VMs are running
                return true;
            }

            try {
                Thread.sleep(2000); // Wait for 2 seconds before polling again
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false; // Indicate that the waiting was interrupted
            }
        }
        // Timeout reached
        return false;
    }

    private HttpHeaders createHeaders(RemoteServer remoteServer) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PVEAPIToken " + aesUtil.decrypt(remoteServer.getApiToken()));
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

}
