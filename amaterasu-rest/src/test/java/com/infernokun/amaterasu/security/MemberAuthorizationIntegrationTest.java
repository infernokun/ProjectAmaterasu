package com.infernokun.amaterasu.security;

import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.ServerType;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Separation of powers: every endpoint that manages users, teams, servers, labs or environments,
 * called by a plain member, is refused with 403. A creator builds labs but runs no environments and
 * manages nobody.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MemberAuthorizationIntegrationTest extends IntegrationSupport {

    record Call(HttpMethod method, String path, Object body) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private static Call call(String method, String path) {
        return new Call(HttpMethod.valueOf(method), path, Map.of());
    }

    private static Call call(String method, String path, Object body) {
        return new Call(HttpMethod.valueOf(method), path, body);
    }

    /** What a member may not do, with real ids where the check needs one. */
    private List<Call> adminOnly(User member, User other, Team team, Room room, Lab lab, LabTracker copy,
                                 RemoteServer server, CTFEntity question) {
        String r = room.getId();
        String t = copy.getId();
        String s = server.getId();
        Map<String, String> action = Map.of("labId", lab.getId(), "labTrackerId", t, "remoteServerId", s);
        return new ArrayList<>(List.of(
                // Users
                call("GET", "/api/user"),
                call("GET", "/api/user/" + other.getId()),
                call("GET", "/api/user/by?username=" + other.getUsername()),
                call("POST", "/api/user", Map.of("username", unique("x"), "password", "password123", "role", "ADMIN")),
                call("POST", "/api/user/many", List.of()),
                call("PUT", "/api/user/" + other.getId(), Map.of("username", "renamed")),
                call("PUT", "/api/user/" + other.getId() + "/password", Map.of("password", "password456")),
                call("PUT", "/api/user/" + other.getId() + "/disabled", Map.of("disabled", true)),
                call("PUT", "/api/user/" + member.getId() + "/role", Map.of("role", "ADMIN")),
                call("POST", "/api/user/import", Map.of("users", List.of())),
                call("PUT", "/api/user/team/bulk", Map.of("userIds", List.of(member.getId()), "teamId", team.getId())),
                call("PUT", "/api/user/team?userId=" + member.getId() + "&teamId=" + team.getId()),
                call("DELETE", "/api/user/" + other.getId()),
                // Teams
                call("GET", "/api/team"),
                call("GET", "/api/team/" + team.getId()),
                call("POST", "/api/team", Map.of("name", unique("t"))),
                call("POST", "/api/team/many", List.of()),
                call("PUT", "/api/team/" + team.getId(), Map.of("name", unique("t"))),
                call("PUT", "/api/team", Map.of("id", team.getId(), "name", unique("t"))),
                call("DELETE", "/api/team/" + team.getId()),
                // Audit log, settings, servers
                call("GET", "/api/audit"),
                call("PUT", "/api/application-info/classification", Map.of("level", "SECRET")),
                call("GET", "/actuator/env"),
                call("GET", "/api/remote-server"),
                call("GET", "/api/remote-server/" + s),
                call("GET", "/api/remote-server/" + s + "/settings"),
                call("POST", "/api/remote-server", Map.of("name", "x")),
                call("POST", "/api/remote-server/validate", Map.of("name", "x")),
                call("POST", "/api/remote-server/" + s + "/validate", Map.of("name", "x")),
                call("POST", "/api/remote-server/" + s + "/check"),
                call("PUT", "/api/remote-server/" + s, Map.of("name", "x")),
                call("PUT", "/api/remote-server/" + s + "/new-labs", Map.of("allowed", false)),
                call("DELETE", "/api/remote-server/" + s),
                call("GET", "/api/remote-server-stats"),
                call("POST", "/api/remote-server-stats", Map.of()),
                call("PUT", "/api/remote-server-stats/x", Map.of()),
                call("DELETE", "/api/remote-server-stats/x"),
                call("GET", "/api/proxmox/vms"),
                // Environment definitions
                call("POST", "/api/labs?remoteServerId=" + s, Map.of("name", "x")),
                call("POST", "/api/labs/many", List.of()),
                call("POST", "/api/labs/validate", "services: {}"),
                call("POST", "/api/labs/upload/" + lab.getId() + "/" + s, "services: {}"),
                call("PUT", "/api/labs/" + lab.getId(), Map.of("name", "x")),
                call("PUT", "/api/labs/" + lab.getId() + "/publish"),
                call("PUT", "/api/labs/" + lab.getId() + "/unpublish"),
                call("DELETE", "/api/labs/" + lab.getId()),
                call("GET", "/api/labs/check/" + lab.getId() + "/" + s),
                call("GET", "/api/labs/settings/" + lab.getId() + "/" + s),
                call("DELETE", "/api/labs/delete-item/" + lab.getId()),
                call("POST", "/api/labs/dev", Map.of("teamId", team.getId())),
                // Environment lifecycle, every route to it
                call("POST", "/api/labs/start", action),
                call("POST", "/api/labs/pause", action),
                call("POST", "/api/labs/stop", action),
                call("POST", "/api/labs/delete", action),
                call("POST", "/api/labs/delete-lab-from-team", action),
                call("POST", "/api/room/" + r + "/environment/start", Map.of("target", "team:" + team.getId())),
                call("POST", "/api/room/" + r + "/environment/pause", Map.of("target", "team:" + team.getId())),
                call("POST", "/api/room/" + r + "/environment/stop", Map.of("target", "team:" + team.getId())),
                call("POST", "/api/room/" + r + "/environment/start", Map.of("target", "solo")),
                call("POST", "/api/lab-tracker", Map.of()),
                call("POST", "/api/lab-tracker/many", List.of()),
                call("PUT", "/api/lab-tracker/" + t, Map.of("id", t)),
                call("DELETE", "/api/lab-tracker/" + t),
                call("POST", "/api/lab-tracker/" + t + "/move", Map.of("remoteServerId", s, "startFresh", true)),
                call("POST", "/api/lab-tracker/" + t + "/restart/web"),
                call("GET", "/api/lab-tracker/overview"),
                call("GET", "/api/lab-tracker/logs/" + t + "/" + s + "/all"),
                call("GET", "/api/lab-tracker/settings/" + t + "/" + s),
                call("POST", "/api/lab-tracker/settings/" + t + "/" + s),
                // Labs, questions and who runs them
                call("POST", "/api/room", Map.of("name", unique("lab"), "scenario", "x")),
                call("POST", "/api/room/many", List.of()),
                call("PUT", "/api/room/" + r, Map.of("name", unique("lab"))),
                call("DELETE", "/api/room/" + r),
                call("POST", "/api/room/" + r + "/files"),
                call("DELETE", "/api/room/" + r + "/files/x"),
                call("GET", "/api/room/facilitated"),
                call("GET", "/api/room/facilitator-candidates"),
                call("GET", "/api/room/" + r + "/facilitators"),
                call("PUT", "/api/room/" + r + "/facilitators", Map.of("userIds", List.of(member.getId()))),
                call("PUT", "/api/room/" + r + "/order", Map.of("challengeIds", List.of(question.getId()))),
                call("GET", "/api/room/" + r + "/live"),
                call("POST", "/api/room/add-points/" + r + "/" + member.getId(), Map.of("points", 100, "reason", "me")),
                call("GET", "/api/ctf-entity"),
                call("GET", "/api/ctf-entity/full"),
                call("GET", "/api/ctf-entity/" + question.getId()),
                call("GET", "/api/ctf-entity/by?room=" + r),
                call("GET", "/api/ctf-entity/room/" + r),
                call("POST", "/api/ctf-entity", Map.of("question", "x")),
                call("POST", "/api/ctf-entity/batch", List.of()),
                call("PUT", "/api/ctf-entity/" + question.getId(), Map.of("question", "x")),
                call("DELETE", "/api/ctf-entity/" + question.getId()),
                call("POST", "/api/ctf-entity/use-hint?hintId=x&roomId=" + r + "&userId=" + other.getId()
                        + "&ctfEntityId=" + question.getId())));
    }

    @TestFactory
    Stream<DynamicTest> membersAreRefusedEveryAdminAction() throws Exception {
        Team team = team();
        User member = user(Role.MEMBER, team);
        User other = user(Role.MEMBER, null);
        String token = token(member);
        RemoteServer server = server(ServerType.PROXMOX, true);
        Lab lab = lab(LabType.VIRTUAL_MACHINE, true);
        Room room = roomRepository.save(Room.builder().name(unique("lab")).scenario("x").environment(lab)
                .environmentMode(EnvironmentMode.PER_TEAM).build());
        CTFEntity question = challenge(room, "Q", null, 0);
        // The member's own team's copy: still not theirs to run
        LabTracker copy = tracker(lab, team, LabStatus.ACTIVE, server);
        return adminOnly(member, other, team, room, lab, copy, server, question).stream()
                .map(call -> DynamicTest.dynamicTest(call.toString(), () -> refused(call, token)));
    }

    @TestFactory
    Stream<DynamicTest> creatorsBuildLabsButManageNobodyAndRunNothing() throws Exception {
        Team team = team();
        User creator = user(Role.CREATOR, null);
        User other = user(Role.MEMBER, team);
        String token = token(creator);
        RemoteServer server = server(ServerType.PROXMOX, true);
        Lab lab = lab(LabType.VIRTUAL_MACHINE, true);
        Room room = roomRepository.save(Room.builder().name(unique("lab")).scenario("x").environment(lab)
                .environmentMode(EnvironmentMode.SHARED).build());
        LabTracker copy = tracker(lab, team, LabStatus.ACTIVE, server);
        String r = room.getId();
        Map<String, String> action = Map.of("labId", lab.getId(), "labTrackerId", copy.getId(), "remoteServerId", server.getId());
        List<Call> calls = List.of(
                call("GET", "/api/user"),
                call("PUT", "/api/user/team/bulk", Map.of("userIds", List.of(other.getId()), "teamId", team.getId())),
                call("PUT", "/api/user/" + other.getId() + "/role", Map.of("role", "ADMIN")),
                call("POST", "/api/team", Map.of("name", unique("t"))),
                call("DELETE", "/api/team/" + team.getId()),
                call("GET", "/api/audit"),
                call("PUT", "/api/room/" + r + "/facilitators", Map.of("userIds", List.of())),
                call("POST", "/api/room/" + r + "/environment/start"),
                call("POST", "/api/room/" + r + "/environment/stop"),
                call("POST", "/api/labs/start", action),
                call("POST", "/api/labs/stop", action),
                call("POST", "/api/labs/delete", action),
                call("POST", "/api/lab-tracker/" + copy.getId() + "/restart/web"),
                call("GET", "/api/lab-tracker/logs/" + copy.getId() + "/" + server.getId() + "/all"),
                call("GET", "/api/room/" + r + "/live"),
                call("POST", "/api/room/add-points/" + r + "/" + other.getId(), Map.of("points", 5, "reason", "x")));
        return calls.stream().map(call -> DynamicTest.dynamicTest(call.toString(), () -> refused(call, token)));
    }

    private void refused(Call call, String token) throws Exception {
        MockHttpServletRequestBuilder request = request(call.method(), call.path());
        if (call.body() instanceof String text) {
            request.contentType("text/plain").content(text);
            call(request, token).andExpect(status().isForbidden());
        } else {
            send(request, call.body(), token).andExpect(status().isForbidden());
        }
    }
}
