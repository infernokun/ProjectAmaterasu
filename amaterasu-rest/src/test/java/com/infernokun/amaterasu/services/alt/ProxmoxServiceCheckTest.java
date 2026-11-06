package com.infernokun.amaterasu.services.alt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.models.ServerCheck;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.utils.AESUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** "Connected" means the token works on that node and holds what Amaterasu needs there. */
class ProxmoxServiceCheckTest {
    static final String API = "https://10.0.0.205:8006/api2/json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private MockRestServiceServer node;
    private ProxmoxService proxmox;
    private RemoteServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        node = MockRestServiceServer.bindTo(restTemplate).build();
        AESUtil aes = mock(AESUtil.class);
        proxmox = new ProxmoxService(aes, restTemplate);
        // Not saved yet, as in the add dialog: the token is sent as typed
        server = RemoteServer.builder().name("pve-a").ipAddress("10.0.0.205").nodeName("proxmox2")
                .serverType(ServerType.PROXMOX).apiToken("amaterasu@pve!labs=secret").build();
    }

    static String permissions(Map<String, List<String>> byPath) throws Exception {
        Map<String, Map<String, Integer>> data = new LinkedHashMap<>();
        byPath.forEach((path, privileges) -> {
            Map<String, Integer> held = new LinkedHashMap<>();
            privileges.forEach(privilege -> held.put(privilege, 1));
            data.put(path, held);
        });
        return JSON.writeValueAsString(Map.of("data", data));
    }

    private void expectVersion() {
        node.expect(requestTo(API + "/version")).andExpect(header("Authorization", "PVEAPIToken amaterasu@pve!labs=secret"))
                .andRespond(withSuccess(ProxmoxFixtures.body("version.json"), MediaType.APPLICATION_JSON));
    }

    private void expectNodeStatus() {
        node.expect(requestTo(API + "/nodes/proxmox2/status"))
                .andRespond(withSuccess(ProxmoxFixtures.body("node-status.json"), MediaType.APPLICATION_JSON));
    }

    private void expectPermissions(String body) {
        node.expect(requestTo(API + "/access/permissions")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void theRealNodesTokenIsUsableForLabs() {
        expectVersion();
        expectNodeStatus();
        expectPermissions(ProxmoxFixtures.body("permissions.json"));

        ProxmoxService.NodeCheck check = proxmox.checkNode(server);

        node.verify();
        assertThat(check.result()).isEqualTo(ServerCheck.ok());
        assertThat(check.version()).isEqualTo("9.2.2");
        assertThat(check.status()).containsKey("memory");
    }

    @Test
    void aRejectedTokenSaysWhichFormatToUse() {
        node.expect(requestTo(API + "/version")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(proxmox.checkConnection(server)).isEqualTo(ServerCheck.failed(
                "Proxmox rejected the token. Use the full USER@REALM!TOKENID=SECRET"));
    }

    @Test
    void aHostThatDoesNotAnswer() {
        node.expect(requestTo(API + "/version")).andRespond(withException(new ConnectException("Connection refused")));

        ServerCheck check = proxmox.checkConnection(server);

        assertThat(check.connected()).isFalse();
        assertThat(check.reason()).startsWith("No answer from 10.0.0.205:8006");
    }

    @Test
    void aWrongNodeNameFailsEvenThoughTheTokenWorks() {
        server.setNodeName("pve9");
        expectVersion();
        node.expect(requestTo(API + "/nodes/pve9/status")).andRespond(withServerError()
                .body("{\"data\":null,\"message\":\"hostname lookup 'pve9' failed - failed to get address info for: pve9: Name or service not known\\n\"}"));

        assertThat(proxmox.checkConnection(server)).isEqualTo(ServerCheck.failed("No node called pve9 on that host"));
    }

    @Test
    void aReadOnlyTokenConnectsWithAWarning() throws Exception {
        expectVersion();
        expectNodeStatus();
        expectPermissions(permissions(Map.of(
                "/vms", List.of("VM.Audit"),
                "/nodes/proxmox2", List.of("Sys.Audit"),
                "/storage", List.of("Datastore.Audit"))));

        assertThat(proxmox.checkConnection(server)).isEqualTo(ServerCheck.warning("Can list VMs but can't deploy labs: missing "
                + "VM.Clone, VM.Allocate, VM.Config.Network, VM.Config.Options, VM.PowerMgmt, VM.Console on /vms; "
                + "Datastore.AllocateSpace on /storage; Sys.Modify on /nodes/proxmox2"));
    }

    @Test
    void privilegesAreReadPerPathNotInheritedFromTheRoot() throws Exception {
        expectVersion();
        expectNodeStatus();
        List<String> everything = List.of("VM.Audit", "VM.Clone", "VM.Allocate", "VM.Config.Network", "VM.Config.Options",
                "VM.PowerMgmt", "VM.Console", "Datastore.AllocateSpace", "Sys.Audit", "Sys.Modify");
        // An ACL on /vms replaces what / grants there
        expectPermissions(permissions(Map.of(
                "/", everything,
                "/vms", List.of("VM.Audit", "VM.PowerMgmt"),
                "/nodes/proxmox2", everything,
                "/storage", everything)));

        ServerCheck check = proxmox.checkConnection(server);

        assertThat(check.connected()).isTrue();
        assertThat(check.warning()).isEqualTo("Can list VMs but can't deploy labs: missing "
                + "VM.Clone, VM.Allocate, VM.Config.Network, VM.Config.Options, VM.Console on /vms");
    }

    @Test
    void aTokenWithNoPrivilegesIsNotUsable() throws Exception {
        expectVersion();
        node.expect(requestTo(API + "/nodes/proxmox2/status")).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .body("{\"data\":null,\"message\":\"Permission check failed (/nodes/proxmox2, Sys.Audit)\\n\"}"));
        expectPermissions(permissions(Map.of("/vms", List.of(), "/nodes/proxmox2", List.of(), "/storage", List.of())));

        assertThat(proxmox.checkConnection(server)).isEqualTo(ServerCheck.failed(
                "The Proxmox token is missing VM.Audit on /vms; Sys.Audit on /nodes/proxmox2"));
    }

    @Test
    void aPathTheListingLeavesOutIsAskedForDirectly() throws Exception {
        expectVersion();
        expectNodeStatus();
        expectPermissions(permissions(Map.of(
                "/vms", List.of("VM.Audit", "VM.Clone", "VM.Allocate", "VM.Config.Network", "VM.Config.Options", "VM.PowerMgmt", "VM.Console"),
                "/nodes/proxmox2", List.of("Sys.Audit", "Sys.Modify"))));
        // The value is the propagate flag: 0 still grants the privilege at that path
        node.expect(requestTo(API + "/access/permissions?path=/storage"))
                .andRespond(withSuccess("{\"data\":{\"/storage\":{\"Datastore.AllocateSpace\":0}}}", MediaType.APPLICATION_JSON));

        assertThat(proxmox.checkConnection(server)).isEqualTo(ServerCheck.ok());
        node.verify();
    }
}
