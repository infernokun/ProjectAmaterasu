package com.infernokun.amaterasu.topbar;

import com.fasterxml.jackson.databind.JsonNode;
import com.infernokun.amaterasu.models.entities.Notification;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.repositories.NotificationRepository;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.repositories.ctf.CTFEntityRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomRepository;
import com.infernokun.amaterasu.services.entity.AuditService;
import com.infernokun.amaterasu.services.notification.NotificationRules;
import com.infernokun.amaterasu.services.notification.NotificationService;
import com.infernokun.amaterasu.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Each user reads and marks only their own notifications, and existing events create them. */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest extends ApiTestSupport {
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AuditService auditService;
    @Autowired private RoomRepository roomRepository;
    @Autowired private NotificationRules rules;
    @Autowired private CTFEntityRepository ctfEntityRepository;

    private Team red;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        red = team("red");
        alice = user(Role.MEMBER, red);
        bob = user(Role.MEMBER, team("blue"));
    }

    private Notification notify(User user, String title) {
        return notificationService.send(List.of(user.getId()), "TEST", title, "Body", "/home").getFirst();
    }

    private JsonNode list(String token) throws Exception {
        return data(getAs("/api/notifications", token).andExpect(status().isOk())).get("content");
    }

    @Test
    void notificationsNeedASignedInUser() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void eachUserSeesOnlyTheirOwnNewestFirst() throws Exception {
        notify(alice, "First");
        Thread.sleep(5);
        notify(alice, "Second");
        notify(bob, "For Bob");

        JsonNode mine = list(token(alice));
        assertThat(mine).hasSize(2);
        assertThat(mine.get(0).get("title").asText()).isEqualTo("Second");
        assertThat(mine.get(0).has("recipientId")).isFalse();
        assertThat(mine.get(0).get("readAt").isNull()).isTrue();
        assertThat(mine.get(0).get("createdAt").asText()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");

        assertThat(list(token(bob))).hasSize(1);
        assertThat(data(getAs("/api/notifications/unread-count", token(alice))).get("count").asLong()).isEqualTo(2);
    }

    @Test
    void listIsPaged() throws Exception {
        for (int i = 0; i < 3; i++) {
            notify(alice, "N" + i);
        }
        JsonNode page = data(getAs("/api/notifications?page=0&size=2", token(alice)));
        assertThat(page.get("content")).hasSize(2);
        assertThat(page.get("totalElements").asInt()).isEqualTo(3);
        assertThat(data(getAs("/api/notifications?page=1&size=2", token(alice))).get("content")).hasSize(1);
    }

    @Test
    void markingAnotherUsersNotificationIsForbidden() throws Exception {
        Notification forAlice = notify(alice, "Private");
        postAs("/api/notifications/" + forAlice.getId() + "/read", token(bob), Map.of()).andExpect(status().isForbidden());
        assertThat(notificationRepository.findById(forAlice.getId()).orElseThrow().getReadAt()).isNull();

        postAs("/api/notifications/missing/read", token(bob), Map.of()).andExpect(status().isNotFound());
    }

    @Test
    void markOneAndMarkAllRead() throws Exception {
        Notification first = notify(alice, "One");
        notify(alice, "Two");
        notify(bob, "Untouched");
        String token = token(alice);

        JsonNode read = data(postAs("/api/notifications/" + first.getId() + "/read", token, Map.of()).andExpect(status().isOk()));
        assertThat(read.get("readAt").isNull()).isFalse();
        assertThat(data(getAs("/api/notifications/unread-count", token)).get("count").asLong()).isEqualTo(1);

        assertThat(data(postAs("/api/notifications/read-all", token, Map.of()).andExpect(status().isOk())).get("count").asInt())
                .isEqualTo(1);
        assertThat(data(getAs("/api/notifications/unread-count", token)).get("count").asLong()).isZero();
        assertThat(data(getAs("/api/notifications/unread-count", token(bob))).get("count").asLong()).isEqualTo(1);
    }

    @Test
    void changingYourPasswordNotifiesYou() throws Exception {
        mvc.perform(post("/api/auth/password").header("Authorization", "Bearer " + token(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "N3w!password-123"))))
                .andExpect(status().isOk());
        List<Notification> mine = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(alice.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertThat(mine).extracting(Notification::getType).contains(NotificationRules.PASSWORD_CHANGED);
    }

    @Test
    void awardedPointsNotifyThePlayerWithALinkToTheirScore() throws Exception {
        User facilitator = user(Role.FACILITATOR, null);
        Room room = roomRepository.save(Room.builder().name(unique("room"))
                .facilitators(new ArrayList<>(List.of(facilitator.getId()))).build());
        postAs("/api/room/join/" + room.getId() + "/" + alice.getId(), token(alice), Map.of()).andExpect(status().isOk());
        postAs("/api/room/add-points/" + room.getId() + "/" + alice.getId(), token(facilitator),
                Map.of("points", 50, "reason", "Writeup bonus")).andExpect(status().isOk());

        JsonNode mine = list(token(alice));
        JsonNode award = null;
        for (JsonNode item : mine) {
            if ("POINTS_AWARDED".equals(item.get("type").asText())) {
                award = item;
            }
        }
        assertThat(award).isNotNull();
        assertThat(award.get("title").asText()).isEqualTo("+50 pts in " + room.getName());
        assertThat(award.get("body").asText()).isEqualTo("Writeup bonus · from " + facilitator.getUsername());
        assertThat(award.get("link").asText()).isEqualTo("/rooms/" + room.getId() + "/scoreboard/" + alice.getUsername());
        assertThat(list(token(bob))).isEmpty();
    }

    @Test
    void aFailedLabActionNotifiesTheRestOfTheTeamAndFacilitators() throws Exception {
        User teammate = user(Role.MEMBER, red);
        User facilitator = user(Role.FACILITATOR, null);
        auditService.recordAs(alice.getId(), alice.getUsername(), "LAB_ACTION_FAILED", "LAB_TRACKER", "lt-1",
                "Web Exploitation 101", red.getId(), "Start failed on dock-01: pull access denied for shop/app");

        JsonNode forTeammate = list(token(teammate));
        assertThat(forTeammate).hasSize(1);
        assertThat(forTeammate.get(0).get("title").asText()).isEqualTo("Web Exploitation 101 failed to start");
        assertThat(forTeammate.get(0).get("body").asText()).isEqualTo("pull access denied for shop/app");
        assertThat(forTeammate.get(0).get("link").asText()).isEqualTo("/labs/lt-1");

        JsonNode forFacilitator = list(token(facilitator));
        assertThat(forFacilitator.get(0).get("type").asText()).isEqualTo(NotificationRules.LAB_ATTENTION);
        assertThat(forFacilitator.get(0).get("title").asText()).isEqualTo(red.getName() + ": Web Exploitation 101 failed to start");

        assertThat(list(token(alice))).isEmpty();
        assertThat(list(token(bob))).isEmpty();
    }

    private CTFEntity challenge(Room room, String title, LocalDateTime release) {
        CTFEntity challenge = new CTFEntity();
        challenge.setQuestion(title);
        challenge.setCategory("Crypto");
        challenge.setPoints(300);
        challenge.setRoom(room);
        challenge.setReleaseDate(release);
        return ctfEntityRepository.save(challenge);
    }

    @Test
    void scheduledReleasesNotifyPlayersInTheRoomOnly() throws Exception {
        Room room = roomRepository.save(Room.builder().name(unique("room")).build());
        postAs("/api/room/join/" + room.getId() + "/" + alice.getId(), token(alice), Map.of()).andExpect(status().isOk());
        LocalDateTime now = LocalDateTime.now();
        CTFEntity later = challenge(room, "Padding Oracle", now.plusMinutes(30));
        challenge(room, "Open from the start", now);

        rules.releasedBetween(now.minusMinutes(1), now.plusHours(1));

        JsonNode mine = list(token(alice));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("title").asText()).isEqualTo("Padding Oracle is open in " + room.getName());
        assertThat(mine.get(0).get("body").asText()).isEqualTo("Crypto · 300 pts");
        assertThat(mine.get(0).get("link").asText()).isEqualTo("/rooms/" + room.getId() + "?challenge=" + later.getId());
        assertThat(list(token(bob))).isEmpty();
    }

    @Test
    void severalReleasesAtOnceBecomeOneNotification() throws Exception {
        Room room = roomRepository.save(Room.builder().name(unique("room")).build());
        postAs("/api/room/join/" + room.getId() + "/" + alice.getId(), token(alice), Map.of()).andExpect(status().isOk());
        // Sub-second precision on purpose: some clocks give nanoseconds, and the release must still match
        LocalDateTime release = LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(123_456_789);
        for (String title : List.of("One", "Two", "Three", "Four")) {
            challenge(room, title, release);
        }

        rules.releasedBetween(release.minusMinutes(1), release);

        JsonNode mine = list(token(alice));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("title").asText()).isEqualTo("4 new challenges are open in " + room.getName());
        assertThat(mine.get(0).get("body").asText()).isEqualTo("One, Two, Three and 1 more");
    }
}
