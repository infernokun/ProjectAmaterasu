package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.infernokun.amaterasu.components.DataMigrations;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.services.alt.LabActionService;
import com.infernokun.amaterasu.utils.AESUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Labs with a scenario and an environment: none, one shared copy, or a copy per team. */
@SpringBootTest
@AutoConfigureMockMvc
class LabEnvironmentIntegrationTest extends IntegrationSupport {
    private static final String VM_PASSWORD = "q4zt-m8wr-2kfn-p7xc";

    @MockitoBean LabActionService labActionService;
    @Autowired AESUtil aesUtil;
    @Autowired DataMigrations migrations;

    private RemoteServer pve;
    private Lab vmLab;
    private User facilitator;

    @BeforeEach
    void setUp() {
        pve = server(ServerType.PROXMOX, true);
        vmLab = labRepository.save(Lab.builder().name(unique("vm")).labType(LabType.VIRTUAL_MACHINE).capacity(5)
                .ready(true).published(true).vmIds(new ArrayList<>(List.of(9003, 9004))).build());
        facilitator = user(Role.FACILITATOR, null);
        when(labActionService.startLab(any(), any())).thenAnswer(call -> {
            LabTracker tracker = call.getArgument(0);
            tracker.setVms(new ArrayList<>(List.of(ProxmoxVM.builder().vmid(150).name("hunt-bastion").status("running")
                    .role("bastion").ciuser("player").guestAgent(true).ready(true).accessAddress("10.0.0.77")
                    .labSubnet("172.16.5.0/24").build())));
            tracker.setAccessPassword(aesUtil.encrypt(VM_PASSWORD));
            return new LabActionResult(true, tracker, "started");
        });
        when(labActionService.teardownLab(any(), any())).thenAnswer(call -> {
            LabTracker tracker = call.getArgument(0);
            tracker.setVms(new ArrayList<>());
            return new LabActionResult(true, tracker, "torn down");
        });
    }

    private Room lab(EnvironmentMode mode, Lab environment) {
        return roomRepository.save(Room.builder().name(unique("lab")).scenario("## Situation\nA box went quiet.")
                .environment(environment).environmentMode(mode)
                .facilitators(new ArrayList<>(List.of(facilitator.getId()))).build());
    }

    private JsonNode environment(Room room, String token) throws Exception {
        return data(call(get("/api/room/" + room.getId() + "/environment"), token).andExpect(status().isOk()).andReturn());
    }

    @Test
    void aLabNeedsAScenarioAndAnEnvironmentForItsMode() throws Exception {
        String creator = token(user(Role.CREATOR, null));
        send(post("/api/room"), Map.of("name", unique("lab")), creator).andExpect(status().isBadRequest());
        send(post("/api/room"), Map.of("name", unique("lab"), "scenario", "Go", "environmentMode", "SHARED"), creator)
                .andExpect(status().isBadRequest());
        JsonNode made = data(send(post("/api/room"), Map.of("name", unique("lab"), "scenario", "## Situation\nGo",
                "environmentMode", "SHARED", "environmentId", vmLab.getId()), creator).andExpect(status().isCreated()).andReturn());
        assertThat(made.get("environmentId").asText()).isEqualTo(vmLab.getId());
        assertThat(made.get("environmentMode").asText()).isEqualTo("SHARED");

        JsonNode plain = data(send(post("/api/room"), Map.of("name", unique("lab"), "scenario", "Just questions"), creator)
                .andExpect(status().isCreated()).andReturn());
        assertThat(plain.get("environmentMode").asText()).isEqualTo("NONE");
        assertThat(plain.get("environmentId").isNull()).isTrue();
        send(put("/api/room/" + plain.get("id").asText()), Map.of("scenario", " "), creator).andExpect(status().isBadRequest());
    }

