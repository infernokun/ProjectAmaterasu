package com.infernokun.amaterasu.services.alt;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVMConfig;
import com.infernokun.amaterasu.utils.AESUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * A lab of tpl-bastion (9003) and tpl-target (9001) as they are on proxmox2: the bastion's uplink stays on
 * vmbr0, every NIC gets a fresh MAC, the copy's password is set before the first start, and the bastion's
 * address comes from its guest agent by MAC.
 */
class ProxmoxServiceBastionTest {
    static final String API = "https://10.0.0.205:8006/api2/json";
    static final String NODE = API + "/nodes/proxmox2";
    static final String UPID = "UPID:proxmox2:0001:0002:0003:qmtask:105:amaterasu@pve!labs:";
    static final Pattern ANY_MAC = Pattern.compile("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}");
    // The MACs PVE generated for the bastion clone in the live check
    static final String CLONE_NET0 = "bc:24:11:fa:35:62";
    static final String CLONE_NET1 = "bc:24:11:c3:00:47";
    static final String AGENT_NOT_RUNNING = "{\"data\":null,\"message\":\"QEMU guest agent is not running\\n\"}";

    private final ObjectMapper json = new ObjectMapper();
    private MockRestServiceServer node;
    private ProxmoxService proxmox;
    private AESUtil aes;
    private RemoteServer server;
    private LabTracker tracker;
    private final List<String> configPuts = new ArrayList<>();
    private ListAppender<ILoggingEvent> logs;
    private Level appLevel;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        node = MockRestServiceServer.bindTo(restTemplate).build();
        AmaterasuConfig config = new AmaterasuConfig();
        config.setEncryptionKey("test-encryption-key-at-least-32-characters");
        aes = new AESUtil(config);
        proxmox = new ProxmoxService(aes, restTemplate);
        proxmox.guestWait(Duration.ofSeconds(5), 10);
        server = RemoteServer.builder().name("pve-a").ipAddress("10.0.0.205").nodeName("proxmox2")
                .serverType(ServerType.PROXMOX).apiToken(aes.encrypt("amaterasu@pve!labs=secret")).build();
        server.setId("server-1");
        Lab lab = Lab.builder().name("Pivot").labType(LabType.VIRTUAL_MACHINE).vmIds(List.of(9003, 9001)).build();
        tracker = LabTracker.builder().labStarted(lab).labOwner(Team.builder().name("Red Fox").build())
                .remoteServer(server).build();
        tracker.setId("lt1");

