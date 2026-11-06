package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.utils.AESUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A lab copy's VM password: its team and admins read it from /access; nobody gets it with the tracker. */
@SpringBootTest
@AutoConfigureMockMvc
class LabAccessIntegrationTest extends IntegrationSupport {
    private static final String VM_PASSWORD = "k7mq-x3vt-9hwp-r2nd";

    @Autowired AESUtil aesUtil;

    private Team owner;
    private LabTracker tracker;

    @BeforeEach
    void setUp() {
        owner = team();
        tracker = tracker(lab(LabType.VIRTUAL_MACHINE, true), owner, LabStatus.ACTIVE, server(ServerType.PROXMOX, true));
        tracker.setVms(new ArrayList<>(List.of(
                ProxmoxVM.builder().vmid(105).name("Red-Fox-Pivot-tpl-bastion").status("running").role("bastion")
                        .ciuser("player").guestAgent(true).ready(true).accessAddress("10.0.0.42").uplinkBridge("vmbr0")
                        .labSubnet("172.16.0.0/24").build(),
                ProxmoxVM.builder().vmid(106).name("Red-Fox-Pivot-tpl-target").status("running").build())));
        tracker.setAccessPassword(aesUtil.encrypt(VM_PASSWORD));
        tracker = labTrackerRepository.save(tracker);
    }

    private String access() {
        return "/api/lab-tracker/" + tracker.getId() + "/access";
    }

    @Test
    void theOwningTeamReadsTheLogin() throws Exception {
        JsonNode login = data(call(get(access()), token(user(Role.MEMBER, owner))).andExpect(status().isOk()).andReturn());
        assertThat(login.get("username").asText()).isEqualTo("player");
        assertThat(login.get("password").asText()).isEqualTo(VM_PASSWORD);
        assertThat(login.get("address").asText()).isEqualTo("10.0.0.42");
        assertThat(login.get("labSubnet").asText()).isEqualTo("172.16.0.0/24");
    }

    @Test
    void anotherTeamAndAFacilitatorAreRefused() throws Exception {
        String body = call(get(access()), token(user(Role.MEMBER, team()))).andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(VM_PASSWORD);
        call(get(access()), token(user(Role.FACILITATOR, null))).andExpect(status().isForbidden());
        call(get(access()), null).andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminReadsTheLogin() throws Exception {
        JsonNode login = data(call(get(access()), admin()).andExpect(status().isOk()).andReturn());
        assertThat(login.get("password").asText()).isEqualTo(VM_PASSWORD);
    }

    @Test
    void theTrackerItselfNeverCarriesThePassword() throws Exception {
        String member = token(user(Role.MEMBER, owner));
        String stored = labTrackerRepository.findById(tracker.getId()).orElseThrow().getAccessPassword();
        for (String path : List.of("/api/lab-tracker/" + tracker.getId(), "/api/lab-tracker")) {
            String body = call(get(path), member).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).contains("10.0.0.42").doesNotContain(VM_PASSWORD).doesNotContain(stored)
                    .doesNotContain("accessPassword");
        }
        String overview = call(get("/api/lab-tracker/overview"), admin()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(overview).doesNotContain(VM_PASSWORD).doesNotContain(stored);
    }
}
