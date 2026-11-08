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
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.TeamCredit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Site-wide teams that admins run: no joining, teammates' solves, shared solves, the team board and the move from lab teams. */
@SpringBootTest
@AutoConfigureMockMvc
class SiteTeamsIntegrationTest extends IntegrationSupport {
    @Autowired DataMigrations migrations;
    @Autowired JdbcTemplate jdbc;

    private Room lab(TeamCredit credit) {
        return roomRepository.save(Room.builder().name(unique("lab")).scenario("Play together.").teamCredit(credit).build());
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

    private JsonNode overview(Room room, String token) throws Exception {
        return data(call(get("/api/room/" + room.getId() + "/overview"), token).andExpect(status().isOk()).andReturn());
    }

    @Test
    void adminsMakeTeamsAndPutPeopleOnThemPlayersCannot() throws Exception {
        User ana = user(Role.MEMBER, null);
        User ben = user(Role.MEMBER, null);
        String anaToken = token(ana);
        String name = unique("Owls");

        send(post("/api/team"), Map.of("name", name), anaToken).andExpect(status().isForbidden());
        String teamId = data(send(post("/api/team"), Map.of("name", name), admin()).andExpect(status().isCreated()).andReturn())
                .get("id").asText();
        send(post("/api/team"), Map.of("name", name.toUpperCase()), admin()).andExpect(status().isBadRequest());

        send(put("/api/user/team/bulk"), Map.of("userIds", List.of(ana.getId()), "teamId", teamId), anaToken)
                .andExpect(status().isForbidden());
        call(put("/api/user/team").param("userId", ana.getId()).param("teamId", teamId), anaToken)
                .andExpect(status().isForbidden());
        send(put("/api/user/team/bulk"), Map.of("userIds", List.of(ana.getId(), ben.getId()), "teamId", teamId), admin())
                .andExpect(status().isOk());

        JsonNode mine = data(call(get("/api/team/mine"), anaToken).andExpect(status().isOk()).andReturn());
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("name").asText()).isEqualTo(name);
        call(get("/api/team"), anaToken).andExpect(status().isForbidden());

        // One team at most: moving Ben takes him off the first
        String other = data(send(post("/api/team"), Map.of("name", unique("Larks")), admin()).andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        call(put("/api/user/team").param("userId", ben.getId()).param("teamId", other), admin()).andExpect(status().isOk());
        assertThat(userRepository.findById(ben.getId()).orElseThrow().getTeam().getId()).isEqualTo(other);
        call(put("/api/user/team").param("userId", ben.getId()), admin()).andExpect(status().isOk());
        assertThat(userRepository.findById(ben.getId()).orElseThrow().getTeam()).isNull();
        assertThat(data(call(get("/api/team/mine"), token(ben)).andExpect(status().isOk()).andReturn())).isEmpty();

        // The old ways in are gone
        Room room = lab(TeamCredit.SEE_TEAMMATES);
        send(post("/api/room/" + room.getId() + "/join"), Map.of("newTeamName", "Mine"), anaToken).andExpect(status().isNotFound());
        send(post("/api/room/join/" + room.getId() + "/" + ana.getId()), null, anaToken).andExpect(status().isNotFound());
        call(get("/api/room/" + room.getId() + "/teams"), anaToken).andExpect(status().isNotFound());
    }

    @Test
    void teammatesSeeEachOthersSolvesAndTheTeamBoardAddsTheirPoints() throws Exception {
        Room room = lab(TeamCredit.SEE_TEAMMATES);
        CTFEntity first = question(room, "First", "one", 100);
        CTFEntity second = question(room, "Second", "two", 50);
        Team owls = team();
        User ana = user(Role.MEMBER, owls);
        User ben = user(Role.MEMBER, owls);
        user(Role.MEMBER, owls);
        String anaToken = token(ana);
        String benToken = token(ben);
        String solo = token(user(Role.MEMBER, null));

        assertThat(overview(room, benToken).get("me").get("teamName").asText()).isEqualTo(owls.getName());
        assertThat(overview(room, benToken).get("me").has("joined")).isFalse();

        send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", first.getId(), "flag", "one"), anaToken).andExpect(status().isOk());
        send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", second.getId(), "flag", "two"), benToken).andExpect(status().isOk());
        send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", second.getId(), "flag", "two"), solo).andExpect(status().isOk());

        JsonNode firstView = null;
        for (JsonNode view : overview(room, benToken).get("challenges")) {
            if (view.get("title").asText().equals("First")) {
                firstView = view;
            }
        }
        assertThat(firstView.get("solved").asBoolean()).isFalse();
        assertThat(firstView.get("solvedByTeammates").get(0).asText()).isEqualTo(ana.getUsername());
        // Without shared solves Ben still answers it himself
        send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", first.getId(), "flag", "one"), benToken).andExpect(status().isOk());

        JsonNode standings = data(call(get("/api/room/" + room.getId() + "/standings"), solo).andExpect(status().isOk()).andReturn());
        assertThat(standings.get("players")).hasSize(3);
        JsonNode row = standings.get("teams").get(0);
        assertThat(row.get("name").asText()).isEqualTo(owls.getName());
        assertThat(row.get("points").asInt()).isEqualTo(250);
        assertThat(row.get("members").asInt()).isEqualTo(2);
        assertThat(row.get("teamSize").asInt()).isEqualTo(3);
        assertThat(standings.get("players").get(0).get("teamName").asText()).isEqualTo(owls.getName());
    }

    @Test
    void withSharedSolvesOneSolveCountsForTheTeam() throws Exception {
        Room room = lab(TeamCredit.SHARED_SOLVES);
        CTFEntity only = question(room, "Only", "answer", 100);
        Team owls = team();
        User ana = user(Role.MEMBER, owls);
        String anaToken = token(ana);
        String benToken = token(user(Role.MEMBER, owls));

        send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", only.getId(), "flag", "answer"), anaToken).andExpect(status().isOk());
        String refused = send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", only.getId(), "flag", "answer"), benToken)
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(refused).contains(ana.getUsername() + " already solved this for your team");
        assertThat(overview(room, benToken).get("room").get("teamCredit").asText()).isEqualTo("SHARED_SOLVES");
    }

    @Test
    void labTeamsBecomeSiteTeamsAndEachPlayerKeepsTheirMostRecent() {
        jdbc.execute("ALTER TABLE room_user ADD COLUMN IF NOT EXISTS lab_team_id VARCHAR(255)");
        jdbc.execute("ALTER TABLE team ADD COLUMN IF NOT EXISTS room_id VARCHAR(255)");
        jdbc.execute("ALTER TABLE room ADD COLUMN IF NOT EXISTS team_mode VARCHAR(255)");
        Lab vm = labRepository.save(Lab.builder().name(unique("vm")).labType(LabType.VIRTUAL_MACHINE).capacity(2)
                .ready(true).published(true).build());
        Room hunt = lab(TeamCredit.SEE_TEAMMATES);
        Room onCall = lab(TeamCredit.SEE_TEAMMATES);
        String emberName = unique("Ember");
        Team ember = teamRepository.save(Team.builder().name(emberName).build());
        Team emberInHunt = labTeam(emberName.toLowerCase(), hunt);
        Team check = labTeam(unique("Content check"), onCall);
        Team lonely = labTeam(unique("Lonely"), hunt);
        User one = user(Role.MEMBER, ember);
        User two = user(Role.MEMBER, ember);
        User three = user(Role.MEMBER, null);
        LocalDateTime now = LocalDateTime.now();
        // Put on Ember by an admin a while ago
        jdbc.update("UPDATE users SET updated_at = ? WHERE id IN (?, ?)", now.minusHours(5), one.getId(), two.getId());
        seat(one, hunt, emberInHunt, now.minusHours(3));
        seat(two, hunt, emberInHunt, now.minusHours(3));
        seat(one, onCall, check, now.minusHours(1));
        seat(three, hunt, lonely, now.minusHours(2));
        LabTracker huntCopy = tracker(vm, emberInHunt, LabStatus.ACTIVE, null);
        LabTracker checkCopy = tracker(vm, check, LabStatus.STOPPED, null);
        LabTracker orphan = labTrackerRepository.save(LabTracker.builder().labStarted(vm).labStatus(LabStatus.STOPPED).build());

        migrations.siteWideTeams();

        assertThat(teamRepository.findById(emberInHunt.getId())).isEmpty();
        assertThat(teamRepository.findById(check.getId())).isPresent();
        assertThat(teamRepository.findById(lonely.getId())).isPresent();
        // A running copy stays up and moves to the site team of the same name
        LabTracker moved = labTrackerRepository.findById(huntCopy.getId()).orElseThrow();
        assertThat(moved.getLabOwner().getId()).isEqualTo(ember.getId());
        assertThat(moved.getLabStatus()).isEqualTo(LabStatus.ACTIVE);
        assertThat(labTrackerRepository.findById(checkCopy.getId()).orElseThrow().getLabOwner().getId()).isEqualTo(check.getId());
        assertThat(labTrackerRepository.findById(orphan.getId()).orElseThrow().isSolo()).isTrue();
        // One joined Content check last, so keeps it; two stays on Ember; three's lab team is now site-wide
        assertThat(userRepository.findById(one.getId()).orElseThrow().getTeam().getId()).isEqualTo(check.getId());
        assertThat(userRepository.findById(two.getId()).orElseThrow().getTeam().getId()).isEqualTo(ember.getId());
        assertThat(userRepository.findById(three.getId()).orElseThrow().getTeam().getId()).isEqualTo(lonely.getId());
        assertThat(column("room_user", "lab_team_id")).isZero();
        assertThat(column("team", "room_id")).isZero();
        assertThat(column("room", "team_mode")).isZero();

        // A second start changes nothing
        long teams = teamRepository.count();
        migrations.siteWideTeams();
        assertThat(teamRepository.count()).isEqualTo(teams);
    }

    private Team labTeam(String name, Room room) {
        Team team = teamRepository.save(Team.builder().name(name).build());
        jdbc.update("UPDATE team SET room_id = ? WHERE id = ?", room.getId(), team.getId());
        return team;
    }

    private void seat(User user, Room room, Team team, LocalDateTime at) {
        RoomUser seat = join(user, room);
        jdbc.update("UPDATE room_user SET lab_team_id = ?, updated_at = ? WHERE id = ?", team.getId(), at, seat.getId());
    }

    private int column(String table, String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = ?"
                + " AND LOWER(column_name) = ?", Integer.class, table, column);
    }
}
