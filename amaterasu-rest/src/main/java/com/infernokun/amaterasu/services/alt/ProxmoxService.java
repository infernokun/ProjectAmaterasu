package com.infernokun.amaterasu.services.alt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.ServerCheck;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.entities.lab.RemoteServerStats;
import com.infernokun.amaterasu.models.enums.LabStatus;
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
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
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
    private static final Pattern MAC = Pattern.compile("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}");
    private static final Pattern STATIC_IP = Pattern.compile("(?:^|,)ip=(\\d+\\.\\d+\\.\\d+\\.\\d+)/(\\d+)");
    /** How long a deploy waits for a bastion's guest agent to report its address. */
    private static final Duration GUEST_TIMEOUT = Duration.ofMinutes(3);
    private static final SecureRandom RANDOM = new SecureRandom();
    // No l, o, 0 or 1: easy to read off the screen and type
    private static final String PASSWORD_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    // Bridge numbers are picked from what exists on the node, so two deploys must not pick at once
    private static final Object BRIDGE_LOCK = new Object();

    private final RestTemplate restTemplate;
    private final AESUtil aesUtil;
    private Duration guestTimeout = GUEST_TIMEOUT;
    private long guestPollMillis = 3000;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ProxmoxService(AESUtil aesUtil) {
        this(aesUtil, createRestTemplate());
    }

    public ProxmoxService(AESUtil aesUtil, RestTemplate restTemplate) {
        this.aesUtil = aesUtil;
        this.restTemplate = restTemplate;
    }

    /** For tests: how long a deploy waits for guest agents, and how often it asks. */
    void guestWait(Duration timeout, long pollMillis) {
        this.guestTimeout = timeout;
        this.guestPollMillis = pollMillis;
    }

    private String api(RemoteServer remoteServer, String path) {
        return String.format("https://%s:8006/api2/json%s", remoteServer.getIpAddress(), path);
    }

    private String nodeApi(RemoteServer remoteServer, String path) {
        return api(remoteServer, "/nodes/" + remoteServer.getNodeName() + path);
    }

    static final String TOKEN_REJECTED = "Proxmox rejected the token. Use the full USER@REALM!TOKENID=SECRET";

    /** Privileges to list VMs and read the node, by ACL path. Without them the server is not usable. */
    static Map<String, List<String>> readPrivileges(String node) {
        Map<String, List<String>> needed = new LinkedHashMap<>();
        needed.put("/vms", List.of("VM.Audit"));
        needed.put("/nodes/" + node, List.of("Sys.Audit"));
        return needed;
    }

    /** Privileges to clone, network, start, open and destroy lab VMs and add their bridges. */
    static Map<String, List<String>> deployPrivileges(String node) {
        Map<String, List<String>> needed = new LinkedHashMap<>();
        needed.put("/vms", List.of("VM.Clone", "VM.Allocate", "VM.Config.Network", "VM.Config.Options",
                "VM.PowerMgmt", "VM.Console"));
        needed.put("/storage", List.of("Datastore.AllocateSpace"));
        needed.put("/nodes/" + node, List.of("Sys.Modify"));
        return needed;
    }

    /** A check of a Proxmox server, with the node status and version it read on the way. */
    public record NodeCheck(ServerCheck result, Map<String, Object> status, String version) {
        static NodeCheck failed(String reason) {
            return new NodeCheck(ServerCheck.failed(reason), null, null);
        }
    }

    public ServerCheck checkConnection(RemoteServer remoteServer) {
        return checkNode(remoteServer).result();
    }

    /**
     * Usable means the token is accepted, the node exists and the token can list VMs and read the node.
     * Missing deploy privileges only warn: such a server can still be watched.
     */
    public NodeCheck checkNode(RemoteServer remoteServer) {
        String version;
        try {
            Object value = getData(remoteServer, api(remoteServer, "/version")).get("version");
            version = value != null ? value.toString() : null;
        } catch (RestClientResponseException e) {
            return NodeCheck.failed(e.getStatusCode().value() == 401 ? TOKEN_REJECTED
                    : "Proxmox at " + address(remoteServer) + " answered " + e.getStatusCode().value() + " " + proxmoxMessage(e));
        } catch (RestClientException e) {
            return NodeCheck.failed(noAnswer(remoteServer));
        }

        Map<String, Object> status = null;
        try {
            status = getData(remoteServer, nodeApi(remoteServer, "/status"));
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 401) {
                return NodeCheck.failed(TOKEN_REJECTED);
            }
            // 403 is a missing Sys.Audit, named below. Anything else is a node name the host cannot route to
            if (code != 403) {
                return NodeCheck.failed("No node called " + remoteServer.getNodeName() + " on that host");
            }
        } catch (RestClientException e) {
            return NodeCheck.failed(noAnswer(remoteServer));
        }

        Map<String, List<String>> read = readPrivileges(remoteServer.getNodeName());
        Map<String, List<String>> deploy = deployPrivileges(remoteServer.getNodeName());
        Map<String, Set<String>> privileges;
        try {
            Set<String> paths = new LinkedHashSet<>(read.keySet());
            paths.addAll(deploy.keySet());
            privileges = privilegesAt(remoteServer, paths);
        } catch (RestClientResponseException e) {
            return new NodeCheck(ServerCheck.failed(e.getStatusCode().value() == 401 ? TOKEN_REJECTED
                    : "Could not read the token's privileges: " + e.getStatusCode().value() + " " + proxmoxMessage(e)), status, version);
        } catch (RestClientException e) {
            return new NodeCheck(ServerCheck.failed(noAnswer(remoteServer)), status, version);
        }

        List<String> missingRead = missing(privileges, read);
        if (!missingRead.isEmpty()) {
            return new NodeCheck(ServerCheck.failed("The Proxmox token is missing " + String.join("; ", missingRead)), status, version);
        }
        List<String> missingDeploy = missing(privileges, deploy);
        return new NodeCheck(missingDeploy.isEmpty() ? ServerCheck.ok()
                : ServerCheck.warning("Can list VMs but can't deploy labs: missing " + String.join("; ", missingDeploy)),
                status, version);
    }

    private static final Pattern PVE_VERSION = Pattern.compile("pve-manager/([^/\\s]+)");
    private static final double GB = 1024d * 1024 * 1024;

    /**
     * Checks the node and reads its host stats from /nodes/{node}/status, in the units the Docker
     * stats script uses: GB for memory and disk, seconds for uptime.
     */
    public RemoteServerStats nodeStats(RemoteServer remoteServer) {
        NodeCheck check = checkNode(remoteServer);
        RemoteServerStats stats = new RemoteServerStats();
        stats.setHostname(remoteServer.getNodeName());
        stats.setOsName("Proxmox VE");
        stats.setStatus(check.result().connected() ? LabStatus.ACTIVE : LabStatus.OFFLINE);
        stats.setWarning(check.result().connected() ? check.result().warning() : check.result().reason());
        stats.setOsVersion(check.version());
        Map<String, Object> status = check.status();
        if (status == null) {
            return stats;
        }
        Matcher version = PVE_VERSION.matcher(String.valueOf(status.get("pveversion")));
        if (version.find()) {
            stats.setOsVersion(version.group(1));
        }
        stats.setCpu((int) number(section(status, "cpuinfo"), "cpus"));
        stats.setCpuUsagePercent(Math.round(number(status, "cpu") * 1000) / 10d);
        Map<String, Object> memory = section(status, "memory");
        stats.setTotalRam(gb(number(memory, "total")));
        stats.setUsedRam(gb(number(memory, "used")));
        // PVE 9 adds "available" (MemAvailable, as the Docker script reads); older nodes have only "free"
        stats.setAvailableRam(gb(memory.containsKey("available") ? number(memory, "available") : number(memory, "free")));
        Map<String, Object> rootfs = section(status, "rootfs");
        stats.setTotalDiskSpace(gb(number(rootfs, "total")));
        stats.setUsedDiskSpace(gb(number(rootfs, "used")));
        stats.setAvailableDiskSpace(gb(number(rootfs, "avail")));
        stats.setUptime((long) number(status, "uptime"));
        return stats;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> status, String key) {
        return status.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static double number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(value.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Bytes to GB with one decimal, as the Docker stats script reports. */
    private static float gb(double bytes) {
        return (float) (Math.round(bytes / GB * 10) / 10d);
    }

    /**
     * The token's effective privileges at each path, as PVE resolves them. An ACL on a deeper path replaces
     * what it inherits, so each path is read from /access/permissions, never derived from a parent.
     */
    Map<String, Set<String>> privilegesAt(RemoteServer remoteServer, Collection<String> paths) {
        Map<String, Object> all = getData(remoteServer, api(remoteServer, "/access/permissions"));
        Map<String, Set<String>> privileges = new LinkedHashMap<>();
        for (String path : paths) {
            Object at = all.get(path);
            if (at == null) {
                // Not listed: ask for that path itself
                at = getData(remoteServer, api(remoteServer, "/access/permissions?path=" + path)).get(path);
            }
            // Values are the propagate flag, so a 0 is still held at this path
            privileges.put(path, at instanceof Map<?, ?> map
                    ? map.keySet().stream().map(String::valueOf).collect(Collectors.toSet()) : Set.of());
        }
        return privileges;
    }

    /** "VM.Clone, VM.Allocate on /vms" for each path that lacks some of what is needed. */
    static List<String> missing(Map<String, Set<String>> held, Map<String, List<String>> needed) {
        List<String> missing = new ArrayList<>();
        needed.forEach((path, privileges) -> {
            List<String> lacking = privileges.stream()
                    .filter(privilege -> !held.getOrDefault(path, Set.of()).contains(privilege))
                    .toList();
            if (!lacking.isEmpty()) {
                missing.add(String.join(", ", lacking) + " on " + path);
            }
        });
        return missing;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(RemoteServer remoteServer, String url) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(createHeaders(remoteServer)), new ParameterizedTypeReference<>() { });
        Object data = response.getBody() != null ? response.getBody().get("data") : null;
        if (!(data instanceof Map<?, ?> map)) {
            throw new RestClientException("Proxmox sent no data for " + url);
        }
        return (Map<String, Object>) map;
    }

    private static String address(RemoteServer remoteServer) {
        return remoteServer.getIpAddress() + ":8006";
    }

    private static String noAnswer(RemoteServer remoteServer) {
        return "No answer from " + address(remoteServer) + ". Check the address and that the node is up";
    }

    static final String CANNOT_SEE_VMS = "The Proxmox token can't see VMs (missing VM.Audit on /vms)";

    /** The node's VMs. PVE leaves out VMs the token may not audit, so an empty list is checked against its privileges. */
    public List<ProxmoxVM> getVMs(RemoteServer remoteServer) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        ResponseEntity<ProxmoxResponse> response = restTemplate.exchange(
                nodeApi(remoteServer, "/qemu"), HttpMethod.GET, entity, ProxmoxResponse.class);

        List<ProxmoxVM> vms = response.getBody() != null && response.getBody().getData() != null
                ? response.getBody().getData() : List.of();
        if (vms.isEmpty() && !canAuditVms(remoteServer)) {
            throw new IllegalStateException(CANNOT_SEE_VMS);
        }
        return vms;
    }

    private boolean canAuditVms(RemoteServer remoteServer) {
        try {
            return privilegesAt(remoteServer, List.of("/vms")).get("/vms").contains("VM.Audit");
        } catch (RestClientException e) {
            // Unknown: report the list as it came
            LOGGER.warn("Could not read the token's privileges on {}: {}", remoteServer.getName(), e.getMessage());
            return true;
        }
    }

    public List<ProxmoxVM> getVMTemplates(RemoteServer remoteServer) {
        return getVMs(remoteServer).stream()
                .filter(ProxmoxVM::isTemplate)
                .collect(Collectors.toList());
    }

    /**
     * Returns the current state of the given VMs, skipping only those Proxmox says do not exist.
     * Any other failure throws, so callers never mistake an unreadable VM for a destroyed one.
     */
    public List<ProxmoxVM> getVMsByIds(RemoteServer remoteServer, List<Integer> vmIds) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        List<ProxmoxVM> found = new ArrayList<>();
        for (Integer vmid : vmIds) {
            try {
                ResponseEntity<Map<String, ProxmoxVM>> response = restTemplate.exchange(
                        nodeApi(remoteServer, "/qemu/" + vmid + "/status/current"), HttpMethod.GET, entity,
                        new ParameterizedTypeReference<>() { });
                if (response.getBody() == null || response.getBody().get("data") == null) {
                    throw new IllegalStateException("Could not read VM " + vmid + " on " + remoteServer.getName()
                            + ": Proxmox sent no VM status");
                }
                found.add(response.getBody().get("data"));
            } catch (RestClientResponseException e) {
                if (!vmDoesNotExist(e)) {
                    throw new IllegalStateException("Could not read VM " + vmid + " on " + remoteServer.getName()
                            + ": " + e.getStatusCode().value() + " " + proxmoxMessage(e));
                }
                LOGGER.info("VM {} no longer exists on {}", vmid, remoteServer.getName());
            } catch (RestClientException e) {
                throw new IllegalStateException("Could not read VM " + vmid + " on " + remoteServer.getName()
                        + ": no answer from " + remoteServer.getIpAddress() + " (" + e.getMessage() + ")");
            }
        }
        return found;
    }

    /** PVE answers 500 "Configuration file '…/123.conf' does not exist" for a missing VM. 595 and the rest are not "gone". */
    static boolean vmDoesNotExist(RestClientResponseException e) {
        int code = e.getStatusCode().value();
        return code == 404 || (code == 500 && (String.valueOf(e.getStatusText()) + " " + e.getResponseBodyAsString())
                .contains("does not exist"));
    }

    /** Proxmox's own error text: the body's message, else the status line. */
    static String proxmoxMessage(RestClientResponseException e) {
        try {
            Object message = objectMapper.readValue(e.getResponseBodyAsString(), Map.class).get("message");
            if (message != null && !message.toString().isBlank()) {
                return message.toString().trim();
            }
        } catch (Exception ignored) {
            // Not JSON
        }
        String text = e.getStatusText();
        return text != null && !text.isBlank() ? text.trim() : e.getResponseBodyAsString().trim();
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
        return readNetwork(remoteServer).interfaces();
    }

    /** The node's interfaces as /etc/network/interfaces.new has them, and the diff not applied yet. */
    private record NodeNetwork(List<ProxmoxNetwork> interfaces, String changes) { }

    private NodeNetwork readNetwork(RemoteServer remoteServer) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(remoteServer));
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                nodeApi(remoteServer, "/network"), HttpMethod.GET, entity, new ParameterizedTypeReference<>() { });

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ResourceNotFoundException("Could not read node networks");
        }
        List<ProxmoxNetwork> interfaces = objectMapper.convertValue(
                response.getBody().get("data"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ProxmoxNetwork.class));
        // PVE sets "changes" beside "data" only while edits are pending
        Object changes = response.getBody().get("changes");
        return new NodeNetwork(interfaces != null ? interfaces : List.of(), changes != null ? changes.toString() : null);
    }

    private static final Pattern IFACE_LINE = Pattern.compile("^iface\\s+(\\S+)");
    private static final Pattern AUTO_LINE = Pattern.compile("^(?:auto|allow-\\S+)\\s+(.+)$");
    /** Stands for changed lines whose stanza the diff does not show. */
    static final String UNKNOWN_INTERFACE = "?";

    /**
     * Interfaces a pending interfaces diff touches. Lines are credited to the stanza they sit in;
     * a changed line outside any visible stanza, or a diff that cannot be read, counts as {@link #UNKNOWN_INTERFACE}.
     */
    static Set<String> touchedInterfaces(String changes) {
        Set<String> touched = new LinkedHashSet<>();
        if (changes == null || changes.isBlank()) {
            return touched;
        }
        boolean inHunk = false;
        String stanza = null;
        for (String line : changes.split("\\R")) {
            if (line.startsWith("@@")) {
                inHunk = true;
                stanza = null;
                continue;
            }
            if (!inHunk || line.startsWith("\\")) {
                continue;
            }
            char kind = line.isEmpty() ? ' ' : line.charAt(0);
            if (kind != ' ' && kind != '+' && kind != '-') {
                touched.add(UNKNOWN_INTERFACE);
                continue;
            }
            boolean changed = kind != ' ';
            String text = line.isEmpty() ? "" : line.substring(1).trim();
            Matcher iface = IFACE_LINE.matcher(text);
            Matcher auto = AUTO_LINE.matcher(text);
            if (iface.find()) {
                stanza = iface.group(1);
                if (changed) {
                    touched.add(stanza);
                }
            } else if (auto.find()) {
                if (changed) {
                    touched.addAll(Arrays.asList(auto.group(1).trim().split("\\s+")));
                }
            } else if (text.isEmpty()) {
                stanza = null;
            } else if (changed && !(stanza == null && text.startsWith("#"))) { // a comment outside stanzas changes nothing
                touched.add(stanza != null ? stanza : UNKNOWN_INTERFACE);
            }
        }
        if (!inHunk) {
            touched.add(UNKNOWN_INTERFACE);
        }
        return touched;
    }

    /** Throws when edits other than to {@code own} bridges wait in interfaces.new: a reload would apply them too. */
    private static void requireNoOtherNetworkChanges(RemoteServer remoteServer, String changes, Collection<String> own) {
        String problem = otherNetworkChanges(remoteServer, changes, own);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
    }

    private static String otherNetworkChanges(RemoteServer remoteServer, String changes, Collection<String> own) {
        Set<String> others = touchedInterfaces(changes);
        others.removeIf(iface -> own.contains(iface) && LAB_BRIDGE.matcher(iface).matches());
        if (others.isEmpty()) {
            return null;
        }
        List<String> named = others.stream().filter(iface -> !UNKNOWN_INTERFACE.equals(iface)).toList();
        return "Proxmox has unapplied network changes on " + remoteServer.getNodeName()
                + (named.isEmpty() ? "" : " (" + String.join(", ", named) + ")")
                + "; apply or revert them in the Proxmox UI first";
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

            // A bastion's uplink keeps its bridge, so it needs no lab bridge of its own
            Set<String> sourceBridges = new LinkedHashSet<>();
            templateConfigs.values().forEach(config -> config.getNetworks().forEach((nic, net) -> {
                String bridge = bridgeOf(net);
                if (bridge != null && !config.isUplink(nic)) {
                    sourceBridges.add(bridge);
                }
            }));

            Map<String, String> bridgeMap = createLabBridges(remoteServer, sourceBridges, labTracker, responses);
            if (bridgeMap.size() != sourceBridges.size()) {
                return failed(labTracker, "Failed to create lab networks\n" + String.join("\n", responses));
            }

            List<ProxmoxVM> clones = new ArrayList<>();
            labTracker.setVms(clones);

            // One password per copy, for every clone whose template has a cloud-init user
            String password = null;
            labTracker.setAccessPassword(null);
            if (templateConfigs.values().stream().anyMatch(ProxmoxVMConfig::hasCiuser)) {
                password = newPassword();
                labTracker.setAccessPassword(aesUtil.encrypt(password));
            }

            for (Integer templateId : templateIds) {
                ProxmoxVM template = templates.get(templateId);
                int newVmid = nextVmid(remoteServer);
                String name = sanitizeName(ownerLabel(labTracker) + "-"
                        + labTracker.getLabStarted().getName() + "-" + template.getName());

                ResponseEntity<ProxmoxActionResponse> clone = sendProxmoxRequest(
                        nodeApi(remoteServer, "/qemu/" + templateId + "/clone"),
                        Map.of("newid", newVmid, "name", name,
                                "description", labTracker.getLabStarted().getName() + " for " + ownerLabel(labTracker)),
                        HttpMethod.POST, remoteServer);
                responses.add("Clone " + template.getName() + " -> " + newVmid + ": " + describe(clone));
                if (!clone.getStatusCode().is2xxSuccessful()) {
                    failures++;
                    continue;
                }
                ProxmoxVMConfig source = templateConfigs.get(templateId);
                ProxmoxVM copy = labCopy(newVmid, template, source);
                clones.add(copy);

                if (!waitForTask(remoteServer, upid(clone), CLONE_TIMEOUT)) {
                    responses.add("Clone of " + template.getName() + " did not finish");
                    failures++;
                    continue;
                }

                ResponseEntity<ProxmoxActionResponse> update = sendProxmoxRequest(
                        nodeApi(remoteServer, "/qemu/" + newVmid + "/config"), labNetworkConfig(source, bridgeMap),
                        HttpMethod.PUT, remoteServer);
                responses.add("Network config " + newVmid + ": " + describe(update));
                if (!update.getStatusCode().is2xxSuccessful()) {
                    failures++;
                    continue;
                }

                // Before the first start, so cloud-init applies it on first boot
                if (password != null && source.hasCiuser()) {
                    ResponseEntity<ProxmoxActionResponse> secret = sendProxmoxRequest(
                            nodeApi(remoteServer, "/qemu/" + newVmid + "/config"), Map.of("cipassword", password),
                            HttpMethod.PUT, remoteServer);
                    responses.add("Password for " + source.getCiuser() + " on " + newVmid + ": "
                            + secret.getStatusCode().value());
                    if (!secret.getStatusCode().is2xxSuccessful()) {
                        failures++;
                        continue;
                    }
                }

                if (copy.isBastion()) {
                    readMacs(remoteServer, copy, responses);
                }
            }

            boolean running = false;
            if (failures == 0) {
                failures += startVMs(clones, remoteServer, responses);
                running = failures == 0 && waitForVMsToReachStatus(clones, remoteServer, "running");
            }
            refreshVMs(labTracker, remoteServer);
            if (running) {
                awaitGuests(labTracker, remoteServer, responses);
            }

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
            if (running) {
                awaitGuests(labTracker, remoteServer, responses);
            }
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
                labTracker.setAccessPassword(null);
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
            NodeNetwork network = readNetwork(remoteServer);
            requireNoOtherNetworkChanges(remoteServer, network.changes(), labTracker.getBridges());
            int next = network.interfaces().stream()
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

            // Someone may have edited the node's network since the check above
            String problem = otherNetworkChanges(remoteServer, readNetwork(remoteServer).changes(), created);
            if (problem != null) {
                responses.add(problem);
                for (String bridge : bridgeMap.values()) {
                    ResponseEntity<ProxmoxActionResponse> undo = sendProxmoxRequest(
                            nodeApi(remoteServer, "/network/" + bridge), null, HttpMethod.DELETE, remoteServer);
                    responses.add("Take back bridge " + bridge + ": " + describe(undo));
                    if (undo.getStatusCode().is2xxSuccessful()) {
                        created.remove(bridge);
                    }
                }
                labTracker.setBridges(created);
                bridgeMap.clear();
            } else if (!applyNetworkChanges(remoteServer, responses)) {
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
            NodeNetwork network = readNetwork(remoteServer);
            Set<String> existing = network.interfaces().stream()
                    .map(ProxmoxNetwork::getIface)
                    .collect(Collectors.toSet());
            List<String> present = bridges.stream().filter(existing::contains).toList();
            // A removal still pending from an earlier attempt needs the reload too
            boolean pending = touchedInterfaces(network.changes()).stream().anyMatch(bridges::contains);
            if (present.isEmpty() && !pending) {
                return true;
            }
            String problem = otherNetworkChanges(remoteServer, network.changes(), bridges);
            if (problem != null) {
                responses.add(problem);
                return false;
            }
            boolean ok = true;
            for (String bridge : present) {
                ResponseEntity<ProxmoxActionResponse> response = sendProxmoxRequest(
                        nodeApi(remoteServer, "/network/" + bridge), null, HttpMethod.DELETE, remoteServer);
                responses.add("Remove bridge " + bridge + ": " + describe(response));
                ok &= response.getStatusCode().is2xxSuccessful();
            }
            problem = otherNetworkChanges(remoteServer, readNetwork(remoteServer).changes(), bridges);
            if (problem != null) {
                responses.add(problem);
                return false;
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
                labTracker.setVms(new ArrayList<>(keepLabDetails(labTracker.getVms(), current)));
            }
        } catch (Exception e) {
            LOGGER.warn("Could not refresh VMs for lab tracker {}: {}", labTracker.getId(), e.getMessage());
        }
    }

    /** The tracked copy of a clone: its role, login user and whether its guest agent is watched. */
    static ProxmoxVM labCopy(int vmid, ProxmoxVM template, ProxmoxVMConfig source) {
        ProxmoxVM copy = new ProxmoxVM(vmid, template);
        copy.setTemplate(0);
        if (source.hasCiuser()) {
            copy.setCiuser(source.getCiuser());
        }
        if (source.isAgentEnabled()) {
            copy.setGuestAgent(true);
            copy.setReady(false);
        }
        if (source.isBastion()) {
            copy.setRole(ProxmoxVM.BASTION);
            String uplink = source.getNetworks().get(ProxmoxVMConfig.UPLINK_NIC);
            copy.setUplinkBridge(uplink != null ? bridgeOf(uplink) : null);
            copy.setLabSubnet(source.getIpconfigs().entrySet().stream()
                    .filter(entry -> !source.isUplink("net" + entry.getKey().substring("ipconfig".length())))
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> staticSubnet(entry.getValue()))
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null));
        }
        return copy;
    }

    /**
     * A clone's NICs: each moves to its lab bridge except a bastion's uplink, and none keeps the
     * template's MAC, so PVE generates a fresh one per clone.
     */
    static Map<String, Object> labNetworkConfig(ProxmoxVMConfig source, Map<String, String> bridgeMap) {
        Map<String, Object> config = new TreeMap<>();
        source.getNetworks().forEach((nic, value) -> {
            String fresh = withoutMac(value);
            String bridge = bridgeOf(fresh);
            if (bridge != null && !source.isUplink(nic)) {
                // Never leave a lab NIC on the template's bridge
                if (!bridgeMap.containsKey(bridge)) {
                    throw new IllegalStateException("No lab bridge for " + nic + " on " + bridge);
                }
                fresh = BRIDGE.matcher(fresh).replaceFirst("bridge=" + Matcher.quoteReplacement(bridgeMap.get(bridge)));
            }
            config.put(nic, fresh);
        });
        config.put("agent", "1");
        return config;
    }

    /** "virtio=BC:24:11:D1:F1:8C,bridge=vmbr0,firewall=1" becomes "virtio,bridge=vmbr0,firewall=1". */
    static String withoutMac(String nic) {
        List<String> kept = new ArrayList<>();
        for (String part : nic.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals("macaddr")) {
                continue;
            }
            kept.add(kv.length == 2 && MAC.matcher(kv[1].trim()).matches() ? kv[0].trim() : part);
        }
        return String.join(",", kept);
    }

    static String bridgeOf(String nic) {
        Matcher matcher = BRIDGE.matcher(nic);
        return matcher.find() ? matcher.group(1) : null;
    }

    static String macOf(String nic) {
        for (String part : nic.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && MAC.matcher(kv[1].trim()).matches()) {
                return kv[1].trim().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    /** "ip=172.16.0.1/24,gw=..." gives 172.16.0.0/24; dhcp and IPv6 give null. */
    static String staticSubnet(String ipconfig) {
        Matcher matcher = STATIC_IP.matcher(ipconfig);
        return matcher.find() ? subnet(matcher.group(1), Integer.parseInt(matcher.group(2))) : null;
    }

    static String subnet(String ip, int prefix) {
        if (prefix < 0 || prefix > 32) {
            return null;
        }
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            return null;
        }
        long address = 0;
        for (String octet : octets) {
            address = (address << 8) | (Integer.parseInt(octet) & 0xff);
        }
        long mask = prefix == 0 ? 0 : (0xffffffffL << (32 - prefix)) & 0xffffffffL;
        long network = address & mask;
        return String.format("%d.%d.%d.%d/%d", network >> 24 & 0xff, network >> 16 & 0xff, network >> 8 & 0xff,
                network & 0xff, prefix);
    }

    /** Four groups of four from an alphabet without look-alikes: about 80 bits. */
    static String newPassword() {
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) {
                password.append('-');
            }
            password.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    /** Reads the MACs PVE generated for a clone, to find its interfaces in the guest agent's answer. */
    private boolean readMacs(RemoteServer remoteServer, ProxmoxVM vm, List<String> responses) {
        try {
            Map<String, String> macs = new TreeMap<>();
            getVmConfigById(remoteServer, vm.getVmid()).getNetworks().forEach((nic, value) -> {
                String mac = macOf(value);
                if (mac != null) {
                    macs.put(nic, mac);
                }
            });
            vm.setMacs(macs);
            return true;
        } catch (RuntimeException e) {
            if (responses != null) {
                responses.add("Could not read the MACs of " + vm.getVmid() + ": " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Waits up to {@link #GUEST_TIMEOUT} for every bastion's address. A lab without one gets a single
     * readiness pass; either way the minute cron fills in what is still missing.
     */
    private void awaitGuests(LabTracker labTracker, RemoteServer remoteServer, List<String> responses) {
        List<ProxmoxVM> vms = labTracker.getVms();
        long deadline = System.nanoTime() + guestTimeout.toNanos();
        while (true) {
            updateGuests(remoteServer, vms);
            boolean waiting = vms.stream().anyMatch(vm -> vm.isBastion() && vm.getAccessAddress() == null);
            if (!waiting || System.nanoTime() >= deadline || !sleep(guestPollMillis)) {
                break;
            }
        }
        vms.stream().filter(ProxmoxVM::isBastion).forEach(vm -> responses.add(vm.getAccessAddress() != null
                ? "Bastion " + vm.getName() + " is at " + vm.getAccessAddress()
                : "Bastion " + vm.getName() + " has no address yet; it shows once its guest agent answers"));
    }

    /**
     * Fresh status reads with what the tracker learned about each VM, then one pass over the guest
     * agents. For the minute cron.
     */
    public List<ProxmoxVM> refreshLabVMs(RemoteServer remoteServer, List<ProxmoxVM> tracked, List<ProxmoxVM> current) {
        List<ProxmoxVM> vms = keepLabDetails(tracked, current);
        updateGuests(remoteServer, vms);
        return vms;
    }

    private static List<ProxmoxVM> keepLabDetails(List<ProxmoxVM> tracked, List<ProxmoxVM> current) {
        Map<Integer, ProxmoxVM> byId = new HashMap<>();
        tracked.forEach(vm -> byId.put(vm.getVmid(), vm));
        return current.stream().map(vm -> vm.keepLabDetails(byId.get(vm.getVmid()))).collect(Collectors.toList());
    }

    /**
     * Asks the guest agent of each running VM that is not ready yet, and of each bastion still without
     * an address. An answer means ready; a bastion's address is the IPv4 on the interface whose MAC is
     * its uplink's.
     */
    void updateGuests(RemoteServer remoteServer, List<ProxmoxVM> vms) {
        for (ProxmoxVM vm : vms) {
            boolean watched = vm.isGuestAgent() && !Boolean.TRUE.equals(vm.getReady());
            boolean needsAddress = vm.isBastion() && vm.getAccessAddress() == null;
            if (!"running".equals(vm.getStatus()) || (!watched && !needsAddress)) {
                continue;
            }
            List<Map<String, Object>> interfaces = guestInterfaces(remoteServer, vm.getVmid());
            if (vm.isGuestAgent()) {
                vm.setReady(interfaces != null);
            }
            if (interfaces == null || !vm.isBastion()) {
                continue;
            }
            if ((vm.getMacs() == null || vm.getMacs().isEmpty()) && !readMacs(remoteServer, vm, null)) {
                continue;
            }
            Map<String, Object> uplink = ipv4(interfaces, vm.getMacs().get(ProxmoxVMConfig.UPLINK_NIC));
            if (uplink != null) {
                vm.setAccessAddress(String.valueOf(uplink.get("ip-address")));
            }
            // The lab subnet hint: the first other NIC with an IPv4
            vm.getMacs().entrySet().stream()
                    .filter(entry -> !ProxmoxVMConfig.UPLINK_NIC.equals(entry.getKey()))
                    .map(entry -> ipv4(interfaces, entry.getValue()))
                    .filter(address -> address != null && address.get("prefix") instanceof Number)
                    .map(address -> subnet(String.valueOf(address.get("ip-address")), ((Number) address.get("prefix")).intValue()))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .ifPresent(vm::setLabSubnet);
        }
    }

    /** GET …/agent/network-get-interfaces; null while the agent does not answer. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> guestInterfaces(RemoteServer remoteServer, int vmid) {
        try {
            Object result = getData(remoteServer, nodeApi(remoteServer, "/qemu/" + vmid + "/agent/network-get-interfaces"))
                    .get("result");
            return result instanceof List<?> list ? (List<Map<String, Object>>) list : null;
        } catch (RestClientException e) {
            LOGGER.debug("Guest agent of VM {} on {} did not answer: {}", vmid, remoteServer.getName(), e.getMessage());
            return null;
        }
    }

    /** The first usable IPv4 of the interface with this MAC. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> ipv4(List<Map<String, Object>> interfaces, String mac) {
        if (mac == null) {
            return null;
        }
        for (Map<String, Object> iface : interfaces) {
            if (!mac.equalsIgnoreCase(String.valueOf(iface.get("hardware-address")))
                    || !(iface.get("ip-addresses") instanceof List<?> addresses)) {
                continue;
            }
            for (Object entry : addresses) {
                if (entry instanceof Map<?, ?> address && "ipv4".equals(address.get("ip-address-type"))) {
                    String ip = String.valueOf(address.get("ip-address"));
                    if (!ip.startsWith("127.") && !ip.startsWith("169.254.")) {
                        return (Map<String, Object>) address;
                    }
                }
            }
        }
        return null;
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

    /** Who a copy is for: its team, or "Shared" for a lab's one shared copy. */
    static String ownerLabel(LabTracker labTracker) {
        return labTracker.getLabOwner() != null ? labTracker.getLabOwner().getName() : "Shared";
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
