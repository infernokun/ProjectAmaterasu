package com.infernokun.amaterasu.services.cron;

import com.infernokun.amaterasu.components.LabActionGuard;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.services.alt.ProxmoxService;
import com.infernokun.amaterasu.services.entity.lab.LabTrackerService;
import com.infernokun.amaterasu.utils.AESUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The minute poll marks a lab DEGRADED only for VMs Proxmox says are gone, never for a failed read. */
class ProxmoxLabStatsServiceTest {
    private static final String NODE = "https://10.0.0.205:8006/api2/json/nodes/proxmox2";

    private MockRestServiceServer node;
    private LabTrackerService trackers;
    private ProxmoxLabStatsService stats;
    private LabTracker tracker;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        node = MockRestServiceServer.bindTo(restTemplate).build();
        AESUtil aes = mock(AESUtil.class);
        when(aes.decrypt("encrypted")).thenReturn("amaterasu@pve!labs=secret");
        trackers = mock(LabTrackerService.class);
        stats = new ProxmoxLabStatsService(new ProxmoxService(aes, restTemplate), trackers, new LabActionGuard());

        RemoteServer server = RemoteServer.builder().name("pve-a").ipAddress("10.0.0.205").nodeName("proxmox2")
                .serverType(ServerType.PROXMOX).apiToken("encrypted").build();
        server.setId("server-1");
        tracker = LabTracker.builder().labStatus(LabStatus.ACTIVE)
                .labStarted(Lab.builder().name("Pivot").labType(LabType.VIRTUAL_MACHINE).build())
                .labOwner(Team.builder().name("Red Fox").build()).remoteServer(server)
                .vms(new ArrayList<>(List.of(ProxmoxVM.builder().vmid(105).status("running").build(),
                        ProxmoxVM.builder().vmid(106).status("running").build())))
                .build();
        tracker.setId("lt1");
        when(trackers.findAllLabTrackers()).thenReturn(List.of(tracker));
    }

    private static String running(int vmid) {
        return "{\"data\":{\"vmid\":" + vmid + ",\"status\":\"running\"}}";
    }

    @Test
    void aFailedReadLeavesTheLabAlone() {
        node.expect(requestTo(NODE + "/qemu/105/status/current")).andRespond(withSuccess(running(105), MediaType.APPLICATION_JSON));
        node.expect(requestTo(NODE + "/qemu/106/status/current")).andRespond(withStatus(HttpStatusCode.valueOf(595)));

        stats.updateActiveProxmoxLabStats();

        node.verify();
        verify(trackers, never()).updateVmsIfStatus(any(), anyList(), any(), any());
    }

    @Test
    void anExpiredTokenLeavesTheLabAlone() {
        node.expect(requestTo(NODE + "/qemu/105/status/current")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        stats.updateActiveProxmoxLabStats();

        verify(trackers, never()).updateVmsIfStatus(any(), anyList(), any(), any());
    }

    @Test
    void aVmThatDoesNotExistDegradesTheLab() {
        node.expect(requestTo(NODE + "/qemu/105/status/current")).andRespond(withSuccess(running(105), MediaType.APPLICATION_JSON));
        node.expect(requestTo(NODE + "/qemu/106/status/current")).andRespond(withServerError()
                .body("{\"data\":null,\"message\":\"Configuration file 'nodes/proxmox2/qemu-server/106.conf' does not exist\\n\"}"));

        stats.updateActiveProxmoxLabStats();

        verify(trackers).updateVmsIfStatus(eq(tracker), argThat(vms -> vms.size() == 2), eq(LabStatus.DEGRADED), eq(LabStatus.ACTIVE));
    }

    @Test
    void runningVmsKeepTheLabActive() {
        node.expect(requestTo(NODE + "/qemu/105/status/current")).andRespond(withSuccess(running(105), MediaType.APPLICATION_JSON));
        node.expect(requestTo(NODE + "/qemu/106/status/current")).andRespond(withSuccess(running(106), MediaType.APPLICATION_JSON));

        stats.updateActiveProxmoxLabStats();

        verify(trackers).updateVmsIfStatus(eq(tracker), argThat(vms -> vms.size() == 2), eq(LabStatus.ACTIVE), eq(LabStatus.ACTIVE));
    }
}
