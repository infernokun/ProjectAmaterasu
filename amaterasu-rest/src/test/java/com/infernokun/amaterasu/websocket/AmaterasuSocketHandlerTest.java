package com.infernokun.amaterasu.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.models.entities.Notification;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AmaterasuSocketHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private UserRepository userRepository;
    private AmaterasuSocketHandler handler;
    private Team red;
    private Team blue;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        handler = new AmaterasuSocketHandler(objectMapper, userRepository);
        red = team("red");
        blue = team("blue");
    }

    private static Team team(String id) {
        Team team = new Team();
        team.setId(id);
        team.setName(id);
        return team;
    }

    private User user(String id, Role role, Team team, boolean disabled) {
        User user = new User();
        user.setId(id);
        user.setUsername(id);
        user.setRole(role);
        user.setTeam(team);
        user.setDisabled(disabled);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    private WebSocketSession connect(String userId) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(userId).build();
        when(session.getPrincipal()).thenReturn(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
        when(session.getAttributes()).thenReturn(new HashMap<>());
        when(session.getId()).thenReturn("s-" + userId);
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);
        return session;
    }

    private LabTracker tracker(Team owner) {
        LabTracker tracker = LabTracker.builder().labOwner(owner).labStatus(LabStatus.ACTIVE).build();
        tracker.setId("lt1");
        return tracker;
    }

    private JsonNode sentTo(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        return objectMapper.readTree(message.getValue().getPayload());
    }

    @Test
    void sendsListsAsPlainArraysEvenWhenTheAppMapperAddsTypes() throws Exception {
        ObjectMapper typed = new ObjectMapper().findAndRegisterModules();
        typed.activateDefaultTyping(typed.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
        handler = new AmaterasuSocketHandler(typed, userRepository);
        user("member-red", Role.MEMBER, red, false);
        WebSocketSession session = connect("member-red");
        LabTracker tracker = tracker(red);
        tracker.setVms(new java.util.ArrayList<>(List.of(ProxmoxVM.builder().vmid(125).name("kali").status("running").build())));

        handler.onLabTrackerChanged(LabTrackerChangedEvent.updated(tracker));

        JsonNode vms = sentTo(session).get("labTracker").get("vms");
        assertThat(vms.isArray()).isTrue();
        assertThat(vms.get(0).get("name").asText()).isEqualTo("kali");
    }

    @Test
    void teamCopiesGoToWhoeverTheLabsTeamsSayWorksInThem() throws Exception {
        // The app passes TeamResolver::worksIn; here a lab team that does not match anyone's site team
        handler = new AmaterasuSocketHandler(objectMapper, userRepository, new com.infernokun.amaterasu.components.LabActionGuard(),
                (user, copy) -> user.getId().equals("member-blue") && copy.getLabOwner() == red);
        user("member-red", Role.MEMBER, red, false);
        user("member-blue", Role.MEMBER, blue, false);
        WebSocketSession redSession = connect("member-red");
        WebSocketSession blueSession = connect("member-blue");

        handler.onLabTrackerChanged(LabTrackerChangedEvent.updated(tracker(red)));

        assertThat(sentTo(blueSession).get("labTracker").get("id").asText()).isEqualTo("lt1");
        verify(redSession, never()).sendMessage(any());
    }

    @Test
    void sendsNotificationsOnlyToTheirRecipient() throws Exception {
        user("member-red", Role.MEMBER, red, false);
        user("member-blue", Role.MEMBER, blue, false);
        WebSocketSession redSession = connect("member-red");
        WebSocketSession blueSession = connect("member-blue");
        Notification notification = Notification.builder().id("n1").recipientId("member-red").type("POINTS_AWARDED")
                .title("+50 pts").createdAt(java.time.Instant.parse("2026-11-06T10:00:00Z")).build();

        handler.onNotificationCreated(new NotificationCreatedEvent(notification));

        JsonNode payload = sentTo(redSession);
        assertThat(payload.get("type").asText()).isEqualTo("notification");
        assertThat(payload.get("notification").get("title").asText()).isEqualTo("+50 pts");
        assertThat(payload.get("notification").get("createdAt").asText()).isEqualTo("2026-11-06T10:00:00Z");
        assertThat(payload.get("notification").has("recipientId")).isFalse();
        verify(blueSession, never()).sendMessage(any());
    }

    @Test
    void sendsTrackerChangesToItsTeamAndAdmins() throws Exception {
        user("member-red", Role.MEMBER, red, false);
        user("member-blue", Role.MEMBER, blue, false);
        user("admin", Role.ADMIN, null, false);
        WebSocketSession redSession = connect("member-red");
        WebSocketSession blueSession = connect("member-blue");
        WebSocketSession adminSession = connect("admin");

        handler.onLabTrackerChanged(LabTrackerChangedEvent.updated(tracker(red)));

        JsonNode payload = sentTo(redSession);
        assertThat(payload.get("type").asText()).isEqualTo("labTracker");
        assertThat(payload.get("action").asText()).isEqualTo("updated");
        assertThat(payload.get("labTracker").get("id").asText()).isEqualTo("lt1");
        assertThat(payload.get("labTracker").get("labStatus").asText()).isEqualTo("ACTIVE");
        sentTo(adminSession);
        verify(blueSession, never()).sendMessage(any());
    }

    @Test
    void skipsDisabledAccounts() throws Exception {
        user("disabled-red", Role.MEMBER, red, true);
        WebSocketSession disabled = connect("disabled-red");

        handler.onLabTrackerChanged(LabTrackerChangedEvent.removed(tracker(red)));

        verify(disabled, never()).sendMessage(any());
    }

    @Test
    void sendsEveryTeamsChangesToFacilitators() throws Exception {
        user("facilitator", Role.FACILITATOR, blue, false);
        WebSocketSession facilitator = connect("facilitator");

        handler.onLabTrackerChanged(LabTrackerChangedEvent.updated(tracker(red)));

        assertThat(sentTo(facilitator).get("labTracker").get("id").asText()).isEqualTo("lt1");
    }

    @Test
    void announcesRoomActivityToEveryone() throws Exception {
        user("member-blue", Role.MEMBER, blue, false);
        WebSocketSession session = connect("member-blue");

        handler.onRoomActivity(new RoomActivityEvent("room1"));

        JsonNode payload = sentTo(session);
        assertThat(payload.get("type").asText()).isEqualTo("roomActivity");
        assertThat(payload.get("roomId").asText()).isEqualTo("room1");
    }

    @Test
    void marksRemovedTrackers() throws Exception {
        user("member-red", Role.MEMBER, red, false);
        WebSocketSession session = connect("member-red");

        handler.onLabTrackerChanged(LabTrackerChangedEvent.removed(tracker(red)));

        assertThat(sentTo(session).get("action").asText()).isEqualTo("removed");
    }

    @Test
    void stopsSendingAfterTheSocketCloses() throws Exception {
        user("member-red", Role.MEMBER, red, false);
        WebSocketSession session = connect("member-red");
        handler.afterConnectionClosed(session, org.springframework.web.socket.CloseStatus.NORMAL);

        handler.onLabTrackerChanged(LabTrackerChangedEvent.updated(tracker(red)));

        verify(session, never()).sendMessage(any());
    }
}
