package com.infernokun.amaterasu.play;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.models.entities.ctf.Hint;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.repositories.ctf.CTFEntityRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomRepository;
import com.infernokun.amaterasu.repositories.lab.LabRepository;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.repositories.lab.RemoteServerRepository;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.ServerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Room views, the scoreboard, the facilitator's live view and awarding points, with their role rules. */
@SpringBootTest
@AutoConfigureMockMvc
class PlayEndpointsIntegrationTest {
    private static final String PASSWORD = "password123";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private CTFEntityRepository ctfEntityRepository;
    @Autowired private LabRepository labRepository;
    @Autowired private LabTrackerRepository labTrackerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RemoteServerRepository remoteServerRepository;

    private Team red;
    private Team blue;
    private User redMember;
    private User blueMember;
    private User facilitator;
    private Room room;
    private CTFEntity open;
    private CTFEntity locked;

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private User user(Role role, Team team) {
        User user = new User(unique("p"), passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setTeam(team);
        return userRepository.save(user);
    }

    private Team team(String name) {
        Team team = new Team();
        team.setName(unique(name));
        return teamRepository.save(team);
    }

    private String token(User user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", user.getUsername(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }

    private String adminToken() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test_admin\",\"password\":\"test_password\"}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }

    private ResultActions getAs(String url, String token) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    private ResultActions postAs(String url, String token, Object body) throws Exception {
        return mvc.perform(post(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private CTFEntity challenge(String title, String flag, int points, LocalDateTime release) {
        CTFEntity challenge = new CTFEntity();
        challenge.setQuestion(title);
        challenge.setDescription("Find the " + title);
        challenge.setCategory("Web");
        challenge.setPoints(points);
        challenge.setMaxAttempts(5);
        challenge.setRoom(room);
        challenge.setReleaseDate(release);
        Flag answer = new Flag();
        answer.setFlag(flag);
        answer.setCtfEntity(challenge);
        challenge.setFlags(List.of(answer));
        Hint hint = new Hint();
        hint.setHint("Look at the headers");
        hint.setCost(10);
        hint.setOrderIndex(1);
        hint.setUnlockAfterAttempts(0);
        hint.setCtfEntity(challenge);
        challenge.setHints(List.of(hint));
        return ctfEntityRepository.save(challenge);
    }

    @BeforeEach
    void setUp() throws Exception {
        red = team("red");
        blue = team("blue");
        redMember = user(Role.MEMBER, red);
        blueMember = user(Role.MEMBER, blue);
        facilitator = user(Role.FACILITATOR, null);
        room = roomRepository.save(Room.builder().name(unique("room"))
                .facilitators(new java.util.ArrayList<>(List.of(facilitator.getId()))).build());
        open = challenge("Header Hunt", "AMA{headers}", 200, LocalDateTime.now().minusHours(1));
        locked = challenge("Later", "AMA{later}", 300, LocalDateTime.now().plusHours(2));
        open.setAttachments(null);
        open = ctfEntityRepository.save(open);
        // Each joins on a team inside the room named like their Admin team
        for (User player : List.of(redMember, blueMember)) {
            postAs("/api/room/" + room.getId() + "/join", token(player), Map.of("newTeamName", player.getTeam().getName()))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void liveViewAndAllTeamsLabsAreForStaffOnly() throws Exception {
        String member = token(redMember);
        getAs("/api/room/" + room.getId() + "/live", member).andExpect(status().isForbidden());
        getAs("/api/lab-tracker/overview", member).andExpect(status().isForbidden());

        String staff = token(facilitator);
        getAs("/api/room/" + room.getId() + "/live", staff).andExpect(status().isOk());
        getAs("/api/lab-tracker/overview", staff).andExpect(status().isOk());
        getAs("/api/room/" + room.getId() + "/live", adminToken()).andExpect(status().isOk());
    }

    @Test
    void facilitatorsAwardPointsWithAReasonAndMembersCannot() throws Exception {
        String url = "/api/room/add-points/" + room.getId() + "/" + blueMember.getId();
        postAs(url, token(redMember), Map.of("points", 100, "reason", "Me")).andExpect(status().isForbidden());

        String staff = token(facilitator);
        postAs(url, staff, Map.of("points", 0, "reason", "Nothing")).andExpect(status().isBadRequest());
        postAs(url, staff, Map.of("points", 50, "reason", " ")).andExpect(status().isBadRequest());
        postAs(url, staff, Map.of("points", 50_000, "reason", "Too much")).andExpect(status().isBadRequest());
        assertThat(data(postAs(url, staff, Map.of("points", 50, "reason", "Writeup bonus"))
                .andExpect(status().isOk())).get("points").asInt()).isEqualTo(50);

        JsonNode feed = data(getAs("/api/room/" + room.getId() + "/live", staff)).get("feed");
        assertThat(feed.get(0).get("kind").asText()).isEqualTo("AWARD");
        assertThat(feed.get(0).get("who").asText()).isEqualTo(facilitator.getUsername());
        assertThat(feed.get(0).get("detail").asText()).isEqualTo("Writeup bonus");
        assertThat(feed.get(0).get("at").asText()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
    }

    @Test
    void overviewHidesLockedChallengesAndHintTextUntilUnlocked() throws Exception {
        String member = token(redMember);
        JsonNode overview = data(getAs("/api/room/" + room.getId() + "/overview", member).andExpect(status().isOk()));
        assertThat(overview.get("playerCount").asInt()).isEqualTo(2);
        assertThat(overview.get("me").get("joined").asBoolean()).isTrue();

        JsonNode challenges = overview.get("challenges");
        JsonNode openView = challenges.get(0).get("id").asText().equals(open.getId()) ? challenges.get(0) : challenges.get(1);
        JsonNode lockedView = openView == challenges.get(0) ? challenges.get(1) : challenges.get(0);
        assertThat(openView.get("state").asText()).isEqualTo("OPEN");
        assertThat(openView.get("hints").get(0).get("text").isNull()).isTrue();
        assertThat(lockedView.get("state").asText()).isEqualTo("LOCKED");
        assertThat(lockedView.get("description").isNull()).isTrue();

        postAs("/api/answer", member, Map.of("roomId", room.getId(), "questionId", locked.getId(), "flag", "AMA{later}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void standingsRankPlayersAndTeams() throws Exception {
        String member = token(redMember);
        postAs("/api/answer", member, Map.of("roomId", room.getId(), "questionId", open.getId(), "flag", "nope"))
                .andExpect(status().isOk());
        postAs("/api/answer", member, Map.of("roomId", room.getId(), "questionId", open.getId(), "flag", "AMA{headers}"))
                .andExpect(status().isOk());

        JsonNode standings = data(getAs("/api/room/" + room.getId() + "/standings", token(blueMember)).andExpect(status().isOk()));
        JsonNode leader = standings.get("players").get(0);
        assertThat(leader.get("username").asText()).isEqualTo(redMember.getUsername());
        assertThat(leader.get("rank").asInt()).isEqualTo(1);
        assertThat(leader.get("points").asInt()).isEqualTo(200);
        assertThat(leader.get("solves").asInt()).isEqualTo(1);
        assertThat(leader.get("wrongAttempts").asInt()).isEqualTo(1);
        assertThat(standings.get("teams").get(0).get("name").asText()).isEqualTo(red.getName());
        assertThat(standings.get("teams").get(0).get("points").asInt()).isEqualTo(200);

        JsonNode overview = data(getAs("/api/room/" + room.getId() + "/overview", member));
        assertThat(overview.get("me").get("rank").asInt()).isEqualTo(1);
        assertThat(overview.get("me").get("solved").asInt()).isEqualTo(1);
    }

    @Test
    void onlyTheOwningTeamRestartsALabService() throws Exception {
        Lab lab = labRepository.save(Lab.builder().name(unique("lab")).labType(LabType.DOCKER_COMPOSE).build());
        LabTracker tracker = labTrackerRepository.save(LabTracker.builder()
                .labStarted(lab).labOwner(red).labStatus(LabStatus.ACTIVE).build());

        postAs("/api/lab-tracker/" + tracker.getId() + "/restart/web", token(blueMember), Map.of())
                .andExpect(status().isForbidden());
        // The owning team gets through the access check; this lab has no server to restart on
        postAs("/api/lab-tracker/" + tracker.getId() + "/restart/web", token(redMember), Map.of())
                .andExpect(status().isBadRequest());
    }

    @Test
    void startingACatalogLabWithoutATrackerSaysWhyItCannotStart() throws Exception {
        Lab lab = labRepository.save(Lab.builder().name(unique("lab")).labType(LabType.DOCKER_COMPOSE).ready(true)
                .published(true).build());
        RemoteServer server = remoteServerRepository.save(RemoteServer.builder().name(unique("dock")).ipAddress("10.0.0.1")
                .serverType(ServerType.DOCKER_HOST).build());

        JsonNode body = objectMapper.readTree(postAs("/api/labs/start", token(redMember),
                        Map.of("labId", lab.getId(), "remoteServerId", server.getId()))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString());
        assertThat(body.get("data").get("output").asText()).contains("is not ACTIVE");
    }

    @Test
    void aLabWithNoServerCannotPauseButStopsCleanly() throws Exception {
        Lab lab = labRepository.save(Lab.builder().name(unique("lab")).labType(LabType.DOCKER_COMPOSE).build());
        LabTracker tracker = labTrackerRepository.save(LabTracker.builder()
                .labStarted(lab).labOwner(red).labStatus(LabStatus.ACTIVE).build());
        Map<String, String> request = Map.of("labId", lab.getId(), "labTrackerId", tracker.getId());

        String paused = postAs("/api/labs/pause", token(redMember), request).andReturn().getResponse().getContentAsString();
        assertThat(paused).contains("not deployed on a server").doesNotContain("Exception");

        postAs("/api/labs/stop", token(redMember), request).andExpect(status().isOk());
        assertThat(labTrackerRepository.findById(tracker.getId()).orElseThrow().getLabStatus()).isEqualTo(LabStatus.STOPPED);
    }
}