        logs = new ListAppender<>();
        logs.start();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(logs);
        Logger app = (Logger) LoggerFactory.getLogger("com.infernokun");
        appLevel = app.getLevel();
        app.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
        ((Logger) LoggerFactory.getLogger("com.infernokun")).setLevel(appLevel);
    }

    private RequestMatcher capture(List<String> into) {
        return request -> into.add(((MockClientHttpRequest) request).getBodyAsString());
    }

    private static String vm(int vmid, String name, String status, int template) {
        return "{\"vmid\":" + vmid + ",\"name\":\"" + name + "\",\"status\":\"" + status + "\",\"template\":" + template
                + ",\"uptime\":0,\"cpus\":1,\"maxmem\":536870912,\"mem\":0,\"cpu\":0}";
    }

    private void ok(String url, HttpMethod method, String body) {
        node.expect(requestTo(url)).andExpect(method(method)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void task() {
        ok(NODE + "/tasks/" + UPID + "/status", HttpMethod.GET, "{\"data\":{\"status\":\"stopped\",\"exitstatus\":\"OK\"}}");
    }

    /** Every call of the deploy up to the guest agent, in order. */
    private void expectDeployUntilTheAgent() {
        ok(NODE + "/qemu", HttpMethod.GET, "{\"data\":[" + vm(9001, "tpl-target", "stopped", 1) + ","
                + vm(9003, "tpl-bastion", "stopped", 1) + "]}");
        ok(NODE + "/qemu/9003/config", HttpMethod.GET, ProxmoxFixtures.body("bastion-template-config.json"));
        ok(NODE + "/qemu/9001/config", HttpMethod.GET, ProxmoxFixtures.body("target-template-config.json"));

        // Both templates sit on vmbr0, so the lab gets one bridge
        ok(NODE + "/network", HttpMethod.GET, ProxmoxFixtures.network(null));
        ok(NODE + "/network", HttpMethod.POST, "{\"data\":null}");
        ok(NODE + "/network", HttpMethod.GET,
                ProxmoxFixtures.network(ProxmoxFixtures.diff(ProxmoxFixtures.addedBridge("abr0")), "abr0"));
        ProxmoxServiceTeardownTest.expectReload(node);

        ok(API + "/cluster/nextid", HttpMethod.GET, "{\"data\":\"105\"}");
        ok(NODE + "/qemu/9003/clone", HttpMethod.POST, "{\"data\":\"" + UPID + "\"}");
        task();
        node.expect(requestTo(NODE + "/qemu/105/config")).andExpect(method(HttpMethod.PUT)).andExpect(capture(configPuts))
                .andRespond(withSuccess("{\"data\":null}", MediaType.APPLICATION_JSON));
        node.expect(requestTo(NODE + "/qemu/105/config")).andExpect(method(HttpMethod.PUT)).andExpect(capture(configPuts))
                .andRespond(withSuccess("{\"data\":null}", MediaType.APPLICATION_JSON));
        ok(NODE + "/qemu/105/config", HttpMethod.GET, ProxmoxFixtures.body("bastion-clone-config.json"));

        ok(API + "/cluster/nextid", HttpMethod.GET, "{\"data\":\"106\"}");
        ok(NODE + "/qemu/9001/clone", HttpMethod.POST, "{\"data\":\"" + UPID + "\"}");
        task();
        node.expect(requestTo(NODE + "/qemu/106/config")).andExpect(method(HttpMethod.PUT)).andExpect(capture(configPuts))
                .andRespond(withSuccess("{\"data\":null}", MediaType.APPLICATION_JSON));

        // Only after both configs and the password
        ok(NODE + "/qemu/105/status/start", HttpMethod.POST, "{\"data\":\"" + UPID + "\"}");
        task();
        ok(NODE + "/qemu/106/status/start", HttpMethod.POST, "{\"data\":\"" + UPID + "\"}");
        task();
        ok(NODE + "/qemu", HttpMethod.GET, "{\"data\":[" + vm(105, "Red-Fox-Pivot-tpl-bastion", "running", 0) + ","
                + vm(106, "Red-Fox-Pivot-tpl-target", "running", 0) + "]}");
        ok(NODE + "/qemu/105/status/current", HttpMethod.GET,
                "{\"data\":" + vm(105, "Red-Fox-Pivot-tpl-bastion", "running", 0) + "}");
        ok(NODE + "/qemu/106/status/current", HttpMethod.GET,
                "{\"data\":" + vm(106, "Red-Fox-Pivot-tpl-target", "running", 0) + "}");
    }

    /** The live agent answer, with the clone's MACs and eth1 listed before eth0. */
    @SuppressWarnings("unchecked")
    static String bastionInterfaces() throws Exception {
        Map<String, Object> body = new ObjectMapper().readValue(ProxmoxFixtures.body("agent-network-get-interfaces.json")
                .replace("bc:24:11:d1:f1:8c", CLONE_NET0).replace("bc:24:11:3d:72:44", CLONE_NET1), Map.class);
        List<Object> result = (List<Object>) ((Map<String, Object>) body.get("data")).get("result");
        result.add(1, result.remove(2));
        return new ObjectMapper().writeValueAsString(body);
    }

    static String targetInterfaces() {
        return "{\"data\":{\"result\":[{\"name\":\"eth0\",\"hardware-address\":\"bc:24:11:41:7e:09\",\"ip-addresses\":"
                + "[{\"ip-address\":\"172.16.0.20\",\"ip-address-type\":\"ipv4\",\"prefix\":24}]}]}}";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> put(int index) throws Exception {
        return json.readValue(configPuts.get(index), Map.class);
    }

    private ProxmoxVM vm(int vmid) {
        return tracker.getVms().stream().filter(machine -> machine.getVmid() == vmid).findFirst().orElseThrow();
    }

    @Test
    void theBastionUplinkStaysOnVmbr0AndEveryOtherNicMovesToTheLabBridgeWithoutAMac() throws Exception {
        expectDeployUntilTheAgent();
        ok(NODE + "/qemu/105/agent/network-get-interfaces", HttpMethod.GET, bastionInterfaces());
        ok(NODE + "/qemu/106/agent/network-get-interfaces", HttpMethod.GET, targetInterfaces());

        LabActionResult result = proxmox.deployProxmoxLab(tracker, server);

        node.verify();
        assertThat(result.isSuccessful()).as(result.getOutput()).isTrue();
        Map<String, Object> bastion = put(0);
        assertThat(bastion).containsEntry("net0", "virtio,bridge=vmbr0").containsEntry("net1", "virtio,bridge=abr0");
        assertThat(put(2)).containsEntry("net0", "virtio,bridge=abr0");
        for (int i : List.of(0, 2)) {
            put(i).entrySet().stream().filter(entry -> entry.getKey().startsWith("net"))
                    .forEach(entry -> assertThat(ANY_MAC.matcher(String.valueOf(entry.getValue())).find())
                            .as(entry.getKey() + " " + entry.getValue()).isFalse());
        }
        assertThat(tracker.getBridges()).containsExactly("abr0");
        assertThat(vm(105).getRole()).isEqualTo("bastion");
        assertThat(vm(105).getUplinkBridge()).isEqualTo("vmbr0");
        assertThat(vm(106).getRole()).isNull();
    }

    @Test
    void aLabsSharedCopyHasNoTeamAndStillDeploys() throws Exception {
        tracker.setLabOwner(null);
        tracker.setRoomId("lab-1");
        expectDeployUntilTheAgent();
        ok(NODE + "/qemu/105/agent/network-get-interfaces", HttpMethod.GET, bastionInterfaces());
        ok(NODE + "/qemu/106/agent/network-get-interfaces", HttpMethod.GET, targetInterfaces());

        LabActionResult result = proxmox.deployProxmoxLab(tracker, server);

        node.verify();
        assertThat(result.isSuccessful()).as(result.getOutput()).isTrue();
        assertThat(ProxmoxService.ownerLabel(tracker)).isEqualTo("Shared");
    }

    @Test
    void thePasswordIsSetBeforeTheFirstStartStoredEncryptedAndNeverLogged() throws Exception {
        expectDeployUntilTheAgent();
        ok(NODE + "/qemu/105/agent/network-get-interfaces", HttpMethod.GET, bastionInterfaces());
        ok(NODE + "/qemu/106/agent/network-get-interfaces", HttpMethod.GET, targetInterfaces());

        LabActionResult result = proxmox.deployProxmoxLab(tracker, server);

        // The ordered expectations put this PUT before either start
        node.verify();
        Map<String, Object> secret = put(1);
        assertThat(secret).containsOnlyKeys("cipassword");
        String password = String.valueOf(secret.get("cipassword"));
        assertThat(password).matches("[a-z2-9]{4}(-[a-z2-9]{4}){3}");
        // tpl-target has no cloud-init user, so it gets no password
        assertThat(put(2)).doesNotContainKey("cipassword");

        assertThat(tracker.getAccessPassword()).isNotBlank().doesNotContain(password);
        assertThat(aes.decrypt(tracker.getAccessPassword())).isEqualTo(password);
        assertThat(vm(105).getCiuser()).isEqualTo("player");
        assertThat(vm(106).getCiuser()).isNull();

        assertThat(result.getOutput()).contains("Password for player on 105: 200").doesNotContain(password);
        assertThat(logs.list).noneMatch(event -> event.getFormattedMessage().contains(password));
        assertThat(json.writeValueAsString(tracker)).doesNotContain(password).doesNotContain("accessPassword");
    }

    @Test
    void aRejectedPasswordFailsTheDeployAndIsNotLogged() {
        ok(NODE + "/qemu", HttpMethod.GET, "{\"data\":[" + vm(9001, "tpl-target", "stopped", 1) + ","
                + vm(9003, "tpl-bastion", "stopped", 1) + "]}");
        ok(NODE + "/qemu/9003/config", HttpMethod.GET, ProxmoxFixtures.body("bastion-template-config.json"));
        ok(NODE + "/qemu/9001/config", HttpMethod.GET, ProxmoxFixtures.body("target-template-config.json"));
        ok(NODE + "/network", HttpMethod.GET, ProxmoxFixtures.network(null));
        ok(NODE + "/network", HttpMethod.POST, "{\"data\":null}");
        ok(NODE + "/network", HttpMethod.GET,
                ProxmoxFixtures.network(ProxmoxFixtures.diff(ProxmoxFixtures.addedBridge("abr0")), "abr0"));
        ProxmoxServiceTeardownTest.expectReload(node);
        ok(API + "/cluster/nextid", HttpMethod.GET, "{\"data\":\"105\"}");
        ok(NODE + "/qemu/9003/clone", HttpMethod.POST, "{\"data\":\"" + UPID + "\"}");
        task();
        node.expect(requestTo(NODE + "/qemu/105/config")).andExpect(method(HttpMethod.PUT)).andExpect(capture(configPuts))
                .andRespond(withSuccess("{\"data\":null}", MediaType.APPLICATION_JSON));
        node.expect(requestTo(NODE + "/qemu/105/config")).andExpect(method(HttpMethod.PUT)).andExpect(capture(configPuts))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":null,\"message\":\"Permission check failed (/vms/105, VM.Config.Cloudinit)\\n\"}"));
        ok(API + "/cluster/nextid", HttpMethod.GET, "{\"data\":\"106\"}");
        ok(NODE + "/qemu/9001/clone", HttpMethod.POST, "{\"data\":\"" + UPID + "\"}");
        task();
        node.expect(requestTo(NODE + "/qemu/106/config")).andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"data\":null}", MediaType.APPLICATION_JSON));
        // Nothing starts; the clones stay on the tracker for teardown
        ok(NODE + "/qemu/105/status/current", HttpMethod.GET, "{\"data\":" + vm(105, "b", "stopped", 0) + "}");
        ok(NODE + "/qemu/106/status/current", HttpMethod.GET, "{\"data\":" + vm(106, "t", "stopped", 0) + "}");

        LabActionResult result = proxmox.deployProxmoxLab(tracker, server);

        node.verify();
        String password = configPuts.get(1).replaceAll(".*\"cipassword\":\"([^\"]+)\".*", "$1");
        assertThat(password).matches("[a-z2-9]{4}(-[a-z2-9]{4}){3}");
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getOutput()).contains("Password for player on 105: 403").doesNotContain(password);
        assertThat(logs.list).anyMatch(event -> event.getFormattedMessage().contains("VM.Config.Cloudinit"));
        assertThat(logs.list).noneMatch(event -> event.getFormattedMessage().contains(password));
        assertThat(tracker.getVms()).extracting(ProxmoxVM::getVmid).containsExactly(105, 106);
    }

    @Test
    void theBastionAddressIsTheIpv4OnItsUplinkMac() throws Exception {
        expectDeployUntilTheAgent();
        ok(NODE + "/qemu/105/agent/network-get-interfaces", HttpMethod.GET, bastionInterfaces());
        ok(NODE + "/qemu/106/agent/network-get-interfaces", HttpMethod.GET, targetInterfaces());

        LabActionResult result = proxmox.deployProxmoxLab(tracker, server);

        node.verify();
        assertThat(vm(105).getMacs()).containsEntry("net0", CLONE_NET0).containsEntry("net1", CLONE_NET1);
        // eth1 (172.16.0.1) comes first in the answer; the uplink's MAC picks eth0
        assertThat(vm(105).getAccessAddress()).isEqualTo("10.0.0.42");
        assertThat(vm(105).getLabSubnet()).isEqualTo("172.16.0.0/24");
        assertThat(vm(105).getReady()).isTrue();
        assertThat(vm(106).getReady()).isTrue();
        assertThat(vm(106).getAccessAddress()).isNull();
        assertThat(result.getOutput()).contains("Bastion Red-Fox-Pivot-tpl-bastion is at 10.0.0.42");
    }

    @Test
    void anAgentThatDoesNotAnswerInTimeLeavesTheAddressPendingAndTheLabUp() {
        proxmox.guestWait(Duration.ofMillis(200), 20);
        expectDeployUntilTheAgent();
        node.expect(manyTimes(), requestTo(NODE + "/qemu/105/agent/network-get-interfaces"))
                .andRespond(withServerError().body(AGENT_NOT_RUNNING).contentType(MediaType.APPLICATION_JSON));
        node.expect(manyTimes(), requestTo(NODE + "/qemu/106/agent/network-get-interfaces"))
                .andRespond(withServerError().body(AGENT_NOT_RUNNING).contentType(MediaType.APPLICATION_JSON));

        LabActionResult result = proxmox.deployProxmoxLab(tracker, server);

        node.verify();
        assertThat(result.isSuccessful()).isTrue();
        assertThat(vm(105).getAccessAddress()).isNull();
        assertThat(vm(105).getReady()).isFalse();
        assertThat(vm(106).getReady()).isFalse();
        // The template's ipconfig1 stands in until the agent answers
        assertThat(vm(105).getLabSubnet()).isEqualTo("172.16.0.0/24");
        assertThat(result.getOutput()).contains("has no address yet");
    }

    @Test
    void teardownForgetsThePassword() {
        tracker.setVms(new ArrayList<>(List.of(ProxmoxVM.builder().vmid(105).status("stopped").build())));
        tracker.setBridges(new ArrayList<>());
        tracker.setAccessPassword(aes.encrypt("abcd-efgh-ijkm-npqr"));
        ok(NODE + "/qemu/105/status/current", HttpMethod.GET, "{\"data\":" + vm(105, "b", "stopped", 0) + "}");
        ok(NODE + "/qemu/105?purge=1&destroy-unreferenced-disks=1", HttpMethod.DELETE, "{\"data\":\"" + UPID + "\"}");
        task();

        assertThat(proxmox.teardownProxmoxLab(tracker, server).isSuccessful()).isTrue();
        assertThat(tracker.getAccessPassword()).isNull();
    }

    @Test
    void macsAreDroppedAndEveryOtherOptionKept() {
        assertThat(ProxmoxService.withoutMac("virtio=BC:24:11:D1:F1:8C,bridge=abr0,firewall=1"))
                .isEqualTo("virtio,bridge=abr0,firewall=1");
        assertThat(ProxmoxService.withoutMac("e1000=bc:24:11:3d:72:44,bridge=vmbr0,tag=20,rate=10,link_down=1"))
                .isEqualTo("e1000,bridge=vmbr0,tag=20,rate=10,link_down=1");
        assertThat(ProxmoxService.withoutMac("model=virtio,macaddr=BC:24:11:3D:72:44,bridge=vmbr0,queues=2"))
                .isEqualTo("model=virtio,bridge=vmbr0,queues=2");
        assertThat(ProxmoxService.withoutMac("virtio,bridge=vmbr0")).isEqualTo("virtio,bridge=vmbr0");
    }

    @Test
    void aLabNicWithoutALabBridgeFailsRatherThanStayOnTheTemplateBridge() {
        ProxmoxVMConfig bastion = new ProxmoxVMConfig();
        bastion.setTags("amaterasu;bastion");
        bastion.setDynamicProperties("net0", "virtio=BC:24:11:D1:F1:8C,bridge=vmbr0");
        bastion.setDynamicProperties("net1", "virtio=BC:24:11:3D:72:44,bridge=vmbr0,firewall=1");
        assertThat(ProxmoxService.labNetworkConfig(bastion, Map.of("vmbr0", "abr3")))
                .containsEntry("net0", "virtio,bridge=vmbr0").containsEntry("net1", "virtio,bridge=abr3,firewall=1");

        ProxmoxVMConfig target = new ProxmoxVMConfig();
        target.setDynamicProperties("net0", "virtio=BC:24:11:6E:0A:93,bridge=vmbr0");
        assertThatThrownBy(() -> ProxmoxService.labNetworkConfig(target, Map.of()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("net0 on vmbr0");
    }

    @Test
    void bastionTagsAgentFlagsAndSubnets() {
        ProxmoxVMConfig config = new ProxmoxVMConfig();
        config.setTags("amaterasu;bastion");
        assertThat(config.isBastion()).isTrue();
        config.setTags("amaterasu,Bastion");
        assertThat(config.isBastion()).isTrue();
        config.setTags("amaterasu;bastions");
        assertThat(config.isBastion()).isFalse();
        assertThat(config.isUplink("net0")).isFalse();

        config.setAgent("enabled=1,fstrim_cloned_disks=1");
        assertThat(config.isAgentEnabled()).isTrue();
        config.setAgent("0");
        assertThat(config.isAgentEnabled()).isFalse();
        config.setAgent("fstrim_cloned_disks=1,enabled=0");
        assertThat(config.isAgentEnabled()).isFalse();

        assertThat(ProxmoxService.staticSubnet("ip=172.16.0.1/24,gw=172.16.0.254")).isEqualTo("172.16.0.0/24");
        assertThat(ProxmoxService.staticSubnet("ip=dhcp")).isNull();
        assertThat(ProxmoxService.subnet("10.20.30.40", 20)).isEqualTo("10.20.16.0/20");
    }
}
