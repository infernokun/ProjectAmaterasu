package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.utils.AESUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Only a VM Proxmox says does not exist counts as gone; any other failure leaves the lab as it is. */
class ProxmoxServiceTeardownTest {
    static final String NODE = "https://10.0.0.205:8006/api2/json/nodes/proxmox2";

    private MockRestServiceServer node;
    private ProxmoxService proxmox;
    private RemoteServer server;
    private LabTracker tracker;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        node = MockRestServiceServer.bindTo(restTemplate).build();
        AESUtil aes = mock(AESUtil.class);
        when(aes.decrypt("encrypted")).thenReturn("amaterasu@pve!labs=secret");
        proxmox = new ProxmoxService(aes, restTemplate);
        server = RemoteServer.builder().name("pve-a").ipAddress("10.0.0.205").nodeName("proxmox2")
                .serverType(ServerType.PROXMOX).apiToken("encrypted").build();
        server.setId("server-1");
        tracker = tracker(server);
    }

    static LabTracker tracker(RemoteServer server) {
        Lab lab = Lab.builder().name("Pivot").labType(LabType.VIRTUAL_MACHINE).vmIds(List.of(9000)).build();
        LabTracker tracker = LabTracker.builder().labStarted(lab).labOwner(Team.builder().name("Red Fox").build())
                .remoteServer(server)
                .vms(new ArrayList<>(List.of(vm(105, "running"), vm(106, "running"))))
                .bridges(new ArrayList<>(List.of("abr0")))
                .build();
        tracker.setId("lt1");
        return tracker;
    }

    static ProxmoxVM vm(int vmid, String status) {
        return ProxmoxVM.builder().vmid(vmid).name("vm" + vmid).status(status).build();
    }

    static String vmStatus(int vmid, String status) {
        return "{\"data\":{\"vmid\":" + vmid + ",\"name\":\"vm" + vmid + "\",\"status\":\"" + status + "\",\"qmpstatus\":\""
                + status + "\",\"uptime\":0,\"cpus\":2,\"maxmem\":2147483648}}";
    }

    static ResponseCreator doesNotExist(int vmid) {
        return withServerError().body("{\"data\":null,\"message\":\"Configuration file 'nodes/proxmox2/qemu-server/" + vmid
                + ".conf' does not exist\\n\"}").contentType(MediaType.APPLICATION_JSON);
    }

    private void expectStatus(int vmid, ResponseCreator response) {
        node.expect(requestTo(NODE + "/qemu/" + vmid + "/status/current")).andExpect(method(HttpMethod.GET)).andRespond(response);
    }

    private void assertUntouched(LabActionResult result) {
        node.verify();
        assertThat(result.isSuccessful()).isFalse();
        assertThat(tracker.getVms()).extracting(ProxmoxVM::getVmid).containsExactly(105, 106);
        assertThat(tracker.getBridges()).containsExactly("abr0");
    }

    @Test
    void aRejectedTokenFailsTeardownAndKeepsTheVmsAndBridges() {
        expectStatus(105, withStatus(HttpStatus.UNAUTHORIZED).body("{\"data\":null}"));

        LabActionResult result = proxmox.teardownProxmoxLab(tracker, server);

        assertUntouched(result);
        assertThat(result.getOutput()).startsWith("Teardown failed: Could not read VM 105 on pve-a: 401");
    }

    @Test
    void aMissingPrivilegeFailsTeardown() {
        expectStatus(105, vmStatusOk(105));
        expectStatus(106, withStatus(HttpStatus.FORBIDDEN)
                .body("{\"data\":null,\"message\":\"Permission check failed (/vms/106, VM.Audit)\\n\"}")
                .contentType(MediaType.APPLICATION_JSON));

        LabActionResult result = proxmox.teardownProxmoxLab(tracker, server);

        assertUntouched(result);
        assertThat(result.getOutput()).contains("VM 106").contains("403 Permission check failed (/vms/106, VM.Audit)");
    }

    @Test
    void aNodeThatCannotBeReachedFailsTeardown() {
        // pveproxy answers 595 when it cannot reach the node it proxies to
        expectStatus(105, withStatus(HttpStatusCode.valueOf(595)).body("{\"data\":null}"));

        assertUntouched(proxmox.teardownProxmoxLab(tracker, server));
    }

    @Test
    void aTimeoutFailsTeardown() {
        expectStatus(105, withException(new SocketTimeoutException("Read timed out")));

        LabActionResult result = proxmox.teardownProxmoxLab(tracker, server);

        assertUntouched(result);
        assertThat(result.getOutput()).contains("no answer from 10.0.0.205");
    }

    @Test
    void aRefusedConnectionFailsPauseAndResume() {
        expectStatus(105, withException(new IOException("Connection refused")));
        assertUntouched(proxmox.pauseProxmoxLab(tracker, server));

        node.reset();
        expectStatus(105, withStatus(HttpStatus.FORBIDDEN).body("{\"data\":null}"));
        LabActionResult resumed = proxmox.resumeProxmoxLab(tracker, server);
        assertUntouched(resumed);
        assertThat(resumed.getOutput()).startsWith("Resume failed: Could not read VM 105");
    }

    @Test
    void vmsProxmoxSaysDoNotExistAreGoneAndTheirBridgesRemoved() {
        expectStatus(105, doesNotExist(105));
        expectStatus(106, withStatus(HttpStatus.NOT_FOUND).body("{\"data\":null}"));
        node.expect(requestTo(NODE + "/network")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(ProxmoxFixtures.network(null, "abr0"), MediaType.APPLICATION_JSON));
        node.expect(requestTo(NODE + "/network/abr0")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"data\":null}", MediaType.APPLICATION_JSON));
        node.expect(requestTo(NODE + "/network")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(ProxmoxFixtures.network(ProxmoxFixtures.diff(ProxmoxFixtures.removedBridge("abr0"))),
                        MediaType.APPLICATION_JSON));
        expectReload(node);

        LabActionResult result = proxmox.teardownProxmoxLab(tracker, server);

        node.verify();
        assertThat(result.isSuccessful()).isTrue();
        assertThat(tracker.getVms()).isEmpty();
        assertThat(tracker.getBridges()).isEmpty();
    }

    static void expectReload(MockRestServiceServer node) {
        String upid = "UPID:proxmox2:0001:0002:0003:srvreload:networking:amaterasu@pve!labs:";
        node.expect(requestTo(NODE + "/network")).andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"data\":\"" + upid + "\"}", MediaType.APPLICATION_JSON));
        node.expect(requestTo(NODE + "/tasks/" + upid + "/status"))
                .andRespond(withSuccess("{\"data\":{\"status\":\"stopped\",\"exitstatus\":\"OK\"}}", MediaType.APPLICATION_JSON));
    }

    private static ResponseCreator vmStatusOk(int vmid) {
        return withSuccess(vmStatus(vmid, "running"), MediaType.APPLICATION_JSON);
    }

    @Test
    void onlyNotFoundAndDoesNotExistMeanGone() {
        assertThat(ProxmoxService.vmDoesNotExist(new org.springframework.web.client.HttpServerErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Configuration file 'nodes/proxmox2/qemu-server/105.conf' does not exist"))).isTrue();
        assertThat(ProxmoxService.vmDoesNotExist(new org.springframework.web.client.HttpServerErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR, "VM is locked (clone)"))).isFalse();
        assertThat(ProxmoxService.vmDoesNotExist(new org.springframework.web.client.HttpClientErrorException(
                HttpStatus.FORBIDDEN, "does not exist"))).isFalse();
    }
}
