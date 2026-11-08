package com.infernokun.amaterasu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.infernokun.amaterasu.components.DataMigrations;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityAnswer;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.repositories.ctf.CTFAnswerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lab drafts and publishing, and the challenges that use a lab. */
@SpringBootTest
@AutoConfigureMockMvc
class LabDraftAndLinkIntegrationTest extends IntegrationSupport {
    @Autowired CTFAnswerRepository answerRepository;
    @Autowired DataMigrations migrations;

    private static List<String> ids(JsonNode list) {
        List<String> ids = new ArrayList<>();
        list.forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    // Drafts

    @Test
    void draftsAreHiddenFromMembersButNotFromAuthors() throws Exception {
        Lab draft = lab(LabType.DOCKER_COMPOSE, false);
        Lab published = lab(LabType.DOCKER_COMPOSE, true);
        String member = token(user(Role.MEMBER, team()));
        String facilitator = token(user(Role.FACILITATOR, null));
        String creator = token(user(Role.CREATOR, null));

        for (String token : List.of(member, facilitator)) {
            assertThat(ids(data(call(get("/api/labs"), token).andExpect(status().isOk()).andReturn())))
                    .contains(published.getId()).doesNotContain(draft.getId());
            call(get("/api/labs/" + draft.getId()), token).andExpect(status().isNotFound());
            call(get("/api/labs/" + published.getId()), token).andExpect(status().isOk());
        }
        for (String token : List.of(creator, admin())) {
            assertThat(ids(data(call(get("/api/labs"), token).andExpect(status().isOk()).andReturn())))
                    .contains(published.getId(), draft.getId());
            call(get("/api/labs/" + draft.getId()), token).andExpect(status().isOk());
        }
    }

    @Test
    void nobodyCanStartADraft() throws Exception {
        Lab draft = lab(LabType.DOCKER_COMPOSE, false);
        var server = server(ServerType.DOCKER_HOST, true);
        Team team = team();
        send(post("/api/labs/start"), Map.of("labId", draft.getId(), "remoteServerId", server.getId()),
                token(user(Role.MEMBER, team))).andExpect(status().isForbidden());
        for (String token : List.of(token(user(Role.ADMIN, team)))) {
            JsonNode body = objectMapper.readTree(send(post("/api/labs/start"),
                    Map.of("labId", draft.getId(), "remoteServerId", server.getId()), token)
                    .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString());
            assertThat(body.get("data").get("output").asText()).contains("is a draft");
        }
        assertThat(labTrackerRepository.findByLabStarted(draft)).isEmpty();
    }

    @Test
    void newLabsStartAsDrafts() throws Exception {
        var server = server(ServerType.PROXMOX, true);
        Map<String, Object> request = new HashMap<>(Map.of("name", unique("vm"), "labType", "VIRTUAL_MACHINE",
                "capacity", 2, "vms", List.of(9001)));
        JsonNode created = data(send(post("/api/labs").param("remoteServerId", server.getId()), request,
                token(user(Role.CREATOR, null))).andExpect(status().isCreated()).andReturn());
        assertThat(created.get("published").asBoolean()).isFalse();
    }

    @Test
    void authorsPublishReadyLabsAndUnpublishOnlyWhenNoTeamHasThemDeployed() throws Exception {
        String creator = token(user(Role.CREATOR, null));
        Lab notReady = labRepository.save(Lab.builder().name(unique("lab")).labType(LabType.DOCKER_COMPOSE)
                .capacity(2).ready(false).build());
        send(put("/api/labs/" + notReady.getId() + "/publish"), null, creator).andExpect(status().isBadRequest());

        Lab lab = lab(LabType.DOCKER_COMPOSE, false);
        JsonNode published = data(send(put("/api/labs/" + lab.getId() + "/publish"), null, creator)
                .andExpect(status().isOk()).andReturn());
        assertThat(published.get("published").asBoolean()).isTrue();
        assertThat(events("LAB_PUBLISHED", lab.getId())).hasSize(1);

        Team team = team();
        var tracker = tracker(labRepository.findById(lab.getId()).orElseThrow(), team, LabStatus.PAUSED,
                server(ServerType.DOCKER_HOST, true));
        JsonNode refused = objectMapper.readTree(send(put("/api/labs/" + lab.getId() + "/unpublish"), null, creator)
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString());
        assertThat(refused.get("message").asText()).contains("deployed by 1 team");

        tracker.setLabStatus(LabStatus.STOPPED);
        labTrackerRepository.save(tracker);
        JsonNode draft = data(send(put("/api/labs/" + lab.getId() + "/unpublish"), null, creator)
                .andExpect(status().isOk()).andReturn());
        assertThat(draft.get("published").asBoolean()).isFalse();
        assertThat(events("LAB_UNPUBLISHED", lab.getId())).hasSize(1);
    }

    @Test
    void membersAndFacilitatorsCannotPublish() throws Exception {
        Lab lab = lab(LabType.DOCKER_COMPOSE, false);
        for (String token : List.of(token(user(Role.MEMBER, team())), token(user(Role.FACILITATOR, null)))) {
            send(put("/api/labs/" + lab.getId() + "/publish"), null, token).andExpect(status().isForbidden());
            send(put("/api/labs/" + lab.getId() + "/unpublish"), null, token).andExpect(status().isForbidden());
        }
        assertThat(labRepository.findById(lab.getId()).orElseThrow().getPublished()).isFalse();
    }

    @Test
    void labsFromBeforeDraftsStayPublishedAndOldChallengesGetAnOrder() throws Exception {
        Lab old = lab(LabType.DOCKER_COMPOSE, false);
        old.setPublished(null);
        labRepository.save(old);
        Room room = room();
        CTFEntity placed = challenge(room, "Placed", null, 0);
        CTFEntity web = challenge(room, "Web 300", null, null);
        web.setPoints(300);
        ctfEntityRepository.save(web);
        CTFEntity crypto = challenge(room, "Crypto", null, null);
        crypto.setCategory("Crypto");
        ctfEntityRepository.save(crypto);

        migrations.run(null);
        migrations.run(null);

        assertThat(labRepository.findById(old.getId()).orElseThrow().getPublished()).isTrue();
        assertThat(ctfEntityRepository.findById(placed.getId()).orElseThrow().getSortOrder()).isZero();
        assertThat(ctfEntityRepository.findById(crypto.getId()).orElseThrow().getSortOrder()).isEqualTo(1);
        assertThat(ctfEntityRepository.findById(web.getId()).orElseThrow().getSortOrder()).isEqualTo(2);
    }

    // Challenges that use a lab

    @Test
    void playersSeeReleasedChallengesForALabInPublishedLabs() throws Exception {
        Lab lab = lab(LabType.DOCKER_COMPOSE, true);
        Room joined = room();
        Room notJoined = room();
        notJoined.setPublished(false);
        roomRepository.save(notJoined);
        User player = user(Role.MEMBER, team());
        RoomUser seat = join(player, joined);

        CTFEntity open = challenge(joined, "Open", lab.getId(), 0);
        CTFEntity hidden = challenge(joined, "Hidden", lab.getId(), 1);
        hidden.setVisible(false);
        ctfEntityRepository.save(hidden);
        CTFEntity later = challenge(joined, "Later", lab.getId(), 2);
        later.setReleaseDate(LocalDateTime.now().plusHours(3));
        ctfEntityRepository.save(later);
        CTFEntity elsewhere = challenge(notJoined, "Elsewhere", lab.getId(), 0);
        challenge(joined, "Other lab", null, 3);

        CTFEntityAnswer answer = new CTFEntityAnswer();
        answer.setRoomUser(seat);
        answer.setCtfEntity(open);
        answer.setCorrect(true);
        answer.setAttempts(1);
        answerRepository.save(answer);

        JsonNode mine = data(call(get("/api/ctf-entity/by-lab/" + lab.getId()), token(player))
                .andExpect(status().isOk()).andReturn());
        assertThat(ids(mine)).containsExactly(open.getId());
        JsonNode item = mine.get(0);
        assertThat(item.get("title").asText()).isEqualTo("Open");
        assertThat(item.get("question").asText()).isEqualTo("Open");
        assertThat(item.get("roomId").asText()).isEqualTo(joined.getId());
        assertThat(item.get("roomName").asText()).isEqualTo(joined.getName());
        assertThat(item.get("category").asText()).isEqualTo("Web");
        assertThat(item.get("points").asInt()).isEqualTo(100);
        assertThat(item.get("solved").asBoolean()).isTrue();
        assertThat(item.has("flags")).isFalse();
        assertThat(item.has("solutionExplanation")).isFalse();

        for (String author : List.of(token(user(Role.CREATOR, null)), admin())) {
            JsonNode all = data(call(get("/api/ctf-entity/by-lab/" + lab.getId()), author).andExpect(status().isOk()).andReturn());
            assertThat(ids(all)).containsExactlyInAnyOrder(open.getId(), hidden.getId(), later.getId(), elsewhere.getId());
            all.forEach(entry -> assertThat(entry.get("solved").asBoolean()).isFalse());
        }

        // Facilitators follow the player rule
        JsonNode staff = data(call(get("/api/ctf-entity/by-lab/" + lab.getId()), token(user(Role.FACILITATOR, null)))
                .andExpect(status().isOk()).andReturn());
        assertThat(ids(staff)).containsExactly(open.getId());
        call(get("/api/ctf-entity/by-lab/" + lab.getId()), null).andExpect(status().isUnauthorized());
        call(get("/api/ctf-entity/by-lab/missing"), token(player)).andExpect(status().isNotFound());
    }

    @Test
    void aDraftLabsChallengesAreOnlyListedForAuthors() throws Exception {
        Lab draft = lab(LabType.DOCKER_COMPOSE, false);
        Room room = room();
        User player = user(Role.MEMBER, team());
        join(player, room);
        challenge(room, "Draft lab challenge", draft.getId(), 0);

        call(get("/api/ctf-entity/by-lab/" + draft.getId()), token(player)).andExpect(status().isNotFound());
        assertThat(data(call(get("/api/ctf-entity/by-lab/" + draft.getId()), token(user(Role.CREATOR, null)))
                .andExpect(status().isOk()).andReturn())).hasSize(1);
    }

    @Test
    void theChallengeEditorLinksAndClearsALab() throws Exception {
        Lab lab = lab(LabType.DOCKER_COMPOSE, true);
        Room room = room();
        String creator = token(user(Role.CREATOR, null));

        send(post("/api/ctf-entity"), Map.of("question", "Linked", "roomId", room.getId(), "labId", "missing",
                "points", 10, "maxAttempts", 3), creator).andExpect(status().isBadRequest());
        JsonNode created = data(send(post("/api/ctf-entity"), Map.of("question", "Linked", "roomId", room.getId(),
                "labId", lab.getId(), "points", 10, "maxAttempts", 3), creator).andExpect(status().isCreated()).andReturn());
        assertThat(created.get("labId").asText()).isEqualTo(lab.getId());
        String id = created.get("id").asText();

        send(put("/api/ctf-entity/" + id), Map.of("question", "Linked", "labId", "missing"), creator)
                .andExpect(status().isBadRequest());
        JsonNode cleared = data(send(put("/api/ctf-entity/" + id), Map.of("question", "Linked"), creator)
                .andExpect(status().isOk()).andReturn());
        assertThat(cleared.get("labId").isNull()).isTrue();

        send(put("/api/ctf-entity/" + id), Map.of("question", "Linked", "labId", lab.getId()), creator)
                .andExpect(status().isOk());
        send(delete("/api/labs/" + lab.getId()), null, creator).andExpect(status().isOk());
        assertThat(ctfEntityRepository.findById(id).orElseThrow().getLabId()).isNull();
    }
}