    @Test
    void aLabWithoutAnEnvironmentTakesAnswersAndShowsNoSetup() throws Exception {
        Room room = lab(EnvironmentMode.NONE, null);
        CTFEntity question = challenge(room, "Warm up", null, 0);
        Flag flag = new Flag();
        flag.setFlag("CTF{warm}");
        flag.setCtfEntity(question);
        question.setFlags(new ArrayList<>(List.of(flag)));
        ctfEntityRepository.save(question);
        User player = user(Role.MEMBER, null);
        join(player, room);
        String token = token(player);

        JsonNode card = cardFor(room, token);
        assertThat(card.get("environment").isNull()).isTrue();
        assertThat(card.get("summary").asText()).isEqualTo("A box went quiet.");
        assertThat(card.get("questionCount").asInt()).isEqualTo(1);

        JsonNode answer = data(send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", question.getId(),
                "flag", "CTF{warm}"), token).andExpect(status().isOk()).andReturn());
        assertThat(answer.get("correct").asBoolean()).isTrue();
    }

    @Test
    void facilitatorsRunTheSharedCopyAndJoinedPlayersReadItsLogin() throws Exception {
        Room room = lab(EnvironmentMode.SHARED, vmLab);
        User player = user(Role.MEMBER, team());
        join(player, room);
        String playerToken = token(player);
        String facilitatorToken = token(facilitator);

        assertThat(environment(room, playerToken).get("status").asText()).isEqualTo("NOT_RUNNING");
        assertThat(environment(room, playerToken).get("canControl").asBoolean()).isFalse();
        assertThat(environment(room, facilitatorToken).get("canControl").asBoolean()).isTrue();
        send(post("/api/room/" + room.getId() + "/environment/start"), null, playerToken).andExpect(status().isForbidden());
        send(post("/api/room/" + room.getId() + "/environment/start"), null, token(user(Role.FACILITATOR, null)))
                .andExpect(status().isForbidden());

        JsonNode started = data(send(post("/api/room/" + room.getId() + "/environment/start"),
                Map.of("remoteServerId", pve.getId()), facilitatorToken)
                .andExpect(status().isOk()).andReturn());
        assertThat(started.get("successful").asBoolean()).isTrue();
        JsonNode env = started.get("environment");
        assertThat(env.get("status").asText()).isEqualTo("RUNNING");
        assertThat(env.get("mode").asText()).isEqualTo("SHARED");
        String trackerId = env.get("trackerId").asText();
        LabTracker tracker = labTrackerRepository.findById(trackerId).orElseThrow();
        assertThat(tracker.getRoomId()).isEqualTo(room.getId());
        assertThat(tracker.getLabOwner()).isNull();
        assertThat(tracker.getRemoteServer().getId()).isEqualTo(pve.getId());

        // Everyone sees the same copy and status
        assertThat(environment(room, playerToken).get("trackerId").asText()).isEqualTo(trackerId);
        assertThat(cardFor(room, playerToken).get("environment").get("status").asText()).isEqualTo("RUNNING");
        assertThat(data(call(get("/api/room/" + room.getId() + "/overview"), playerToken).andExpect(status().isOk()).andReturn())
                .get("environment").get("status").asText()).isEqualTo("RUNNING");

        JsonNode login = data(call(get("/api/lab-tracker/" + trackerId + "/access"), playerToken).andExpect(status().isOk()).andReturn());
        assertThat(login.get("password").asText()).isEqualTo(VM_PASSWORD);
        assertThat(login.get("address").asText()).isEqualTo("10.0.0.77");
        call(get("/api/lab-tracker/" + trackerId + "/access"), token(user(Role.MEMBER, team()))).andExpect(status().isForbidden());

        send(post("/api/room/" + room.getId() + "/environment/stop"), null, playerToken).andExpect(status().isForbidden());
        JsonNode stopped = data(send(post("/api/room/" + room.getId() + "/environment/stop"), null, facilitatorToken)
                .andExpect(status().isOk()).andReturn());
        assertThat(stopped.get("environment").get("status").asText()).isEqualTo("NOT_RUNNING");
        assertThat(labTrackerRepository.findById(trackerId).orElseThrow().getLabStatus()).isEqualTo(LabStatus.STOPPED);
    }

    @Test
    void theSharedCopyMustStopBeforeTheEnvironmentChanges() throws Exception {
        Room room = lab(EnvironmentMode.SHARED, vmLab);
        send(post("/api/room/" + room.getId() + "/environment/start"), null, token(facilitator)).andExpect(status().isOk());
        String creator = token(user(Role.CREATOR, null));
        send(put("/api/room/" + room.getId()), Map.of("environmentMode", "NONE"), creator).andExpect(status().isConflict());
        send(post("/api/room/" + room.getId() + "/environment/stop"), null, admin()).andExpect(status().isOk());
        send(put("/api/room/" + room.getId()), Map.of("environmentMode", "NONE"), creator).andExpect(status().isOk());
        assertThat(roomRepository.findById(room.getId()).orElseThrow().effectiveMode()).isEqualTo(EnvironmentMode.NONE);
    }

    @Test
    void eachTeamInTheLabStartsItsOwnCopy() throws Exception {
        Room room = lab(EnvironmentMode.PER_TEAM, vmLab);
        User redPlayer = user(Role.MEMBER, team());
        User redMate = user(Role.MEMBER, null);
        User bluePlayer = user(Role.MEMBER, team());
        String solo = token(user(Role.MEMBER, null));
        send(post("/api/room/" + room.getId() + "/join"), Map.of(), solo).andExpect(status().isOk());

        assertThat(environment(room, solo).get("note").asText()).contains("Join or create a team");
        send(post("/api/room/" + room.getId() + "/environment/start"), null, solo).andExpect(status().isBadRequest());

        String redTeam = data(send(post("/api/room/" + room.getId() + "/join"), Map.of("newTeamName", "Red"), token(redPlayer))
                .andExpect(status().isOk()).andReturn()).get("teamId").asText();
        send(post("/api/room/" + room.getId() + "/join"), Map.of("teamId", redTeam), token(redMate)).andExpect(status().isOk());
        send(post("/api/room/" + room.getId() + "/join"), Map.of("newTeamName", "Blue"), token(bluePlayer)).andExpect(status().isOk());

        JsonNode started = data(send(post("/api/room/" + room.getId() + "/environment/start"),
                Map.of("remoteServerId", pve.getId()), token(redPlayer)).andExpect(status().isOk()).andReturn());
        String trackerId = started.get("environment").get("trackerId").asText();
        LabTracker tracker = labTrackerRepository.findById(trackerId).orElseThrow();
        assertThat(tracker.getLabOwner().getId()).isEqualTo(redTeam);
        assertThat(tracker.getLabOwner().getRoomId()).isEqualTo(room.getId());
        assertThat(tracker.getRoomId()).isNull();

        // A teammate works in the same copy
        assertThat(environment(room, token(redMate)).get("trackerId").asText()).isEqualTo(trackerId);
        call(get("/api/lab-tracker/" + trackerId + "/access"), token(redMate)).andExpect(status().isOk());
        JsonNode mine = data(call(get("/api/lab-tracker"), token(redMate)).andExpect(status().isOk()).andReturn());
        assertThat(mine.toString()).contains(trackerId);

        JsonNode blue = environment(room, token(bluePlayer));
        assertThat(blue.get("status").asText()).isEqualTo("NOT_RUNNING");
        assertThat(blue.get("copiesRunning").asInt()).isEqualTo(1);
        call(get("/api/lab-tracker/" + trackerId + "/access"), token(bluePlayer)).andExpect(status().isForbidden());
    }

    @Test
    void deletingAnEnvironmentLeavesItsLabsWithout() throws Exception {
        Lab doomed = labRepository.save(Lab.builder().name(unique("vm")).labType(LabType.VIRTUAL_MACHINE).capacity(1)
                .ready(true).published(true).build());
        Room room = lab(EnvironmentMode.PER_TEAM, doomed);
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/labs/" + doomed.getId()),
                token(user(Role.CREATOR, null))).andExpect(status().isOk());
        Room after = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(after.getEnvironment()).isNull();
        assertThat(after.effectiveMode()).isEqualTo(EnvironmentMode.NONE);
    }

    @Test
    void existingRoomsBecomeLabsWithTheEnvironmentTheirQuestionsUse() {
        Lab other = labRepository.save(Lab.builder().name(unique("vm")).labType(LabType.VIRTUAL_MACHINE).capacity(1)
                .ready(true).published(true).build());
        Lab unused = labRepository.save(Lab.builder().name(unique("vm")).labType(LabType.VIRTUAL_MACHINE).capacity(1)
                .description("Ping the gateway.").ready(true).published(true).build());
        Room old = roomRepository.save(Room.builder().name(unique("room")).environmentMode(null).build());
        challenge(old, "one", other.getId(), 0);
        challenge(old, "two", vmLab.getId(), 1);
        challenge(old, "three", vmLab.getId(), 2);
        Room plain = roomRepository.save(Room.builder().name(unique("room")).environmentMode(null).build());
        challenge(plain, "no lab", null, 0);

        migrations.mergeRoomsAndLabs();

        Room paired = roomRepository.findById(old.getId()).orElseThrow();
        assertThat(paired.getEnvironmentId()).isEqualTo(vmLab.getId());
        assertThat(paired.getEnvironmentMode()).isEqualTo(EnvironmentMode.PER_TEAM);
        assertThat(paired.getScenario()).contains("## Situation").contains("3 questions").contains("{{address}}");
        Room none = roomRepository.findById(plain.getId()).orElseThrow();
        assertThat(none.getEnvironmentMode()).isEqualTo(EnvironmentMode.NONE);
        assertThat(none.getScenario()).isNotBlank();
        Room own = roomRepository.findAll().stream().filter(room -> unused.getId().equals(room.getEnvironmentId()))
                .findFirst().orElseThrow();
        assertThat(own.getName()).isEqualTo(unused.getName());
        assertThat(own.getScenario()).contains("Ping the gateway.");

        // A second start changes nothing
        long rooms = roomRepository.count();
        migrations.mergeRoomsAndLabs();
        assertThat(roomRepository.count()).isEqualTo(rooms);
    }

    private JsonNode cardFor(Room room, String token) throws Exception {
        JsonNode cards = data(call(get("/api/room/cards"), token).andExpect(status().isOk()).andReturn());
        Map<String, JsonNode> byId = new HashMap<>();
        cards.forEach(card -> byId.put(card.get("id").asText(), card));
        return byId.get(room.getId());
    }
}
