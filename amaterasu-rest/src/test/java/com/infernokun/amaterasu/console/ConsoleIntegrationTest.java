package com.infernokun.amaterasu.console;

import com.fasterxml.jackson.databind.JsonNode;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVncTicket;
import com.infernokun.amaterasu.repositories.lab.LabRepository;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.repositories.lab.RemoteServerRepository;
import com.infernokun.amaterasu.services.alt.ProxmoxService;
import com.infernokun.amaterasu.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may open a VM console, and what happens when the lab or the node cannot give one. */
@SpringBootTest
@AutoConfigureMockMvc
class ConsoleIntegrationTest extends ApiTestSupport {
    @MockitoBean private ProxmoxService proxmoxService;
    @Autowired private ConsoleSessions consoleSessions;
    @Autowired private LabRepository labRepository;
    @Autowired private LabTrackerRepository labTrackerRepository;
    @Autowired private RemoteServerRepository remoteServerRepository;

    private User redMember;
    private User blueMember;
    private RemoteServer node;
    private LabTracker vmLab;
    private LabTracker composeLab;
    private final ProxmoxVncTicket ticket = new ProxmoxVncTicket("5900", "PVEVNC:ticket", "root@pam");

    @BeforeEach
    void setUp() {
        Team red = team("red");
        redMember = user(Role.MEMBER, red);
        blueMember = user(Role.MEMBER, team("blue"));
        node = remoteServerRepository.save(RemoteServer.builder().name(unique("pve")).ipAddress("10.0.0.250")
                .nodeName("inferno").serverType(ServerType.PROXMOX).build());
        RemoteServer dock = remoteServerRepository.save(RemoteServer.builder().name(unique("dock")).ipAddress("10.0.0.5")
                .serverType(ServerType.DOCKER_HOST).build());

        Lab forest = labRepository.save(Lab.builder().name("AD Forest Breach").labType(LabType.VIRTUAL_MACHINE).ready(true).build());
        Lab web = labRepository.save(Lab.builder().name("Web Exploitation 101").labType(LabType.DOCKER_COMPOSE).ready(true).build());
        vmLab = labTrackerRepository.save(LabTracker.builder().labStarted(forest).labOwner(red).labStatus(LabStatus.ACTIVE)
                .remoteServer(node)
                .vms(new ArrayList<>(List.of(vm(101, "kali", "running"), vm(102, "dc01", "stopped"))))
                .build());
        composeLab = labTrackerRepository.save(LabTracker.builder().labStarted(web).labOwner(red).labStatus(LabStatus.ACTIVE)
                .remoteServer(dock).build());
        when(proxmoxService.createVncProxy(any(), anyInt())).thenReturn(ticket);
    }

    private static ProxmoxVM vm(int vmid, String name, String status) {
        return ProxmoxVM.builder().vmid(vmid).name(name).status(status).build();
    }

    private String url(LabTracker tracker, int vmid) {
        return "/api/lab-tracker/" + tracker.getId() + "/vms/" + vmid + "/console";
    }

    @Test
    void consolesNeedASignedInUser() throws Exception {
        mvc.perform(post(url(vmLab, 101))).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherTeamCannotOpenTheConsole() throws Exception {
        postAs(url(vmLab, 101), token(blueMember), Map.of()).andExpect(status().isForbidden());
        verify(proxmoxService, never()).createVncProxy(any(), anyInt());
    }

    @Test
    void theOwningTeamGetsAOneTimeConsoleForItsVm() throws Exception {
        JsonNode session = data(postAs(url(vmLab, 101), token(redMember), Map.of()).andExpect(status().isOk()));

        verify(proxmoxService).createVncProxy(argThat(server -> node.getId().equals(server.getId())), eq(101));
        assertThat(session.get("vmid").asInt()).isEqualTo(101);
        assertThat(session.get("vmName").asText()).isEqualTo("kali");
        assertThat(session.get("password").asText()).isEqualTo("PVEVNC:ticket");
        String id = session.get("sessionId").asText();
        assertThat(session.get("path").asText()).isEqualTo("/socket-handler/console/" + id);

        assertThat(consoleSessions.claim(id, blueMember.getId())).isEmpty();
        assertThat(consoleSessions.claim(id, redMember.getId())).hasValueSatisfying(pending -> {
            assertThat(pending.serverId()).isEqualTo(node.getId());
            assertThat(pending.ticket()).isEqualTo(ticket);
        });
    }

    @Test
    void adminsMayOpenAnyTeamsConsole() throws Exception {
        postAs(url(vmLab, 101), adminToken(), Map.of()).andExpect(status().isOk());
    }

    @Test
    void aVmOutsideTheLabIsNotFound() throws Exception {
        postAs(url(vmLab, 999), token(redMember), Map.of()).andExpect(status().isNotFound());
        postAs("/api/lab-tracker/missing/vms/101/console", token(redMember), Map.of()).andExpect(status().isNotFound());
        verify(proxmoxService, never()).createVncProxy(any(), anyInt());
    }

    @Test
    void stoppedVmsAndComposeLabsHaveNoConsole() throws Exception {
        JsonNode stopped = objectMapper.readTree(postAs(url(vmLab, 102), token(redMember), Map.of())
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString());
        assertThat(stopped.get("message").asText()).isEqualTo("dc01 is stopped. Start or resume the lab first.");

        composeLab.setVms(new ArrayList<>(List.of(vm(101, "web", "running"))));
        labTrackerRepository.save(composeLab);
        postAs(url(composeLab, 101), token(redMember), Map.of()).andExpect(status().isConflict());
        verify(proxmoxService, never()).createVncProxy(any(), anyInt());
    }

    @Test
    void aNodeThatRefusesGivesItsReason() throws Exception {
        when(proxmoxService.createVncProxy(any(), anyInt())).thenThrow(HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null,
                "{\"message\":\"VM 101 not running\\n\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        JsonNode body = objectMapper.readTree(postAs(url(vmLab, 101), token(redMember), Map.of())
                .andExpect(status().isBadGateway()).andReturn().getResponse().getContentAsString());
        assertThat(body.get("message").asText()).isEqualTo(node.getName() + " refused the console: VM 101 not running");
    }
}
