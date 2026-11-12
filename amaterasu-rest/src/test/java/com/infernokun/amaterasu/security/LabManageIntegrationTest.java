package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.infernokun.amaterasu.models.PointsHistoryEntry;
import com.infernokun.amaterasu.models.entities.AuditEvent;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.models.entities.ctf.Hint;
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
import com.infernokun.amaterasu.repositories.ctf.CTFAnswerRepository;
import com.infernokun.amaterasu.repositories.ctf.FlagRepository;
import com.infernokun.amaterasu.repositories.ctf.HintRepository;
import com.infernokun.amaterasu.websocket.RoomActivityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Managing one lab: submissions with near misses, credit, accepted answers, awards, resets and the read views. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class LabManageIntegrationTest extends IntegrationSupport {
    @Autowired CTFAnswerRepository answerRepository;
    @Autowired FlagRepository flagRepository;
    @Autowired HintRepository hintRepository;
    @Autowired ApplicationEvents applicationEvents;

    private User facilitator;
    private String facilitatorToken;

    @BeforeEach
    void setUp() throws Exception {
        facilitator = user(Role.FACILITATOR, null);
        facilitatorToken = token(facilitator);
    }

    private Room lab(TeamCredit credit) {
        return roomRepository.save(Room.builder().name(unique("lab")).scenario("Find the flags.").surroundTag("AMATERASU{x}")
                .teamCredit(credit).teamMode(TeamMode.SITE)
                .facilitators(new ArrayList<>(List.of(facilitator.getId()))).build());
    }

    private CTFEntity question(Room room, String title, String answer, int points, int sortOrder) {
        CTFEntity question = challenge(room, title, null, sortOrder);
        question.setPoints(points);
        question.setMaxAttempts(10);
        Flag flag = new Flag();
        flag.setFlag(answer);
        flag.setCaseSensitive(true);
        flag.setCtfEntity(question);
        question.setFlags(new ArrayList<>(List.of(flag)));
        return ctfEntityRepository.save(question);
    }

    private void answer(Room room, CTFEntity question, String flag, String token) throws Exception {
        send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", question.getId(), "flag", flag), token)
                .andExpect(status().isOk());
    }

    private JsonNode manage(Room room, String path, String token) throws Exception {
        return data(call(get("/api/room/" + room.getId() + "/manage" + path), token).andExpect(status().isOk()).andReturn());
    }

    private JsonNode postOk(Room room, String path, Object body) throws Exception {
        return data(send(post("/api/room/" + room.getId() + "/manage" + path), body, facilitatorToken)
                .andExpect(status().isOk()).andReturn());
    }

    private int points(Room room, User user) {
        return roomUserRepository.findByRoomId(room.getId()).stream().filter(seat -> seat.getUser().getId().equals(user.getId()))
                .findFirst().map(RoomUser::getPoints).orElse(0);
    }

    private RoomUser seat(Room room, User user) {
        return roomUserRepository.findByRoomId(room.getId()).stream().filter(s -> s.getUser().getId().equals(user.getId()))
                .findFirst().orElseThrow();
    }

    private static List<JsonNode> list(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        return list(array).stream().filter(node -> value.equals(node.path(field).asText())).findFirst().orElse(null);
    }

    @Test
    void submissionsListEveryAttemptAndMarkNearMisses() throws Exception {
        Room room = lab(TeamCredit.SEE_TEAMMATES);
        CTFEntity horse = question(room, "Horse", "AMATERASU{correcthorse}", 100, 0);
        CTFEntity admin = question(room, "Admin", "Admin", 50, 1);
        User ana = user(Role.MEMBER, null);
        User ben = user(Role.MEMBER, null);
        User cal = user(Role.MEMBER, null);
        answer(room, horse, "correcthorse", token(ana));
        answer(room, horse, "AMATERASU{correcthorse}", token(ana));
        answer(room, admin, "admin", token(ben));
        answer(room, admin, "nope", token(cal));

        JsonNode page = manage(room, "/submissions", facilitatorToken);
        assertThat(page.get("total").asInt()).isEqualTo(4);
        assertThat(page.get("nearMisses").asInt()).isEqualTo(2);
        JsonNode first = list(page.get("items")).stream().filter(item -> item.get("userId").asText().equals(ana.getId())
                && item.get("index").asInt() == 0).findFirst().orElseThrow();
        assertThat(first.get("correct").asBoolean()).isFalse();
        assertThat(first.get("questionSolved").asBoolean()).isTrue();
        assertThat(first.get("nearMiss").get("reasons")).extracting(JsonNode::asText).containsExactly("Missing the AMATERASU{…} wrapper");
        JsonNode solved = list(page.get("items")).stream().filter(item -> item.get("userId").asText().equals(ana.getId())
                && item.get("index").asInt() == 1).findFirst().orElseThrow();
        assertThat(solved.get("correct").asBoolean()).isTrue();
        assertThat(solved.get("nearMiss").isNull()).isTrue();

        JsonNode near = manage(room, "/submissions?result=near", facilitatorToken);
        assertThat(list(near.get("items"))).extracting(item -> item.get("flag").asText()).containsExactlyInAnyOrder("correcthorse", "admin");
        assertThat(find(near.get("items"), "flag", "admin").get("nearMiss").get("reasons").get(0).asText()).isEqualTo("Wrong letter case");
        assertThat(manage(room, "/submissions?userId=" + ana.getId(), facilitatorToken).get("total").asInt()).isEqualTo(2);
        assertThat(manage(room, "/submissions?questionId=" + admin.getId(), facilitatorToken).get("total").asInt()).isEqualTo(2);
        assertThat(manage(room, "/submissions?result=wrong", facilitatorToken).get("total").asInt()).isEqualTo(3);
        JsonNode paged = manage(room, "/submissions?size=1&page=1", facilitatorToken);
        assertThat(paged.get("items").size()).isEqualTo(1);
        assertThat(paged.get("total").asInt()).isEqualTo(4);

        // Filtered by team: site teams here
        Team owls = team();
        User dee = user(Role.MEMBER, owls);
        answer(room, admin, "Admin", token(dee));
        assertThat(manage(room, "/submissions?teamId=" + owls.getId(), facilitatorToken).get("total").asInt()).isEqualTo(1);
        assertThat(manage(room, "/submissions?teamId=none", facilitatorToken).get("total").asInt()).isEqualTo(4);
    }

    @Test
    void creditForAnAttemptSolvesTheQuestionAndIsAudited() throws Exception {
        Room room = lab(TeamCredit.SEE_TEAMMATES);
        CTFEntity admin = question(room, "Admin", "Admin", 50, 0);
        CTFEntity horse = question(room, "Horse", "AMATERASU{correcthorse}", 100, 1);
        User ben = user(Role.MEMBER, null);
        User cal = user(Role.MEMBER, null);
        answer(room, admin, "admin", token(ben));
        String submission = list(manage(room, "/submissions?userId=" + ben.getId(), facilitatorToken).get("items")).getFirst()
                .get("id").asText();

        applicationEvents.clear();
        JsonNode credited = postOk(room, "/credit", Map.of("submissionId", submission, "reason", "Case slip, accepted"));
        assertThat(credited.get("points").asInt()).isEqualTo(50);
        assertThat(points(room, ben)).isEqualTo(50);
        assertThat(applicationEvents.stream(RoomActivityEvent.class).anyMatch(event -> event.roomId().equals(room.getId()))).isTrue();

        PointsHistoryEntry entry = seat(room, ben).getPointsHistory().getLast();
        assertThat(entry.getKind()).isEqualTo("CREDIT");
        assertThat(entry.getAwardedBy()).isEqualTo(facilitator.getUsername());
        assertThat(entry.getReason()).contains("Case slip, accepted");
        List<AuditEvent> audited = events("CREDIT_GIVEN", admin.getId());
        assertThat(audited).hasSize(1);
        assertThat(audited.getFirst().getActorUsername()).isEqualTo(facilitator.getUsername());
        assertThat(audited.getFirst().getDetail()).contains(ben.getUsername()).contains("Case slip, accepted");

        JsonNode after = list(manage(room, "/submissions?userId=" + ben.getId(), facilitatorToken).get("items")).getFirst();
        assertThat(after.get("creditedBy").asText()).isEqualTo(facilitator.getUsername());
        assertThat(after.get("questionSolved").asBoolean()).isTrue();
        // Overview shows it solved for the player
        JsonNode challenge = find(data(call(get("/api/room/" + room.getId() + "/overview"), token(ben)).andExpect(status().isOk())
                .andReturn()).get("challenges"), "id", admin.getId());
        assertThat(challenge.get("solved").asBoolean()).isTrue();

        send(post("/api/room/" + room.getId() + "/manage/credit"), Map.of("submissionId", submission, "reason", "again"),
                facilitatorToken).andExpect(status().isBadRequest());
        send(post("/api/room/" + room.getId() + "/manage/credit"), Map.of("submissionId", submission), facilitatorToken)
                .andExpect(status().isBadRequest());

        // A custom amount, for a player who never tried
        JsonNode custom = postOk(room, "/credit", Map.of("userId", cal.getId(), "questionId", horse.getId(), "points", 30,
                "reason", "Solved it on paper"));
        assertThat(custom.get("points").asInt()).isEqualTo(30);
        assertThat(points(room, cal)).isEqualTo(30);
    }

    @Test
    void withSharedSolvesCreditCountsForTheTeamOnce() throws Exception {
        Room room = lab(TeamCredit.SHARED_SOLVES);
        CTFEntity admin = question(room, "Admin", "Admin", 50, 0);
        Team owls = team();
        User ana = user(Role.MEMBER, owls);
        User ben = user(Role.MEMBER, owls);
        answer(room, admin, "admin", token(ana));

        JsonNode credited = postOk(room, "/credit", Map.of("userId", ana.getId(), "questionId", admin.getId(), "reason", "Close enough"));
        assertThat(credited.get("team").asText()).isEqualTo(owls.getName());
        // Ben's team has it now: he cannot answer it, and cannot be credited it again
        send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", admin.getId(), "flag", "Admin"), token(ben))
                .andExpect(status().isBadRequest());
        send(post("/api/room/" + room.getId() + "/manage/credit"), Map.of("userId", ben.getId(), "questionId", admin.getId(),
                "reason", "x"), facilitatorToken).andExpect(status().isBadRequest());
        JsonNode benView = find(data(call(get("/api/room/" + room.getId() + "/overview"), token(ben)).andExpect(status().isOk())
                .andReturn()).get("challenges"), "id", admin.getId());
        assertThat(benView.get("solvedByTeammates")).extracting(JsonNode::asText).containsExactly(ana.getUsername());
    }

    @Test
    void acceptingAnAnswerCanCreditEveryoneWhoGaveIt() throws Exception {
        Room room = lab(TeamCredit.SEE_TEAMMATES);
        CTFEntity admin = question(room, "Admin", "Admin", 50, 0);
        User ana = user(Role.MEMBER, null);
        User ben = user(Role.MEMBER, null);
        User cal = user(Role.MEMBER, null);
        User dee = user(Role.MEMBER, null);
        answer(room, admin, "administrator", token(ana));
        answer(room, admin, "administrator", token(ana));
        answer(room, admin, "Administrator ", token(ben));
        answer(room, admin, "root", token(cal));
        answer(room, admin, "administrator", token(dee));
        answer(room, admin, "Admin", token(dee));

        JsonNode accepted = postOk(room, "/accept-answer", Map.of("questionId", admin.getId(), "answer", "administrator",
                "caseSensitive", false, "creditExisting", true, "reason", "The account is also called that"));
        assertThat(list(accepted.get("credited"))).extracting(credit -> credit.get("userId").asText())
                .containsExactlyInAnyOrder(ana.getId(), ben.getId());
        assertThat(points(room, ana)).isEqualTo(50);
        assertThat(points(room, ben)).isEqualTo(50);
        assertThat(points(room, cal)).isZero();
        assertThat(points(room, dee)).isEqualTo(50);
        assertThat(flagRepository.getFlagsByCtfEntityId(admin.getId())).extracting(Flag::getFlag).contains("administrator");
        assertThat(events("FLAG_ACCEPTED", admin.getId())).hasSize(1);
        assertThat(events("FLAG_ACCEPTED", admin.getId()).getFirst().getDetail()).contains("credited 2 players");
        assertThat(events("CREDIT_GIVEN", admin.getId())).hasSize(2);

        // From now on it is simply right
        User eve = user(Role.MEMBER, null);
        JsonNode eveAnswer = data(send(post("/api/answer"), Map.of("roomId", room.getId(), "questionId", admin.getId(),
                "flag", "ADMINISTRATOR"), token(eve)).andExpect(status().isOk()).andReturn());
        assertThat(eveAnswer.get("correct").asBoolean()).isTrue();

        send(post("/api/room/" + room.getId() + "/manage/accept-answer"), Map.of("questionId", admin.getId(),
                "answer", "administrator", "reason", "x"), facilitatorToken).andExpect(status().isBadRequest());

        // Without crediting, from a submission: Cal's "root" becomes a flag but Cal is not credited
        String rootSubmission = list(manage(room, "/submissions?userId=" + cal.getId(), facilitatorToken).get("items")).getFirst()
                .get("id").asText();
        JsonNode root = postOk(room, "/accept-answer", Map.of("submissionId", rootSubmission, "reason", "Also fine"));
        assertThat(root.get("flag").asText()).isEqualTo("root");
        assertThat(root.get("credited")).isEmpty();
        assertThat(points(room, cal)).isZero();
    }

    @Test
    void awardsToPlayersAndTeamsShowOnTheBoards() throws Exception {
        Room room = lab(TeamCredit.SEE_TEAMMATES);
        CTFEntity admin = question(room, "Admin", "Admin", 50, 0);
        Team owls = team();
        User ana = user(Role.MEMBER, owls);
        answer(room, admin, "Admin", token(ana));

        JsonNode awarded = postOk(room, "/award", Map.of("userId", ana.getId(), "points", 25, "reason", "Great write-up"));
        assertThat(awarded.get("total").asInt()).isEqualTo(75);
        postOk(room, "/award", Map.of("teamId", owls.getId(), "points", 40, "reason", "Helped another team"));
        send(post("/api/room/" + room.getId() + "/manage/award"), Map.of("userId", ana.getId(), "teamId", owls.getId(),
                "points", 5, "reason", "x"), facilitatorToken).andExpect(status().isBadRequest());
        send(post("/api/room/" + room.getId() + "/manage/award"), Map.of("userId", ana.getId(), "points", 0, "reason", "x"),
                facilitatorToken).andExpect(status().isBadRequest());

        JsonNode standings = data(call(get("/api/room/" + room.getId() + "/standings"), token(ana)).andExpect(status().isOk()).andReturn());
        JsonNode row = find(standings.get("teams"), "teamId", owls.getId());
        assertThat(row.get("points").asInt()).isEqualTo(115);
        assertThat(row.get("history").get(row.get("history").size() - 1).get("total").asInt()).isEqualTo(115);

        JsonNode players = manage(room, "/players", facilitatorToken);
        JsonNode team = find(players.get("teams"), "teamId", owls.getId());
        assertThat(team.get("awardPoints").asInt()).isEqualTo(40);
        assertThat(team.get("points").asInt()).isEqualTo(115);
        assertThat(events("TEAM_POINTS_AWARDED", owls.getId())).hasSize(1);
        assertThat(events("POINTS_AWARDED", ana.getId())).hasSize(1);
        assertThat(events("TEAM_POINTS_AWARDED", owls.getId()).getFirst().getDetail()).contains("Helped another team");
    }

    @Test
    void resettingProgressTakesBackPointsAndRefundsHints() throws Exception {
        Room room = lab(TeamCredit.SEE_TEAMMATES);
        CTFEntity admin = question(room, "Admin", "Admin", 50, 0);
        CTFEntity horse = question(room, "Horse", "AMATERASU{correcthorse}", 100, 1);
        Hint hint = new Hint();
        hint.setHint("Look at the users table");
        hint.setCost(10);
        hint.setOrderIndex(0);
        hint.setCtfEntity(horse);
        hint = hintRepository.save(hint);
        User ana = user(Role.MEMBER, null);
        String anaToken = token(ana);
        answer(room, admin, "Admin", anaToken);
        call(post("/api/ctf-entity/use-hint").param("hintId", hint.getId()).param("roomId", room.getId())
                .param("userId", ana.getId()).param("ctfEntityId", horse.getId()), anaToken).andExpect(status().isOk());
        answer(room, horse, "wrong", anaToken);
        assertThat(points(room, ana)).isEqualTo(40);

        send(post("/api/room/" + room.getId() + "/manage/reset"), Map.of("userId", ana.getId(), "questionId", horse.getId(),
                "reason", "Hint was misleading"), facilitatorToken).andExpect(status().isBadRequest());
        JsonNode reset = postOk(room, "/reset", Map.of("userId", ana.getId(), "questionId", horse.getId(),
                "reason", "Hint was misleading", "confirm", true));
        assertThat(reset.get("pointsChange").asInt()).isEqualTo(10);
        assertThat(points(room, ana)).isEqualTo(50);
        assertThat(answerRepository.findByRoomUserIdAndCtfEntityId(seat(room, ana).getId(), horse.getId())).isEmpty();

        JsonNode whole = postOk(room, "/reset", Map.of("userId", ana.getId(), "reason", "Start over", "confirm", true));
        assertThat(whole.get("pointsChange").asInt()).isEqualTo(-50);
        assertThat(points(room, ana)).isZero();
        assertThat(answerRepository.findByRoomUserId(seat(room, ana).getId())).isEmpty();
        assertThat(seat(room, ana).getPointsHistory().getLast().getKind()).isEqualTo("RESET");
        assertThat(events("PROGRESS_RESET", ana.getId())).hasSize(2);
        assertThat(events("PROGRESS_RESET", ana.getId())).allMatch(event -> facilitator.getUsername().equals(event.getActorUsername()));

        // She can answer again
        answer(room, admin, "Admin", anaToken);
        assertThat(points(room, ana)).isEqualTo(50);
    }

    @Test
    void overviewPlayersQuestionsAndActivity() throws Exception {
        Lab environment = lab(LabType.DOCKER_COMPOSE, true);
        Room room = lab(TeamCredit.SEE_TEAMMATES);
        room.setEnvironment(environment);
        room.setEnvironmentMode(EnvironmentMode.SHARED);
        room = roomRepository.save(room);
        LabTracker shared = labTrackerRepository.save(LabTracker.builder().labStarted(environment).roomId(room.getId())
                .labStatus(LabStatus.ACTIVE).build());
        auditEventRepository.save(AuditEvent.builder().at(Instant.now()).actorUsername(facilitator.getUsername())
                .action("LAB_STARTED").targetType("LAB_TRACKER").targetId(shared.getId()).targetLabel(environment.getName()).build());

        CTFEntity admin = question(room, "Admin", "Admin", 50, 0);
        CTFEntity horse = question(room, "Horse", "AMATERASU{correcthorse}", 100, 1);
        Team owls = team();
        User ana = user(Role.MEMBER, owls);
        User ben = user(Role.MEMBER, null);
        answer(room, admin, "Admin", token(ana));
        answer(room, horse, "AMATERASU{corecthorse}", token(ana));
        answer(room, horse, "AMATERASU{corecthorse}", token(ben));
        answer(room, horse, "guess", token(ben));
        postOk(room, "/credit", Map.of("userId", ben.getId(), "questionId", admin.getId(), "reason", "Explained it"));
        postOk(room, "/award", Map.of("teamId", owls.getId(), "points", 5, "reason", "Tidy notes"));

        JsonNode overview = manage(room, "/overview", facilitatorToken);
        assertThat(overview.get("players").asInt()).isEqualTo(2);
        assertThat(overview.get("activePlayers").asInt()).isEqualTo(2);
        assertThat(overview.get("solves").asInt()).isEqualTo(2);
        assertThat(overview.get("attempts").asInt()).isEqualTo(4);
        assertThat(overview.get("nearMisses").asInt()).isEqualTo(2);
        assertThat(overview.get("teams").asInt()).isEqualTo(1);
        assertThat(find(overview.get("questionRates"), "id", admin.getId()).get("solveRate").asInt()).isEqualTo(100);
        assertThat(find(overview.get("questionRates"), "id", horse.getId()).get("solveRate").asInt()).isZero();
        assertThat(list(overview.get("solvesOverTime")).stream().mapToInt(bucket -> bucket.get("solves").asInt()).sum()).isEqualTo(2);
        assertThat(list(overview.get("solvesOverTime")).stream().mapToInt(bucket -> bucket.get("attempts").asInt()).sum()).isEqualTo(4);
        assertThat(overview.get("copies").get(0).get("target").asText()).isEqualTo("shared");

        JsonNode players = manage(room, "/players", facilitatorToken);
        JsonNode anaRow = find(players.get("players"), "userId", ana.getId());
        assertThat(anaRow.get("solves").asInt()).isEqualTo(1);
        assertThat(anaRow.get("attempts").asInt()).isEqualTo(2);
        assertThat(anaRow.get("teamName").asText()).isEqualTo(owls.getName());
        JsonNode detail = manage(room, "/players/" + ben.getId(), facilitatorToken);
        JsonNode benAdmin = find(detail.get("questions"), "questionId", admin.getId());
        assertThat(benAdmin.get("solved").asBoolean()).isTrue();
        assertThat(benAdmin.get("creditedBy").asText()).isEqualTo(facilitator.getUsername());
        assertThat(detail.get("submissions").size()).isEqualTo(2);

        JsonNode questions = manage(room, "/questions", facilitatorToken);
        JsonNode horseStats = find(questions, "id", horse.getId());
        assertThat(horseStats.get("nearMisses").asInt()).isEqualTo(2);
        JsonNode common = horseStats.get("wrongAnswers").get(0);
        assertThat(common.get("answer").asText()).isEqualTo("AMATERASU{corecthorse}");
        assertThat(common.get("count").asInt()).isEqualTo(2);
        assertThat(common.get("players").asInt()).isEqualTo(2);
        assertThat(common.get("nearMiss").get("reasons").get(0).asText()).isEqualTo("One character off");

        JsonNode activity = manage(room, "/activity", facilitatorToken);
        Map<String, Integer> kinds = new HashMap<>();
        list(activity).forEach(item -> kinds.merge(item.get("kind").asText(), 1, Integer::sum));
        assertThat(kinds).containsEntry("FIRST_ACTIVITY", 2).containsEntry("SOLVE", 1).containsEntry("CREDIT", 1)
                .containsEntry("TEAM_AWARD", 1).containsEntry("ENVIRONMENT", 1);
        assertThat(Instant.parse(list(activity).getFirst().get("at").asText()))
                .isAfterOrEqualTo(Instant.parse(list(activity).getLast().get("at").asText()));
    }

    @Test
    void onlyAdminsAndTheLabsFacilitatorsManageIt() throws Exception {
        Room room = lab(TeamCredit.SEE_TEAMMATES);
        CTFEntity admin = question(room, "Admin", "Admin", 50, 0);
        User ana = user(Role.MEMBER, null);
        String other = token(user(Role.FACILITATOR, null));
        for (String path : List.of("/overview", "/submissions", "/players", "/players/" + ana.getId(), "/questions", "/activity")) {
            call(get("/api/room/" + room.getId() + "/manage" + path),
                    other).andExpect(status().isForbidden());
        }
        send(post("/api/room/" + room.getId() + "/manage/credit"), Map.of("userId", ana.getId(), "questionId", admin.getId(),
                "reason", "x"), other).andExpect(status().isForbidden());
        // Admins run every lab
        data(call(get("/api/room/" + room.getId() + "/manage/overview"),
                admin()).andExpect(status().isOk()).andReturn());
    }
}
