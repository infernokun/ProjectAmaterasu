package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.infernokun.amaterasu.models.entities.ApplicationInfo;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.repositories.ApplicationInfoRepository;
import com.infernokun.amaterasu.utils.AESUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CSV imports and bulk moves of users, editing servers, moving labs between them, and the classification banner. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminImportServersIntegrationTest extends IntegrationSupport {
    @Autowired AESUtil aesUtil;
    @Autowired ApplicationInfoRepository applicationInfoRepository;

    private static Map<String, Object> row(int line, String username, String password, String role, String team) {
        Map<String, Object> row = new HashMap<>();
        row.put("row", line);
        row.put("username", username);
        row.put("password", password);
        row.put("role", role);
        row.put("team", team);
        return row;
    }

    private List<String> nonAdmins() throws Exception {
        return List.of(token(user(Role.MEMBER, team())), token(user(Role.CREATOR, null)), token(user(Role.FACILITATOR, null)));
    }

    // Users

    @Test
    void adminsImportUsersAndBadRowsAreSkippedWithAReason() throws Exception {
        Team team = team();
        User existing = user(Role.MEMBER, null);
        String fresh = unique("imp");
        String creator = unique("imp");
        List<Map<String, Object>> rows = List.of(
                row(2, fresh, "Str0ng-password", "member", team.getName().toUpperCase()),
                row(3, creator, "Str0ng-password", "CREATOR", ""),
                row(4, fresh.toUpperCase(), "Str0ng-password", "MEMBER", ""),
                row(5, existing.getUsername(), "Str0ng-password", "MEMBER", ""),
                row(6, unique("imp"), "Str0ng-password", "OWNER", ""),
                row(7, unique("imp"), "Str0ng-password", "MEMBER", "No Such Team"),
                row(8, "a b", "Str0ng-password", "MEMBER", ""),
                row(9, unique("imp"), "short", "MEMBER", ""));

        JsonNode result = data(send(post("/api/user/import"), Map.of("users", rows), admin())
                .andExpect(status().isOk()).andReturn());
        assertThat(result.get("created")).hasSize(2);
        assertThat(result.get("created").get(0).get("team").asText()).isEqualTo(team.getName());
        assertThat(result.get("created").get(1).get("role").asText()).isEqualTo("CREATOR");
        JsonNode skipped = result.get("skipped");
        assertThat(skipped).hasSize(6);
        assertThat(skipped.get(0).get("row").asInt()).isEqualTo(4);
        assertThat(skipped.get(0).get("reason").asText()).contains("more than once");
        assertThat(skipped.get(1).get("reason").asText()).contains("already exists");
        assertThat(skipped.get(2).get("reason").asText()).contains("not one of");
        assertThat(skipped.get(3).get("reason").asText()).contains("No team named");
        assertThat(skipped.get(4).get("reason").asText()).contains("Username must be");
        assertThat(skipped.get(5).get("reason").asText()).contains("Password must be");

        User imported = userRepository.findByUsernameIgnoreCase(fresh).orElseThrow();
        assertThat(imported.getTeam().getId()).isEqualTo(team.getId());
        assertThat(imported.getRole()).isEqualTo(Role.MEMBER);
        token(fresh, "Str0ng-password");
        assertThat(events("USERS_IMPORTED", null)).isNotEmpty();
    }

    @Test
    void adminsMoveSeveralUsersToATeamAtOnce() throws Exception {
        Team from = team();
        Team to = team();
        User a = user(Role.MEMBER, from);
        User b = user(Role.MEMBER, null);
        String admin = admin();

        JsonNode moved = data(send(put("/api/user/team/bulk"), Map.of("userIds", List.of(a.getId(), b.getId()),
                "teamId", to.getId()), admin).andExpect(status().isOk()).andReturn());
        assertThat(moved).hasSize(2);
        assertThat(userRepository.findById(a.getId()).orElseThrow().getTeam().getId()).isEqualTo(to.getId());
        assertThat(userRepository.findById(b.getId()).orElseThrow().getTeam().getId()).isEqualTo(to.getId());
        assertThat(events("USER_TEAM_CHANGED", a.getId())).isNotEmpty();

        // One unknown id moves nobody
        send(put("/api/user/team/bulk"), Map.of("userIds", List.of(a.getId(), "missing")), admin)
                .andExpect(status().isNotFound());
        assertThat(userRepository.findById(a.getId()).orElseThrow().getTeam().getId()).isEqualTo(to.getId());

        Map<String, Object> noTeam = new HashMap<>();
        noTeam.put("userIds", List.of(a.getId()));
        noTeam.put("teamId", null);
        send(put("/api/user/team/bulk"), noTeam, admin).andExpect(status().isOk());
        assertThat(userRepository.findById(a.getId()).orElseThrow().getTeam()).isNull();
    }

    @Test
    void onlyAdminsImportOrBulkMoveUsers() throws Exception {
        User target = user(Role.MEMBER, null);
        Team team = team();
        for (String token : nonAdmins()) {
            send(post("/api/user/import"), Map.of("users", List.of(row(2, unique("x"), "Str0ng-password", "ADMIN", ""))), token)
                    .andExpect(status().isForbidden());
            send(put("/api/user/team/bulk"), Map.of("userIds", List.of(target.getId()), "teamId", team.getId()), token)
                    .andExpect(status().isForbidden());
        }
    }

    // Servers

    @Test
    void adminsEditAServerAndCredentialsStayUnlessReentered() throws Exception {
        RemoteServer server = server(ServerType.DOCKER_HOST, false);
        server.setPassword(aesUtil.encrypt("old-secret"));
        remoteServerRepository.save(server);
        String admin = admin();

        JsonNode settings = data(call(get("/api/remote-server/" + server.getId() + "/settings"), admin)
                .andExpect(status().isOk()).andReturn());
        assertThat(settings.get("username").asText()).isEqualTo("amaterasu");
        assertThat(settings.get("hasPassword").asBoolean()).isTrue();
        assertThat(settings.has("password")).isFalse();

        Map<String, Object> edit = new HashMap<>(Map.of("name", server.getName() + "-b", "ipAddress", "10.20.0.13",
                "port", 2222, "username", "labs"));
        send(put("/api/remote-server/" + server.getId()), Map.of("name", " ", "ipAddress", "10.0.0.1", "port", 22,
                "username", "labs"), admin).andExpect(status().isBadRequest());
        send(put("/api/remote-server/" + server.getId()), Map.of("name", "x", "ipAddress", "http://10.0.0.1/", "port", 22,
                "username", "labs"), admin).andExpect(status().isBadRequest());
        send(put("/api/remote-server/" + server.getId()), Map.of("name", "x", "ipAddress", "10.0.0.1", "port", 70000,
                "username", "labs"), admin).andExpect(status().isBadRequest());
        send(put("/api/remote-server/" + server.getId()), Map.of("name", "x", "ipAddress", "10.0.0.1", "port", 22,
                "username", "labs", "serverType", "PROXMOX"), admin).andExpect(status().isBadRequest());

        JsonNode saved = data(send(put("/api/remote-server/" + server.getId()), edit, admin)
                .andExpect(status().isOk()).andReturn());
        assertThat(saved.get("name").asText()).isEqualTo(server.getName() + "-b");
        assertThat(saved.get("port").asInt()).isEqualTo(2222);
        RemoteServer stored = remoteServerRepository.findById(server.getId()).orElseThrow();
        assertThat(stored.getUsername()).isEqualTo("labs");
        assertThat(aesUtil.decrypt(stored.getPassword())).isEqualTo("old-secret");
        assertThat(events("SERVER_UPDATED", server.getId()).getFirst().getDetail()).contains("port 1 to 2222")
                .doesNotContain("old-secret");

        edit.put("password", "new-secret");
        send(put("/api/remote-server/" + server.getId()), edit, admin).andExpect(status().isOk());
        assertThat(aesUtil.decrypt(remoteServerRepository.findById(server.getId()).orElseThrow().getPassword()))
                .isEqualTo("new-secret");

        RemoteServer other = server(ServerType.DOCKER_HOST, false);
        edit.put("name", other.getName());
        send(put("/api/remote-server/" + server.getId()), edit, admin).andExpect(status().isBadRequest());
    }

    @Test
    void testingEditsUsesTheStoredCredentialsAndReportsAFailedConnection() throws Exception {
        RemoteServer server = server(ServerType.DOCKER_HOST, false);
        server.setPassword(aesUtil.encrypt("old-secret"));
        remoteServerRepository.save(server);
        JsonNode valid = data(send(post("/api/remote-server/" + server.getId() + "/validate"),
                Map.of("name", server.getName(), "ipAddress", "127.0.0.1", "port", 1, "username", "labs"), admin())
                .andExpect(status().isOk()).andReturn());
        assertThat(valid.asBoolean()).isFalse();
    }

    @Test
    void onlyAdminsEditServersOrMoveLabs() throws Exception {
        RemoteServer server = server(ServerType.DOCKER_HOST, false);
        RemoteServer target = server(ServerType.DOCKER_HOST, true);
        Team team = team();
        LabTracker tracker = tracker(lab(LabType.DOCKER_COMPOSE, true), team, LabStatus.STOPPED, server);
        Map<String, Object> edit = Map.of("name", "renamed", "ipAddress", "10.0.0.2", "port", 22, "username", "labs");
        for (String token : nonAdmins()) {
            call(get("/api/remote-server/" + server.getId() + "/settings"), token).andExpect(status().isForbidden());
            send(put("/api/remote-server/" + server.getId()), edit, token).andExpect(status().isForbidden());
            send(post("/api/remote-server/" + server.getId() + "/validate"), edit, token).andExpect(status().isForbidden());
            send(post("/api/lab-tracker/" + tracker.getId() + "/move"), Map.of("remoteServerId", target.getId()), token)
                    .andExpect(status().isForbidden());
        }
        // Even the team that owns the lab cannot move it
        send(post("/api/lab-tracker/" + tracker.getId() + "/move"), Map.of("remoteServerId", target.getId()),
                token(user(Role.MEMBER, team))).andExpect(status().isForbidden());
        assertThat(labTrackerRepository.findById(tracker.getId()).orElseThrow().getRemoteServer().getId())
                .isEqualTo(server.getId());
    }

    @Test
    void aLabThatIsNotDeployedMovesDirectly() throws Exception {
        RemoteServer from = server(ServerType.DOCKER_HOST, false);
        RemoteServer to = server(ServerType.DOCKER_HOST, false);
        RemoteServer proxmox = server(ServerType.PROXMOX, true);
        Team team = team();
        LabTracker tracker = tracker(lab(LabType.DOCKER_COMPOSE, true), team, LabStatus.STOPPED, from);
        String admin = admin();

        send(post("/api/lab-tracker/" + tracker.getId() + "/move"), Map.of("remoteServerId", proxmox.getId()), admin)
                .andExpect(status().isBadRequest());
        send(post("/api/lab-tracker/" + tracker.getId() + "/move"), Map.of("remoteServerId", from.getId()), admin)
                .andExpect(status().isBadRequest());

        JsonNode result = data(send(post("/api/lab-tracker/" + tracker.getId() + "/move"),
                Map.of("remoteServerId", to.getId()), admin).andExpect(status().isOk()).andReturn());
        assertThat(result.get("isSuccessful").asBoolean()).isTrue();
        LabTracker moved = labTrackerRepository.findById(tracker.getId()).orElseThrow();
        assertThat(moved.getRemoteServer().getId()).isEqualTo(to.getId());
        assertThat(moved.getLabStatus()).isEqualTo(LabStatus.STOPPED);
        assertThat(events("LAB_MOVED", tracker.getId()).getFirst().getDetail())
                .contains("From " + from.getName() + " to " + to.getName());
        assertThat(events("LAB_MOVED", tracker.getId()).getFirst().getTeamId()).isEqualTo(team.getId());
    }

    @Test
    void aDeployedLabNeedsConfirmationAndIsLeftAloneWhenItsCopyCannotBeRemoved() throws Exception {
        RemoteServer from = server(ServerType.DOCKER_HOST, true);
        RemoteServer offline = server(ServerType.DOCKER_HOST, false);
        RemoteServer to = server(ServerType.DOCKER_HOST, true);
        Team team = team();
        Lab lab = lab(LabType.DOCKER_COMPOSE, true);
        LabTracker tracker = tracker(lab, team, LabStatus.ACTIVE, from);
        String admin = admin();

        JsonNode refused = objectMapper.readTree(send(post("/api/lab-tracker/" + tracker.getId() + "/move"),
                Map.of("remoteServerId", to.getId()), admin).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString());
        assertThat(refused.get("message").asText()).contains("destroys that copy");

        send(post("/api/lab-tracker/" + tracker.getId() + "/move"),
                Map.of("remoteServerId", offline.getId(), "startFresh", true), admin).andExpect(status().isBadRequest());

        // The test servers do not answer, so the old copy cannot be torn down and nothing moves
        JsonNode failed = data(send(post("/api/lab-tracker/" + tracker.getId() + "/move"),
                Map.of("remoteServerId", to.getId(), "startFresh", true), admin)
                .andExpect(status().isBadGateway()).andReturn());
        assertThat(failed.get("isSuccessful").asBoolean()).isFalse();
        assertThat(failed.get("output").asText()).contains("nothing was moved");
        LabTracker after = labTrackerRepository.findById(tracker.getId()).orElseThrow();
        assertThat(after.getRemoteServer().getId()).isEqualTo(from.getId());
        assertThat(after.getLabStatus()).isEqualTo(LabStatus.ACTIVE);
        assertThat(events("LAB_ACTION_FAILED", tracker.getId()).getFirst().getDetail()).contains("Move to " + to.getName());
    }

    // Classification banner

    @Test
    void adminsSetTheClassificationBannerForEveryone() throws Exception {
        String admin = admin();
        send(put("/api/application-info/classification"), Map.of("classification", "TOP SECRET"), admin)
                .andExpect(status().isBadRequest());
        for (String token : nonAdmins()) {
            send(put("/api/application-info/classification"), Map.of("classification", "SECRET"), token)
                    .andExpect(status().isForbidden());
        }
        send(put("/api/application-info/classification"), Map.of("classification", "SECRET"), null)
                .andExpect(status().isUnauthorized());

        if (applicationInfoRepository.count() == 0) {
            ApplicationInfo info = new ApplicationInfo();
            info.setSettings(Map.of("keep", "me"));
            applicationInfoRepository.save(info);
        }
        send(put("/api/application-info/classification"), Map.of("classification", "secret"), admin)
                .andExpect(status().isOk());
        JsonNode info = data(call(get("/api/application-info"), null).andExpect(status().isOk()).andReturn());
        JsonNode settings = objectMapper.readTree(info.get("settings").asText());
        assertThat(settings.get("classification").asText()).isEqualTo("SECRET");
        assertThat(events("CLASSIFICATION_CHANGED", null).getLast().getDetail()).endsWith("to SECRET");

        send(put("/api/application-info/classification"), Map.of("classification", "UNCLASSIFIED"), admin)
                .andExpect(status().isOk());
    }
}
