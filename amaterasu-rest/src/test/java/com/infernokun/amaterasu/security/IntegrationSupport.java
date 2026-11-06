package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.models.entities.AuditEvent;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.entities.lab.RemoteServerStats;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.repositories.AuditEventRepository;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.repositories.ctf.CTFEntityRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomUserRepository;
import com.infernokun.amaterasu.repositories.lab.LabRepository;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.repositories.lab.RemoteServerRepository;
import com.infernokun.amaterasu.repositories.lab.RemoteServerStatsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Accounts, rooms, labs and servers for the integration tests, and signed-in requests. */
abstract class IntegrationSupport {
    static final String PASSWORD = "password123";

    @Autowired MockMvc mvc;
    // The context also holds a Redis mapper with type info; requests need plain JSON
    final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired UserRepository userRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired LabRepository labRepository;
    @Autowired LabTrackerRepository labTrackerRepository;
    @Autowired RemoteServerRepository remoteServerRepository;
    @Autowired RemoteServerStatsRepository remoteServerStatsRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired RoomUserRepository roomUserRepository;
    @Autowired CTFEntityRepository ctfEntityRepository;
    @Autowired AuditEventRepository auditEventRepository;
    @Autowired PasswordEncoder passwordEncoder;

    static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    User user(Role role, Team team) {
        User user = new User(unique("u"), passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setTeam(team);
        return userRepository.save(user);
    }

    Team team() {
        return teamRepository.save(Team.builder().name(unique("team")).build());
    }

    String token(User user) throws Exception {
        return token(user.getUsername(), PASSWORD);
    }

    String token(String username, String password) throws Exception {
        MvcResult result = send(post("/api/auth/login"), Map.of("username", username, "password", password), null)
                .andExpect(status().isOk()).andReturn();
        return data(result).get("accessToken").asText();
    }

    String admin() throws Exception {
        return token("test_admin", "test_password");
    }

    ResultActions send(MockHttpServletRequestBuilder request, Object body, String token) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }

    ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return send(request, null, token);
    }

    JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    Room room(String... facilitatorIds) {
        return roomRepository.save(Room.builder().name(unique("room"))
                .facilitators(new ArrayList<>(List.of(facilitatorIds))).build());
    }

    RoomUser join(User user, Room room) {
        RoomUser roomUser = new RoomUser();
        roomUser.setUser(user);
        roomUser.setRoom(room);
        roomUser.setPoints(0);
        roomUser.setRoomUserStatus(RoomUserStatus.JOINED);
        return roomUserRepository.save(roomUser);
    }

    CTFEntity challenge(Room room, String title, String labId, Integer sortOrder) {
        CTFEntity challenge = new CTFEntity();
        challenge.setQuestion(title);
        challenge.setRoom(room);
        challenge.setCategory("Web");
        challenge.setPoints(100);
        challenge.setMaxAttempts(5);
        challenge.setVisible(true);
        challenge.setLabId(labId);
        challenge.setSortOrder(sortOrder);
        challenge.setReleaseDate(LocalDateTime.now().minusHours(1));
        return ctfEntityRepository.save(challenge);
    }

    Lab lab(LabType type, boolean published) {
        return labRepository.save(Lab.builder().name(unique("lab")).labType(type).dockerFile("lab.yml")
                .capacity(5).ready(true).published(published).build());
    }

    RemoteServer server(ServerType type, boolean online) {
        RemoteServer server = remoteServerRepository.save(RemoteServer.builder().name(unique(type == ServerType.PROXMOX ? "pve" : "dock"))
                .ipAddress("127.0.0.1").port(1).username("amaterasu")
                .nodeName(type == ServerType.PROXMOX ? "pve" : null).serverType(type).build());
        if (online) {
            RemoteServerStats stats = new RemoteServerStats();
            stats.setRemoteServer(server);
            stats.setStatus(LabStatus.ACTIVE);
            remoteServerStatsRepository.save(stats);
            server.setRemoteServerStats(stats);
        }
        return server;
    }

    LabTracker tracker(Lab lab, Team owner, LabStatus status, RemoteServer server) {
        LabTracker tracker = labTrackerRepository.save(LabTracker.builder()
                .labStarted(lab).labOwner(owner).labStatus(status).remoteServer(server).build());
        // Reloaded, so one team can be given several labs
        Team fresh = teamRepository.findById(owner.getId()).orElseThrow();
        fresh.getTeamActiveLabs().add(tracker.getId());
        teamRepository.save(fresh);
        return tracker;
    }

    List<AuditEvent> events(String action, String targetId) {
        return auditEventRepository.findAll().stream()
                .filter(event -> action.equals(event.getAction()) && (targetId == null || targetId.equals(event.getTargetId())))
                .toList();
    }
}
