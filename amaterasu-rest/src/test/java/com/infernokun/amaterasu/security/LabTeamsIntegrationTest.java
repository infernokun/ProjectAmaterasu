package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.infernokun.amaterasu.components.DataMigrations;
import com.infernokun.amaterasu.components.TeamResolver;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.models.entities.ctf.LabTeamMember;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.enums.TeamCredit;
import com.infernokun.amaterasu.models.enums.TeamMode;
import com.infernokun.amaterasu.repositories.ctf.LabTeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teams per lab: SITE uses the site teams, NONE has everyone play solo, LAB uses the lab's own teams.
 * Every team-related view, the environment copies and copy access resolve through TeamResolver.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LabTeamsIntegrationTest extends IntegrationSupport {
    @Autowired TeamResolver teamResolver;
    @Autowired LabTeamMemberRepository memberRepository;
    @Autowired DataMigrations migrations;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private Room lab(TeamMode mode, TeamCredit credit, Lab environment, String... facilitators) {
        return roomRepository.save(Room.builder().name(unique("lab")).scenario("Play together.").teamMode(mode)
                .teamCredit(credit).environment(environment)
                .environmentMode(environment == null ? EnvironmentMode.NONE : EnvironmentMode.PER_TEAM)
                .facilitators(new ArrayList<>(List.of(facilitators))).build());
    }

    private CTFEntity question(Room room, String title, String answer) {
        CTFEntity question = challenge(room, title, null, 0);
        Flag flag = new Flag();
        flag.setFlag(answer);
        flag.setCtfEntity(question);
        question.setFlags(new ArrayList<>(List.of(flag)));
        return ctfEntityRepository.save(question);
    }

    private String labTeam(Room room, String name, String token, User... members) throws Exception {
        JsonNode view = data(send(post("/api/room/" + room.getId() + "/teams"), Map.of("name", name), token)
                .andExpect(status().isCreated()).andReturn());
        String id = StreamSupport.stream(view.get("labTeams").spliterator(), false)
                .filter(team -> team.get("name").asText().equals(name)).findFirst().orElseThrow().get("id").asText();
        if (members.length > 0) {
            send(post("/api/room/" + room.getId() + "/teams/" + id + "/members"),
                    Map.of("userIds", List.of(members).stream().map(User::getId).toList()), token).andExpect(status().isOk());
        }
        return id;
    }

    private JsonNode overview(Room room, String token) throws Exception {
        return data(call(get("/api/room/" + room.getId() + "/overview"), token).andExpect(status().isOk()).andReturn());
    }

    private void answer(Room room, CTFEntity question, String flag, String token) throws Exception {
        send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", question.getId(), "flag", flag), token)
                .andExpect(status().isOk());
    }

    private static JsonNode find(JsonNode list, String field, String value) {
        return StreamSupport.stream(list.spliterator(), false).filter(node -> value.equals(node.path(field).asText()))
                .findFirst().orElse(null);
    }

    @Test
    void teamOfFollowsTheLabsMode() {
        Team site = team();
        User ana = user(Role.MEMBER, site);
        User ben = user(Role.MEMBER, null);
        Room siteLab = lab(TeamMode.SITE, TeamCredit.SEE_TEAMMATES, null);
        Room soloLab = lab(TeamMode.NONE, TeamCredit.SEE_TEAMMATES, null);
        Room ownLab = lab(TeamMode.LAB, TeamCredit.SEE_TEAMMATES, null);
        Team own = teamRepository.save(Team.builder().name("Own").labId(ownLab.getId()).build());
        memberRepository.save(LabTeamMember.builder()
                .roomId(ownLab.getId()).team(own).user(ben).build());

        assertThat(teamResolver.teamOf(siteLab, ana).getId()).isEqualTo(site.getId());
        assertThat(teamResolver.teamOf(siteLab, ben)).isNull();
        assertThat(teamResolver.teamOf(soloLab, ana)).isNull();
        // Lab teams are independent of site teams: Ben has no site team, Ana's site team does not count here
        assertThat(teamResolver.teamOf(ownLab, ben).getId()).isEqualTo(own.getId());
        assertThat(teamResolver.teamOf(ownLab, ana)).isNull();
        assertThat(teamResolver.teamsIn(ownLab)).extracting(Team::getId).containsExactly(own.getId());
        assertThat(teamResolver.teamsIn(soloLab)).isEmpty();
        assertThat(teamResolver.teamsIn(siteLab)).extracting(Team::getId).contains(site.getId()).doesNotContain(own.getId());
    }

    @Test
    void existingLabsKeepTheSiteTeams() throws Exception {
        Room room = lab(TeamMode.SITE, TeamCredit.SEE_TEAMMATES, null);
        jdbc.update("UPDATE room SET lab_team_mode = NULL WHERE id = ?", room.getId());
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTeamMode()).isNull();
        // Null reads as SITE even before the migration runs
        assertThat(roomRepository.findById(room.getId()).orElseThrow().effectiveTeamMode()).isEqualTo(TeamMode.SITE);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> migrations.siteTeamsForExistingLabs());

        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTeamMode()).isEqualTo(TeamMode.SITE);
        JsonNode created = data(send(post("/api/room"), Map.of("name", unique("lab"), "scenario", "x"), admin())
                .andExpect(status().isCreated()).andReturn());
        assertThat(created.get("teamMode").asText()).isEqualTo("SITE");
    }

    @Test
    void labTeamsDriveTheBoardTeammatesAndSharedSolves() throws Exception {
        Room room = lab(TeamMode.LAB, TeamCredit.SHARED_SOLVES, null);
        CTFEntity first = question(room, "First", "one");
        CTFEntity second = question(room, "Second", "two");
        Team siteTeam = team();
        User ana = user(Role.MEMBER, siteTeam);
        User ben = user(Role.MEMBER, null);
        User cal = user(Role.MEMBER, siteTeam);
        String admin = admin();
        String owls = labTeam(room, "Owls", admin, ana, ben);

        String anaToken = token(ana);
        String benToken = token(ben);
        String calToken = token(cal);
        assertThat(overview(room, benToken).get("me").get("teamName").asText()).isEqualTo("Owls");
        assertThat(overview(room, calToken).get("me").get("teamName").isNull()).isTrue();
        assertThat(overview(room, benToken).get("room").get("teamMode").asText()).isEqualTo("LAB");

        answer(room, first, "one", anaToken);
        // Shared solves: Ben's team already has it, his site team-mate Cal does not
        send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", first.getId(), "flag", "one"), benToken)
                .andExpect(status().isBadRequest());
        answer(room, first, "one", calToken);
        answer(room, second, "two", benToken);

        JsonNode firstView = find(overview(room, benToken).get("challenges"), "id", first.getId());
        assertThat(firstView.get("solvedByTeammates")).extracting(JsonNode::asText).containsExactly(ana.getUsername());

        JsonNode standings = data(call(get("/api/room/" + room.getId() + "/standings"), benToken).andExpect(status().isOk()).andReturn());
        JsonNode row = find(standings.get("teams"), "teamId", owls);
        assertThat(row.get("name").asText()).isEqualTo("Owls");
        assertThat(row.get("points").asInt()).isEqualTo(200);
        assertThat(row.get("teamSize").asInt()).isEqualTo(2);
        assertThat(find(standings.get("teams"), "teamId", siteTeam.getId())).isNull();
        assertThat(find(standings.get("players"), "userId", cal.getId()).get("teamId").isNull()).isTrue();

        JsonNode live = data(call(get("/api/room/" + room.getId() + "/live"), admin).andExpect(status().isOk()).andReturn());
        assertThat(live.get("teamCount").asInt()).isEqualTo(1);
        assertThat(find(live.get("players"), "userId", ben.getId()).get("teamName").asText()).isEqualTo("Owls");

        JsonNode card = find(data(call(get("/api/room/cards"), benToken).andExpect(status().isOk()).andReturn()), "id", room.getId());
        assertThat(card.get("teamName").asText()).isEqualTo("Owls");
        // The audit log files the solve under the lab team
        assertThat(events("FLAG_SOLVED", second.getId())).extracting(event -> event.getTeamId()).containsExactly(owls);
    }

    @Test
    void noneModeHasEveryonePlaySolo() throws Exception {
        Room room = lab(TeamMode.NONE, TeamCredit.SHARED_SOLVES, null);
        CTFEntity first = question(room, "First", "one");
        Team site = team();
        User ana = user(Role.MEMBER, site);
        User ben = user(Role.MEMBER, site);
        answer(room, first, "one", token(ana));
        // Same site team, but no teams count here, so Ben answers for himself
        answer(room, first, "one", token(ben));
        assertThat(overview(room, token(ben)).get("me").get("teamName").isNull()).isTrue();
        JsonNode standings = data(call(get("/api/room/" + room.getId() + "/standings"), token(ben)).andExpect(status().isOk()).andReturn());
        assertThat(standings.get("teams")).isEmpty();
    }

    @Test
    void labTeamsGetTheirOwnCopyAndOnlyTheyCanReachIt() throws Exception {
        RemoteServer server = server(ServerType.PROXMOX, true);
        Lab environment = lab(LabType.VIRTUAL_MACHINE, true);
        User facilitator = user(Role.FACILITATOR, null);
        Room room = lab(TeamMode.LAB, TeamCredit.SEE_TEAMMATES, environment, facilitator.getId());
        Team site = team();
        User ana = user(Role.MEMBER, site);
        User ben = user(Role.MEMBER, site);
        User cal = user(Role.MEMBER, null);
        String facilitatorToken = token(facilitator);
        String owlsId = labTeam(room, "Owls", facilitatorToken, ana);
        Team owls = teamRepository.findById(owlsId).orElseThrow();

        LabTracker owlsCopy = tracker(environment, owls, LabStatus.ACTIVE, server);
        LabTracker siteCopy = tracker(environment, site, LabStatus.ACTIVE, server);
        LabTracker solo = labTrackerRepository.save(LabTracker.builder().labStarted(environment).solo(true)
                .labStatus(LabStatus.ACTIVE).remoteServer(server).build());

        JsonNode anaEnv = data(call(get("/api/room/" + room.getId() + "/environment"), token(ana)).andExpect(status().isOk()).andReturn());
        assertThat(anaEnv.get("trackerId").asText()).isEqualTo(owlsCopy.getId());
        assertThat(anaEnv.get("copyLabel").asText()).isEqualTo("Owls's copy");
        // Ben is on Ana's site team but on no team in this lab: the solo copy
        JsonNode benEnv = data(call(get("/api/room/" + room.getId() + "/environment"), token(ben)).andExpect(status().isOk()).andReturn());
        assertThat(benEnv.get("trackerId").asText()).isEqualTo(solo.getId());
        assertThat(benEnv.get("copyLabel").asText()).isEqualTo("Solo copy");

        call(get("/api/lab-tracker/" + owlsCopy.getId()), token(ana)).andExpect(status().isOk());
        call(get("/api/lab-tracker/" + owlsCopy.getId()), token(ben)).andExpect(status().isForbidden());
        call(get("/api/lab-tracker/" + solo.getId()), token(ben)).andExpect(status().isOk());
        call(get("/api/lab-tracker/" + solo.getId()), token(cal)).andExpect(status().isOk());
        call(get("/api/lab-tracker/" + solo.getId()), token(ana)).andExpect(status().isForbidden());
        // The site team's copy is not what anyone uses in this lab
        call(get("/api/lab-tracker/" + siteCopy.getId()), token(ben)).andExpect(status().isForbidden());
        // Live updates follow the same rule
        assertThat(teamResolver.worksIn(ana, owlsCopy)).isTrue();
        assertThat(teamResolver.worksIn(ben, owlsCopy)).isFalse();
        assertThat(teamResolver.worksIn(ben, solo)).isTrue();
        assertThat(teamResolver.worksIn(ana, solo)).isFalse();
        assertThat(teamResolver.members(owls)).extracting(User::getId).containsExactly(ana.getId());

        JsonNode copies = data(call(get("/api/room/" + room.getId() + "/environment"), facilitatorToken)
                .andExpect(status().isOk()).andReturn()).get("copies");
        assertThat(copies).extracting(copy -> copy.get("target").asText()).containsExactly("team:" + owlsId, "solo");
        assertThat(copies.get(0).get("members").asInt()).isEqualTo(1);
        // A site team is not a copy target while the lab uses its own teams
        send(post("/api/room/" + room.getId() + "/environment/stop"), Map.of("target", "team:" + site.getId()), facilitatorToken)
                .andExpect(status().isBadRequest());

        // Leaving LAB while the Owls' copy is deployed would strand it
        send(put("/api/room/" + room.getId() + "/teams/mode"), Map.of("teamMode", "SITE"), facilitatorToken)
                .andExpect(status().isConflict());
        send(delete("/api/room/" + room.getId() + "/teams/" + owlsId), null, facilitatorToken).andExpect(status().isConflict());
        owlsCopy.setLabStatus(LabStatus.STOPPED);
        labTrackerRepository.save(owlsCopy);
        JsonNode view = data(send(put("/api/room/" + room.getId() + "/teams/mode"), Map.of("teamMode", "SITE"), facilitatorToken)
                .andExpect(status().isOk()).andReturn());
        assertThat(view.get("teamMode").asText()).isEqualTo("SITE");
        // SITE again: Ben is back on the site team's copy
        JsonNode benSite = data(call(get("/api/room/" + room.getId() + "/environment"), token(ben)).andExpect(status().isOk()).andReturn());
        assertThat(benSite.get("trackerId").asText()).isEqualTo(siteCopy.getId());
        call(get("/api/lab-tracker/" + siteCopy.getId()), token(ben)).andExpect(status().isOk());
        assertThat(events("LAB_TEAM_MODE_CHANGED", room.getId())).hasSize(1);
    }

    @Test
    void adminsAndTheLabsFacilitatorsRunLabTeamsMembersAndOtherFacilitatorsCannot() throws Exception {
        User facilitator = user(Role.FACILITATOR, null);
        User otherFacilitator = user(Role.FACILITATOR, null);
        Room room = lab(TeamMode.SITE, TeamCredit.SEE_TEAMMATES, null, facilitator.getId());
        User member = user(Role.MEMBER, null);
        String facilitatorToken = token(facilitator);

        call(get("/api/room/" + room.getId() + "/teams"), token(member)).andExpect(status().isForbidden());
        call(get("/api/room/" + room.getId() + "/teams"), token(otherFacilitator)).andExpect(status().isForbidden());
        send(put("/api/room/" + room.getId() + "/teams/mode"), Map.of("teamMode", "LAB"), token(otherFacilitator))
                .andExpect(status().isForbidden());

        JsonNode view = data(send(put("/api/room/" + room.getId() + "/teams/mode"), Map.of("teamMode", "LAB"), facilitatorToken)
                .andExpect(status().isOk()).andReturn());
        assertThat(view.get("teamMode").asText()).isEqualTo("LAB");
        String red = labTeam(room, "Red", facilitatorToken, member);
        send(post("/api/room/" + room.getId() + "/teams"), Map.of("name", "red"), facilitatorToken).andExpect(status().isBadRequest());
        String blue = labTeam(room, "Blue", facilitatorToken);

        // One team per lab: adding to Blue moves the member off Red
        send(post("/api/room/" + room.getId() + "/teams/" + blue + "/members"), Map.of("userIds", List.of(member.getId())),
                facilitatorToken).andExpect(status().isOk());
        assertThat(memberRepository.findByRoomIdAndUserId(room.getId(), member.getId()).orElseThrow().getTeam().getId()).isEqualTo(blue);
        assertThat(memberRepository.findByRoomId(room.getId())).hasSize(1);

        JsonNode renamed = data(send(put("/api/room/" + room.getId() + "/teams/" + red), Map.of("name", "Crimson"), facilitatorToken)
                .andExpect(status().isOk()).andReturn());
        assertThat(find(renamed.get("labTeams"), "id", red).get("name").asText()).isEqualTo("Crimson");

        JsonNode candidates = data(call(get("/api/room/" + room.getId() + "/teams/candidates"), facilitatorToken)
                .andExpect(status().isOk()).andReturn());
        assertThat(find(candidates, "userId", member.getId()).get("labTeamName").asText()).isEqualTo("Blue");

        send(delete("/api/room/" + room.getId() + "/teams/" + blue + "/members/" + member.getId()), null, facilitatorToken)
                .andExpect(status().isOk());
        assertThat(memberRepository.findByRoomId(room.getId())).isEmpty();
        send(delete("/api/room/" + room.getId() + "/teams/" + red), null, facilitatorToken).andExpect(status().isOk());
        assertThat(teamRepository.findById(red)).isEmpty();

        // Lab teams stay out of the site team list and the site team endpoints
        JsonNode siteTeams = data(call(get("/api/team"), admin()).andExpect(status().isOk()).andReturn());
        assertThat(find(siteTeams, "id", blue)).isNull();
        send(put("/api/team/" + blue), Map.of("name", "Hijack"), admin()).andExpect(status().isBadRequest());
        call(put("/api/user/team").param("userId", member.getId()).param("teamId", blue), admin()).andExpect(status().isNotFound());

        assertThat(events("LAB_TEAM_CREATED", room.getId())).hasSize(2);
        assertThat(events("LAB_TEAM_MEMBERS_CHANGED", room.getId())).hasSize(3);
        assertThat(events("LAB_TEAM_MEMBERS_CHANGED", room.getId())).allMatch(event -> facilitator.getUsername().equals(event.getActorUsername()));
    }

    @Test
    void copyFromSiteTeamsSeedsTheLabsTeams() throws Exception {
        Room room = lab(TeamMode.LAB, TeamCredit.SEE_TEAMMATES, null);
        Team foxes = teamRepository.save(Team.builder().name(unique("Foxes")).build());
        User ana = user(Role.MEMBER, foxes);
        User ben = user(Role.MEMBER, foxes);
        String admin = admin();
        String own = labTeam(room, "Already", admin, ben);

        JsonNode view = data(send(post("/api/room/" + room.getId() + "/teams/copy-site"), null, admin)
                .andExpect(status().isOk()).andReturn());
        JsonNode copied = find(view.get("labTeams"), "name", foxes.getName());
        assertThat(copied).isNotNull();
        assertThat(copied.get("id").asText()).isNotEqualTo(foxes.getId());
        assertThat(copied.get("members")).extracting(member -> member.get("userId").asText()).containsExactly(ana.getId());
        // Ben was already on a lab team and stays there
        assertThat(find(view.get("labTeams"), "id", own).get("members").get(0).get("userId").asText()).isEqualTo(ben.getId());
        assertThat(userRepository.findById(ana.getId()).orElseThrow().getTeam().getId()).isEqualTo(foxes.getId());

        // Running it again changes nothing
        JsonNode again = data(send(post("/api/room/" + room.getId() + "/teams/copy-site"), null, admin)
                .andExpect(status().isOk()).andReturn());
        assertThat(again.get("labTeams").size()).isEqualTo(view.get("labTeams").size());
        assertThat(events("LAB_TEAMS_COPIED", room.getId())).hasSize(2);
    }

    @Test
    void deletingALabTakesItsTeamsWithIt() throws Exception {
        Room room = lab(TeamMode.LAB, TeamCredit.SEE_TEAMMATES, null);
        String admin = admin();
        String team = labTeam(room, "Gone", admin, user(Role.MEMBER, null));
        send(delete("/api/room/" + room.getId()), null, admin).andExpect(status().isOk());
        assertThat(teamRepository.findById(team)).isEmpty();
        assertThat(memberRepository.findByRoomId(room.getId())).isEmpty();
    }
}
