package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.infernokun.amaterasu.components.DataMigrations;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.TeamCredit;
import com.infernokun.amaterasu.models.enums.TeamMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Teams inside a lab: joining, sizes and modes, teammates' solves, shared solves, the team board and the move from Admin teams. */
@SpringBootTest
@AutoConfigureMockMvc
class LabTeamsIntegrationTest extends IntegrationSupport {
    @Autowired DataMigrations migrations;

    private Room lab(TeamMode mode, Integer size, TeamCredit credit) {
        return roomRepository.save(Room.builder().name(unique("lab")).scenario("Play together.").teamMode(mode)
                .maxTeamSize(size).teamCredit(credit).build());
    }

    private CTFEntity question(Room room, String title, String answer, int points) {
        CTFEntity question = challenge(room, title, null, 0);
        question.setPoints(points);
        Flag flag = new Flag();
        flag.setFlag(answer);
        flag.setCtfEntity(question);
        question.setFlags(new ArrayList<>(List.of(flag)));
        return ctfEntityRepository.save(question);
    }

    private JsonNode join(Room room, String token, Map<String, ?> body) throws Exception {
        return data(send(post("/api/room/" + room.getId() + "/join"), body, token).andExpect(status().isOk()).andReturn());
    }

    private JsonNode overview(Room room, String token) throws Exception {
        return data(call(get("/api/room/" + room.getId() + "/overview"), token).andExpect(status().isOk()).andReturn());
    }

    @Test
    void playersJoinSoloOrOnATeamUpToItsSize() throws Exception {
        Room room = lab(TeamMode.TEAMS_OPTIONAL, 2, TeamCredit.SEE_TEAMMATES);
        String ana = token(user(Role.MEMBER, null));
        String ben = token(user(Role.MEMBER, null));
        String cy = token(user(Role.MEMBER, null));
        String dee = token(user(Role.MEMBER, null));

        assertThat(join(room, dee, Map.of()).get("teamId").isNull()).isTrue();
        String owls = join(room, ana, Map.of("newTeamName", "Night Owls")).get("teamId").asText();
        join(room, ben, Map.of("teamId", owls));
        send(post("/api/room/" + room.getId() + "/join"), Map.of("teamId", owls), cy).andExpect(status().isBadRequest());
        send(post("/api/room/" + room.getId() + "/join"), Map.of("newTeamName", "night owls"), cy).andExpect(status().isBadRequest());

        JsonNode teams = data(call(get("/api/room/" + room.getId() + "/teams"), cy).andExpect(status().isOk()).andReturn());
        assertThat(teams).hasSize(1);
        assertThat(teams.get(0).get("size").asInt()).isEqualTo(2);
        assertThat(teams.get(0).get("full").asBoolean()).isTrue();

        // Ben switches to solo; the team keeps Ana
        JsonNode moved = data(send(post("/api/room/" + room.getId() + "/team"), Map.of(), ben).andExpect(status().isOk()).andReturn());
        assertThat(moved.get("teamId").isNull()).isTrue();
        assertThat(overview(room, ana).get("me").get("teamName").asText()).isEqualTo("Night Owls");

        // Admin teams stay out of a lab's teams, and a lab team name does not block an Admin team
        assertThat(teamRepository.findById(owls).orElseThrow().getRoomId()).isEqualTo(room.getId());
        send(post("/api/team"), Map.of("name", "Night Owls"), admin()).andExpect(status().isCreated());
    }

    @Test
    void modesDecideWhoMayPlaySolo() throws Exception {
        Room required = lab(TeamMode.TEAMS_REQUIRED, 4, TeamCredit.SEE_TEAMMATES);
        User solo = user(Role.MEMBER, null);
        send(post("/api/room/" + required.getId() + "/join"), Map.of(), token(solo)).andExpect(status().isBadRequest());
        send(post("/api/room/join/" + required.getId() + "/" + solo.getId()), null, token(solo)).andExpect(status().isBadRequest());
        join(required, token(solo), Map.of("newTeamName", "Solo no more"));

        Room alone = lab(TeamMode.SOLO_ONLY, null, TeamCredit.SEE_TEAMMATES);
        send(post("/api/room/" + alone.getId() + "/join"), Map.of("newTeamName", "x"), token(solo)).andExpect(status().isBadRequest());
        join(alone, token(solo), Map.of());

        // One copy per team needs teams
        String creator = token(user(Role.CREATOR, null));
        Lab vm = labRepository.save(Lab.builder().name(unique("vm")).labType(LabType.VIRTUAL_MACHINE).capacity(2)
                .ready(true).published(true).build());
        send(put("/api/room/" + alone.getId()), Map.of("environmentMode", "PER_TEAM", "environmentId", vm.getId()), creator)
                .andExpect(status().isBadRequest());
        send(post("/api/room"), Map.of("name", unique("lab"), "scenario", "x", "teamMode", "TEAMS_REQUIRED", "maxTeamSize", 3,
                "teamCredit", "SHARED_SOLVES"), creator).andExpect(status().isCreated());
    }

