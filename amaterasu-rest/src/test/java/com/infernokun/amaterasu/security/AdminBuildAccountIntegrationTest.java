package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.components.RefreshCookie;
import com.infernokun.amaterasu.models.entities.AuditEvent;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityAnswer;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityHintUsage;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.repositories.AuditEventRepository;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.repositories.ctf.CTFAnswerRepository;
import com.infernokun.amaterasu.repositories.ctf.CTFEntityRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomUserRepository;
import com.infernokun.amaterasu.repositories.lab.LabRepository;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.repositories.lab.RemoteServerRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role rules and behavior of the audit log, user and team admin, servers, authoring and account sessions. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminBuildAccountIntegrationTest {
    private static final String PASSWORD = "password123";

    @Autowired private MockMvc mvc;
    // The context also holds a Redis mapper with type info; requests need plain JSON
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private LabRepository labRepository;
    @Autowired private LabTrackerRepository labTrackerRepository;
    @Autowired private RemoteServerRepository remoteServerRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomUserRepository roomUserRepository;
    @Autowired private CTFEntityRepository ctfEntityRepository;
    @Autowired private CTFAnswerRepository ctfAnswerRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private record Session(String accessToken, String refreshCookie) {
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private User user(Role role, Team team) {
        User user = new User(unique("u"), passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setTeam(team);
        return userRepository.save(user);
    }

    private Team team() {
        return teamRepository.save(Team.builder().name(unique("team")).build());
    }

    private Session signIn(String username, String password) throws Exception {
        MvcResult result = send(post("/api/auth/login"), Map.of("username", username, "password", password), null)
                .andExpect(status().isOk()).andReturn();
        return new Session(data(result).get("accessToken").asText(), cookie(result));
    }

    private Session signIn(User user) throws Exception {
        return signIn(user.getUsername(), PASSWORD);
    }

    private String admin() throws Exception {
        return signIn("test_admin", "test_password").accessToken();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body, String token) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return send(request, null, token);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private static String cookie(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(RefreshCookie.NAME + "="))
                .map(header -> header.substring(RefreshCookie.NAME.length() + 1, header.indexOf(';')))
                .findFirst().orElse(null);
    }

    private Lab lab(LabType type) {
        return labRepository.save(Lab.builder().name(unique("lab")).labType(type).dockerFile("lab.yml")
                .capacity(5).ready(true).build());
    }

    private LabTracker tracker(Lab lab, Team owner, LabStatus status, RemoteServer server) {
        LabTracker tracker = labTrackerRepository.save(LabTracker.builder()
                .labStarted(lab).labOwner(owner).labStatus(status).remoteServer(server).build());
        owner.getTeamActiveLabs().add(tracker.getId());
        teamRepository.save(owner);
        return tracker;
    }

    private RemoteServer server() {
        return remoteServerRepository.save(RemoteServer.builder().name(unique("dock")).ipAddress("10.0.0.9")
                .port(22).serverType(ServerType.DOCKER_HOST).build());
    }

    // Audit log

    @Test
    void signInIsAuditedAndSetsLastSignIn() throws Exception {
        Team team = team();
        User member = user(Role.MEMBER, team);
        send(post("/api/auth/login"), Map.of("username", member.getUsername(), "password", "wrong-password"), null)
                .andExpect(status().isUnauthorized());
        signIn(member);

        assertThat(userRepository.findById(member.getId()).orElseThrow().getLastSignIn()).isNotNull();
        List<AuditEvent> events = auditEventRepository.findAll().stream()
                .filter(event -> member.getId().equals(event.getTargetId())).toList();
        assertThat(events).extracting(AuditEvent::getAction).contains("SIGN_IN", "SIGN_IN_FAILED");
        assertThat(events).allMatch(event -> team.getId().equals(event.getTeamId()));
    }

    @Test
    void membersOnlySeeTheirOwnTeamsEvents() throws Exception {
        Team red = team();
        Team blue = team();
        User redMember = user(Role.MEMBER, red);
        User blueMember = user(Role.MEMBER, blue);
        signIn(blueMember);
        String token = signIn(redMember).accessToken();

        JsonNode own = data(call(get("/api/audit"), token).andExpect(status().isOk()).andReturn());
        assertThat(own.get("content")).isNotEmpty();
        own.get("content").forEach(event -> assertThat(event.get("teamId").asText()).isEqualTo(red.getId()));

        JsonNode other = data(call(get("/api/audit").param("teamId", blue.getId()), token)
                .andExpect(status().isOk()).andReturn());
        assertThat(other.get("content")).isEmpty();
        assertThat(other.get("totalElements").asInt()).isZero();

        String admin = admin();
        JsonNode blueForAdmin = data(call(get("/api/audit").param("teamId", blue.getId())
                .param("action", "SIGN_IN,SIGN_IN_FAILED").param("size", "5"), admin).andExpect(status().isOk()).andReturn());
        assertThat(blueForAdmin.get("content")).isNotEmpty();
        assertThat(blueForAdmin.get("size").asInt()).isEqualTo(5);

        User noTeam = user(Role.MEMBER, null);
        JsonNode none = data(call(get("/api/audit"), signIn(noTeam).accessToken()).andReturn());
        assertThat(none.get("content")).isEmpty();
        call(get("/api/audit"), null).andExpect(status().isUnauthorized());
    }

    @Test
    void removingALabFromATeamIsAuditedForThatTeam() throws Exception {
        Team team = team();
        User member = user(Role.MEMBER, team);
        LabTracker stopped = tracker(lab(LabType.DOCKER_COMPOSE), team, LabStatus.STOPPED, null);
        String token = signIn(member).accessToken();

        send(post("/api/labs/delete-lab-from-team"),
                Map.of("labId", stopped.getLabStarted().getId(), "labTrackerId", stopped.getId()), token)
                .andExpect(status().isOk());

        JsonNode events = data(call(get("/api/audit").param("targetType", "LAB_TRACKER")
                .param("targetId", stopped.getId()), token).andExpect(status().isOk()).andReturn());
        assertThat(events.get("content")).hasSize(1);
        JsonNode event = events.get("content").get(0);
        assertThat(event.get("action").asText()).isEqualTo("LAB_DELETED");
        assertThat(event.get("actorUsername").asText()).isEqualTo(member.getUsername());
        assertThat(event.get("teamId").asText()).isEqualTo(team.getId());
        assertThat(java.time.Instant.parse(event.get("at").asText())).isBeforeOrEqualTo(java.time.Instant.now());
    }

    // Users and teams

    @Test
    void adminsCreateUsersAndChangeRoles() throws Exception {
        Team team = team();
        String admin = admin();
        String username = unique("new");

        JsonNode created = data(send(post("/api/user"),
                Map.of("username", username, "password", "Str0ng-password", "role", "CREATOR", "teamId", team.getId()), admin)
                .andExpect(status().isCreated()).andReturn());
        assertThat(created.get("role").asText()).isEqualTo("CREATOR");
        assertThat(created.get("team").get("id").asText()).isEqualTo(team.getId());
        assertThat(created.has("password")).isFalse();
        send(post("/api/user"), Map.of("username", username, "password", "Str0ng-password"), admin)
                .andExpect(status().isBadRequest());

        Session session = signIn(username, "Str0ng-password");
        Thread.sleep(1100);
        String id = created.get("id").asText();
        send(put("/api/user/" + id + "/role"), Map.of("role", "FACILITATOR"), admin).andExpect(status().isOk());

        // The old token carried the old role, so it stops working; a refresh brings the new role
        call(get("/api/labs"), session.accessToken()).andExpect(status().isUnauthorized());
        MvcResult refreshed = mvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie(RefreshCookie.NAME, session.refreshCookie()))).andExpect(status().isOk()).andReturn();
        assertThat(data(refreshed).get("user").get("role").asText()).isEqualTo("FACILITATOR");

        String adminId = userRepository.findByUsernameIgnoreCase("test_admin").orElseThrow().getId();
        send(put("/api/user/" + adminId + "/role"), Map.of("role", "MEMBER"), admin).andExpect(status().isBadRequest());
    }

    @Test
    void membersCannotManageUsersOrTeams() throws Exception {
        User member = user(Role.MEMBER, null);
        User creator = user(Role.CREATOR, null);
        Team team = team();
        for (String token : List.of(signIn(member).accessToken(), signIn(creator).accessToken())) {
            send(post("/api/user"), Map.of("username", unique("x"), "password", "Str0ng-password"), token)
                    .andExpect(status().isForbidden());
            send(put("/api/user/" + member.getId() + "/role"), Map.of("role", "ADMIN"), token)
                    .andExpect(status().isForbidden());
            call(put("/api/user/team").param("userId", member.getId()).param("teamId", team.getId()), token)
                    .andExpect(status().isForbidden());
            send(put("/api/team/" + team.getId()), Map.of("name", "taken over"), token).andExpect(status().isForbidden());
            call(delete("/api/team/" + team.getId()), token).andExpect(status().isForbidden());
        }
    }

    @Test
    void teamsRenameAndRefuseDeletionWhileLabsAreDeployed() throws Exception {
        String admin = admin();
        Team team = team();
        User member = user(Role.MEMBER, team);
        LabTracker running = tracker(lab(LabType.DOCKER_COMPOSE), team, LabStatus.ACTIVE, null);

        String name = unique("Renamed ");
        JsonNode renamed = data(send(put("/api/team/" + team.getId()), Map.of("name", name, "description", "Blue team"), admin)
                .andExpect(status().isOk()).andReturn());
        assertThat(renamed.get("name").asText()).isEqualTo(name);
        assertThat(renamed.get("teamActiveLabs")).hasSize(1);

        call(delete("/api/team/" + team.getId()), admin).andExpect(status().isConflict());

        running.setLabStatus(LabStatus.STOPPED);
        labTrackerRepository.save(running);
        call(delete("/api/team/" + team.getId()), admin).andExpect(status().isOk());
        assertThat(userRepository.findById(member.getId()).orElseThrow().getTeam()).isNull();
        assertThat(labTrackerRepository.findById(running.getId())).isEmpty();
        assertThat(auditEventRepository.findAll()).anyMatch(event ->
                "TEAM_DELETED".equals(event.getAction()) && team.getId().equals(event.getTargetId()));

        call(put("/api/user/team").param("userId", member.getId()), admin).andExpect(status().isOk());
    }

    // Servers

    @Test
    void serversWithDeployedLabsCannotBeDeleted() throws Exception {
        String admin = admin();
        Team team = team();
        RemoteServer busy = server();
        LabTracker running = tracker(lab(LabType.DOCKER_COMPOSE), team, LabStatus.PAUSED, busy);

        call(delete("/api/remote-server/" + busy.getId()), admin).andExpect(status().isConflict());
        assertThat(remoteServerRepository.findById(busy.getId())).isPresent();

        running.setLabStatus(LabStatus.STOPPED);
        labTrackerRepository.save(running);
        call(delete("/api/remote-server/" + busy.getId()), admin).andExpect(status().isOk());
        assertThat(remoteServerRepository.findById(busy.getId())).isEmpty();
        assertThat(labTrackerRepository.findById(running.getId()).orElseThrow().getRemoteServer()).isNull();
    }

    @Test
    void onlyAdminsManageServers() throws Exception {
        RemoteServer server = server();
        String member = signIn(user(Role.MEMBER, null)).accessToken();
        String creator = signIn(user(Role.CREATOR, null)).accessToken();
        for (String token : List.of(member, creator)) {
            call(post("/api/remote-server/" + server.getId() + "/check"), token).andExpect(status().isForbidden());
            call(delete("/api/remote-server/" + server.getId()), token).andExpect(status().isForbidden());
            send(post("/api/remote-server"), Map.of("name", "x", "serverType", "DOCKER_HOST"), token)
                    .andExpect(status().isForbidden());
        }
        send(post("/api/remote-server"), Map.of("name", " ", "ipAddress", "10.0.0.1", "serverType", "DOCKER_HOST"), admin())
                .andExpect(status().isBadRequest());
    }

    // Labs

    @Test
    void authorsDeleteLabsOnlyWhenNoTeamHasThemDeployed() throws Exception {
        Team team = team();
        Lab lab = lab(LabType.VIRTUAL_MACHINE);
        LabTracker running = tracker(lab, team, LabStatus.ACTIVE, null);
        String member = signIn(user(Role.MEMBER, team)).accessToken();
        String creator = signIn(user(Role.CREATOR, null)).accessToken();

        call(delete("/api/labs/" + lab.getId()), member).andExpect(status().isForbidden());
        send(put("/api/labs/" + lab.getId()), Map.of("name", "hijacked"), member).andExpect(status().isForbidden());
        call(delete("/api/labs/" + lab.getId()), creator).andExpect(status().isConflict());

        JsonNode edited = data(send(put("/api/labs/" + lab.getId()),
                Map.of("name", "AD Forest Breach", "capacity", 8, "vms", List.of(9001, 9002)), creator)
                .andExpect(status().isOk()).andReturn());
        assertThat(edited.get("capacity").asInt()).isEqualTo(8);
        assertThat(edited.get("vmIds")).hasSize(2);
        assertThat(edited.get("ready").asBoolean()).isTrue();

        running.setLabStatus(LabStatus.STOPPED);
        labTrackerRepository.save(running);
        call(delete("/api/labs/" + lab.getId()), creator).andExpect(status().isOk());
        assertThat(labRepository.findById(lab.getId())).isEmpty();
        assertThat(teamRepository.findById(team.getId()).orElseThrow().getTeamActiveLabs()).doesNotContain(running.getId());
        call(delete("/api/labs/" + lab.getId()), creator).andExpect(status().isNotFound());
    }

    // Rooms and challenges

    private JsonNode createChallenge(String roomId, String token) throws Exception {
        Map<String, Object> challenge = new java.util.HashMap<>(Map.of(
                "question", "Union Based Dump", "roomId", roomId, "category", "Web", "difficultyLevel", "MEDIUM",
                "points", 300, "maxAttempts", 5, "description", "Pull a hidden table.",
                "releaseDate", "2026-11-04T09:00", "expirationDate", "2026-11-05 18:00:00"));
        challenge.put("flags", List.of(Map.of("flag", "un10n_s3lect_all", "caseSensitive", true, "surroundWithTag", true, "weight", 1.0)));
        challenge.put("hints", List.of(Map.of("hint", "Count the columns", "cost", 25),
                Map.of("hint", "Look at information_schema", "cost", 50, "unlockAfterAttempts", 3)));
        return data(send(post("/api/ctf-entity"), challenge, token).andExpect(status().isCreated()).andReturn());
    }

    @Test
    void authorsEditChallengesHintsAndSchedule() throws Exception {
        String creator = signIn(user(Role.CREATOR, null)).accessToken();
        JsonNode room = data(send(post("/api/room"), Map.of("name", unique("Winter Range ")), creator)
                .andExpect(status().isCreated()).andReturn());
        JsonNode created = createChallenge(room.get("id").asText(), creator);
        assertThat(created.get("releaseDate").asText()).isEqualTo("2026-11-04 09:00:00");
        assertThat(created.get("expirationDate").asText()).isEqualTo("2026-11-05 18:00:00");

        String id = created.get("id").asText();
        JsonNode full = data(call(get("/api/ctf-entity/" + id), creator).andReturn());
        String keptHintId = full.get("hints").get(0).get("id").asText();
        List<Map<String, Object>> hints = new ArrayList<>();
        hints.add(Map.of("id", keptHintId, "hint", "Count the columns first", "cost", 30));
        hints.add(Map.of("hint", "A new hint", "cost", 10));
        Map<String, Object> update = new java.util.HashMap<>(Map.of("question", "Union Based Dump", "points", 350,
                "releaseDate", "2026-11-04 10:00:00", "hints", hints,
                "flags", List.of(Map.of("flag", "un10n_s3lect_all", "caseSensitive", false))));
        JsonNode updated = data(send(put("/api/ctf-entity/" + id), update, creator).andExpect(status().isOk()).andReturn());

        assertThat(updated.get("points").asInt()).isEqualTo(350);
        assertThat(updated.get("expirationDate").isNull()).isTrue();
        assertThat(updated.get("hints")).hasSize(2);
        CTFEntity stored = ctfEntityRepository.findById(id).orElseThrow();
        assertThat(stored.getHints()).extracting(h -> h.getHint())
                .containsExactlyInAnyOrder("Count the columns first", "A new hint");
        assertThat(stored.getHints()).anyMatch(h -> h.getId().equals(keptHintId) && h.getCost() == 30);

        send(post("/api/room"), Map.of("name", room.get("name").asText()), creator).andExpect(status().isBadRequest());
    }

    @Test
    void deletingChallengesAndRoomsRemovesAnswersAndMembers() throws Exception {
        String creator = signIn(user(Role.CREATOR, null)).accessToken();
        User player = user(Role.MEMBER, null);
        JsonNode roomJson = data(send(post("/api/room"), Map.of("name", unique("Doomed ")), creator).andReturn());
        String roomId = roomJson.get("id").asText();
        String first = createChallenge(roomId, creator).get("id").asText();
        String second = createChallenge(roomId, creator).get("id").asText();

        Room room = roomRepository.findById(roomId).orElseThrow();
        RoomUser roomUser = roomUserRepository.save(RoomUser.builder().room(room).user(player).build());
        CTFEntity challenge = ctfEntityRepository.findById(first).orElseThrow();
        CTFEntityAnswer answer = CTFEntityAnswer.builder().roomUser(roomUser).ctfEntity(challenge)
                .correct(false).attempts(1).hintUsages(new ArrayList<>()).build();
        CTFEntityHintUsage usage = CTFEntityHintUsage.builder().ctfEntityAnswer(answer)
                .hint(challenge.getHints().get(0)).usedAt(java.time.LocalDateTime.now()).pointsDeducted(25).usageOrder(1).build();
        answer.getHintUsages().add(usage);
        ctfAnswerRepository.save(answer);

        String member = signIn(player).accessToken();
        call(delete("/api/ctf-entity/" + first), member).andExpect(status().isForbidden());
        call(delete("/api/room/" + roomId), member).andExpect(status().isForbidden());

        call(delete("/api/ctf-entity/" + first), creator).andExpect(status().is2xxSuccessful());
        assertThat(ctfEntityRepository.findById(first)).isEmpty();
        assertThat(ctfAnswerRepository.findByCtfEntityId(first)).isEmpty();

        call(delete("/api/room/" + roomId), creator).andExpect(status().isOk());
        assertThat(roomRepository.findById(roomId)).isEmpty();
        assertThat(ctfEntityRepository.findById(second)).isEmpty();
        assertThat(roomUserRepository.findById(roomUser.getId())).isEmpty();
        assertThat(auditEventRepository.findAll()).anyMatch(event ->
                "ROOM_DELETED".equals(event.getAction()) && roomId.equals(event.getTargetId()));
    }

    @Test
    void paidHintsCanStillBeRemoved() throws Exception {
        String creator = signIn(user(Role.CREATOR, null)).accessToken();
        User player = user(Role.MEMBER, null);
        String roomId = data(send(post("/api/room"), Map.of("name", unique("Hints ")), creator).andReturn())
                .get("id").asText();
        String id = createChallenge(roomId, creator).get("id").asText();
        CTFEntity challenge = ctfEntityRepository.findById(id).orElseThrow();
        RoomUser roomUser = roomUserRepository.save(RoomUser.builder()
                .room(roomRepository.findById(roomId).orElseThrow()).user(player).build());
        CTFEntityAnswer answer = CTFEntityAnswer.builder().roomUser(roomUser).ctfEntity(challenge)
                .correct(false).attempts(1).hintUsages(new ArrayList<>()).build();
        answer.getHintUsages().add(CTFEntityHintUsage.builder().ctfEntityAnswer(answer)
                .hint(challenge.getHints().get(0)).usedAt(java.time.LocalDateTime.now()).pointsDeducted(25).usageOrder(1).build());
        ctfAnswerRepository.save(answer);

        send(put("/api/ctf-entity/" + id), Map.of("question", "No hints now", "hints", List.of()), creator)
                .andExpect(status().isOk());
        assertThat(ctfEntityRepository.findById(id).orElseThrow().getHints()).isEmpty();
        assertThat(ctfAnswerRepository.findByCtfEntityId(id)).hasSize(1);
    }

    @Test
    void challengesImportInBulk() throws Exception {
        String creator = signIn(user(Role.CREATOR, null)).accessToken();
        String roomId = data(send(post("/api/room"), Map.of("name", unique("Import ")), creator).andReturn())
                .get("id").asText();
        List<Map<String, Object>> batch = List.of(
                Map.of("question", "One", "points", 100, "room", Map.of("id", roomId),
                        "flags", List.of(Map.of("flag", "one")), "hints", List.of(Map.of("hint", "h", "cost", 5))),
                Map.of("question", "Two", "points", 200, "room", Map.of("id", roomId), "flags", List.of(Map.of("flag", "two"))));

        String member = signIn(user(Role.MEMBER, null)).accessToken();
        send(post("/api/ctf-entity/batch"), batch, member).andExpect(status().isForbidden());
        send(post("/api/ctf-entity/batch"), batch, creator).andExpect(status().isCreated());

        List<CTFEntity> imported = ctfEntityRepository.findByRoomId(roomId);
        assertThat(imported).extracting(CTFEntity::getQuestion).containsExactlyInAnyOrder("One", "Two");
        assertThat(imported).allMatch(c -> !c.getFlags().isEmpty() && c.getFlags().get(0).getCtfEntity() != null);

        send(post("/api/ctf-entity/batch"), List.of(Map.of("question", "No room")), creator)
                .andExpect(status().isBadRequest());
    }

    // Account sessions

    @Test
    void sessionsAreListedAndSignedOutOneByOneOrAllOthers() throws Exception {
        User member = user(Role.MEMBER, team());
        Session laptop = signIn(member);
        Session phone = signIn(member);
        Session tablet = signIn(member);

        JsonNode sessions = data(mvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + laptop.accessToken())
                        .cookie(new Cookie(RefreshCookie.NAME, laptop.refreshCookie())))
                .andExpect(status().isOk()).andReturn());
        assertThat(sessions).hasSize(3);
        assertThat(sessions.get(0).get("current").asBoolean()).isTrue();
        assertThat(sessions.get(1).get("current").asBoolean()).isFalse();
        String phoneSession = null;
        for (JsonNode session : sessions) {
            if (!session.get("current").asBoolean() && phoneSession == null) {
                phoneSession = session.get("id").asText();
            }
        }

        // Someone else cannot end it
        User other = user(Role.MEMBER, null);
        Session otherSession = signIn(other);
        mvc.perform(delete("/api/auth/sessions/" + phoneSession)
                        .header("Authorization", "Bearer " + otherSession.accessToken())
                        .cookie(new Cookie(RefreshCookie.NAME, otherSession.refreshCookie())))
                .andExpect(status().isNotFound());
        call(get("/api/auth/sessions"), null).andExpect(status().isUnauthorized());

        Thread.sleep(1100);
        MvcResult revoked = mvc.perform(delete("/api/auth/sessions/" + phoneSession)
                        .header("Authorization", "Bearer " + laptop.accessToken())
                        .cookie(new Cookie(RefreshCookie.NAME, laptop.refreshCookie())))
                .andExpect(status().isOk()).andReturn();
        String fresh = data(revoked).get("accessToken").asText();
        call(get("/api/labs"), laptop.accessToken()).andExpect(status().isUnauthorized());
        call(get("/api/labs"), fresh).andExpect(status().isOk());

        // The current session cannot be ended from here
        String current = sessions.get(0).get("id").asText();
        mvc.perform(delete("/api/auth/sessions/" + current)
                        .header("Authorization", "Bearer " + fresh)
                        .cookie(new Cookie(RefreshCookie.NAME, laptop.refreshCookie())))
                .andExpect(status().isBadRequest());

        MvcResult others = mvc.perform(post("/api/auth/sessions/sign-out-others")
                        .header("Authorization", "Bearer " + fresh)
                        .cookie(new Cookie(RefreshCookie.NAME, laptop.refreshCookie())))
                .andExpect(status().isOk()).andReturn();
        String afterOthers = data(others).get("accessToken").asText();

        JsonNode remaining = data(mvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + afterOthers)
                        .cookie(new Cookie(RefreshCookie.NAME, laptop.refreshCookie())))
                .andExpect(status().isOk()).andReturn());
        assertThat(remaining).hasSize(1);
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, phone.refreshCookie())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, tablet.refreshCookie())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, laptop.refreshCookie())))
                .andExpect(status().isOk());
        assertThat(auditEventRepository.findAll()).anyMatch(event ->
                "SESSION_REVOKED".equals(event.getAction()) && member.getUsername().equals(event.getActorUsername()));
    }

    @Test
    void passwordChangeIsAudited() throws Exception {
        User member = user(Role.MEMBER, team());
        String token = signIn(member).accessToken();
        send(post("/api/auth/password"), Map.of("currentPassword", PASSWORD, "newPassword", "another-pass-1"), token)
                .andExpect(status().isOk());
        assertThat(auditEventRepository.findAll()).anyMatch(event ->
                "PASSWORD_CHANGED".equals(event.getAction()) && member.getId().equals(event.getTargetId())
                        && member.getId().equals(event.getActorId()));
    }
}