    @Test
    void teammatesSeeEachOthersSolvesAndTheTeamBoardAddsTheirPoints() throws Exception {
        Room room = lab(TeamMode.TEAMS_OPTIONAL, 4, TeamCredit.SEE_TEAMMATES);
        CTFEntity first = question(room, "First", "one", 100);
        CTFEntity second = question(room, "Second", "two", 50);
        User ana = user(Role.MEMBER, null);
        User ben = user(Role.MEMBER, null);
        String anaToken = token(ana);
        String benToken = token(ben);
        String team = join(room, anaToken, Map.of("newTeamName", "Owls")).get("teamId").asText();
        join(room, benToken, Map.of("teamId", team));

        send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", first.getId(), "flag", "one"), anaToken).andExpect(status().isOk());
        send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", second.getId(), "flag", "two"), benToken).andExpect(status().isOk());

        JsonNode forBen = overview(room, benToken);
        JsonNode firstView = null;
        for (JsonNode view : forBen.get("challenges")) {
            if (view.get("title").asText().equals("First")) {
                firstView = view;
            }
        }
        assertThat(firstView.get("solved").asBoolean()).isFalse();
        assertThat(firstView.get("solvedByTeammates").get(0).asText()).isEqualTo(ana.getUsername());
        // Without shared solves Ben still answers it himself
        send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", first.getId(), "flag", "one"), benToken).andExpect(status().isOk());

        JsonNode standings = data(call(get("/api/room/" + room.getId() + "/standings"), benToken).andExpect(status().isOk()).andReturn());
        JsonNode row = standings.get("teams").get(0);
        assertThat(row.get("name").asText()).isEqualTo("Owls");
        assertThat(row.get("points").asInt()).isEqualTo(250);
        assertThat(row.get("members").asInt()).isEqualTo(2);
        assertThat(standings.get("players").get(0).get("teamName").asText()).isEqualTo("Owls");
    }

    @Test
    void withSharedSolvesOneSolveCountsForTheTeam() throws Exception {
        Room room = lab(TeamMode.TEAMS_OPTIONAL, 4, TeamCredit.SHARED_SOLVES);
        CTFEntity only = question(room, "Only", "answer", 100);
        User ana = user(Role.MEMBER, null);
        String anaToken = token(ana);
        String benToken = token(user(Role.MEMBER, null));
        String team = join(room, anaToken, Map.of("newTeamName", "Owls")).get("teamId").asText();
        join(room, benToken, Map.of("teamId", team));

        send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", only.getId(), "flag", "answer"), anaToken).andExpect(status().isOk());
        String refused = send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", only.getId(), "flag", "answer"), benToken)
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(refused).contains(ana.getUsername() + " already solved this for your team");
        assertThat(overview(room, benToken).get("room").get("teamCredit").asText()).isEqualTo("SHARED_SOLVES");
    }

    @Test
    void adminTeamsMoveIntoTheLabsTheyPlayedWithTheirCopies() {
        Team ember = team();
        User one = user(Role.MEMBER, ember);
        User two = user(Role.MEMBER, ember);
        Lab vm = labRepository.save(Lab.builder().name(unique("vm")).labType(LabType.VIRTUAL_MACHINE).capacity(2)
                .ready(true).published(true).build());
        Room old = roomRepository.save(Room.builder().name(unique("room")).scenario("x").environment(vm)
                .environmentMode(EnvironmentMode.PER_TEAM).teamMode(null).maxTeamSize(null).teamCredit(null).build());
        join(one, old);
        join(two, old);
        LabTracker copy = tracker(vm, ember, LabStatus.STOPPED, null);

        migrations.moveTeamsIntoLabs();

        List<RoomUser> seats = roomUserRepository.findByRoomId(old.getId());
        assertThat(seats).allSatisfy(seat -> {
            assertThat(seat.getTeam()).isNotNull();
            assertThat(seat.getTeam().getName()).isEqualTo(ember.getName());
            assertThat(seat.getTeam().getRoomId()).isEqualTo(old.getId());
        });
        Team labTeam = seats.getFirst().getTeam();
        assertThat(seats.get(1).getTeam().getId()).isEqualTo(labTeam.getId());
        assertThat(labTrackerRepository.findById(copy.getId()).orElseThrow().getLabOwner().getId()).isEqualTo(labTeam.getId());
        assertThat(teamRepository.findById(labTeam.getId()).orElseThrow().getTeamActiveLabs()).contains(copy.getId());
        assertThat(teamRepository.findById(ember.getId()).orElseThrow().getTeamActiveLabs()).doesNotContain(copy.getId());
        Room after = roomRepository.findById(old.getId()).orElseThrow();
        assertThat(after.getTeamMode()).isEqualTo(TeamMode.TEAMS_OPTIONAL);
        assertThat(after.getMaxTeamSize()).isEqualTo(4);

        // A second start changes nothing
        long teams = teamRepository.count();
        migrations.moveTeamsIntoLabs();
        assertThat(teamRepository.count()).isEqualTo(teams);
    }
}
