package com.infernokun.amaterasu.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.repositories.UserRepository;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class AmaterasuSocketHandler extends TextWebSocketHandler {
    static final String USER_ID = "amaterasu.userId";
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    private final List<WebSocketSession> currentSessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public AmaterasuSocketHandler(ObjectMapper objectMapper, UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        if (session.getPrincipal() instanceof JwtAuthenticationToken token) {
            session.getAttributes().put(USER_ID, token.getName());
        }
        // Heartbeats and broadcasts come from different threads; the decorator serializes sends
        this.currentSessions.add(new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES));
        log.info("WEBSOCKET Connection Established w ID: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        this.currentSessions.removeIf(open -> open.getId().equals(session.getId()));
        log.info("WEBSOCKET Connection Closed for ID: {}", session.getId());
    }

    /** Sends a tracker change to admins, facilitators and members of the team that owns the lab. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onLabTrackerChanged(LabTrackerChangedEvent event) {
        LabTracker labTracker = event.labTracker();
        String teamId = labTracker.getLabOwner() != null ? labTracker.getLabOwner().getId() : null;

        String message;
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", "labTracker");
            node.put("action", event.removed() ? "removed" : "updated");
            node.set("labTracker", objectMapper.valueToTree(labTracker));
            message = objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("WEBSOCKET Could not serialize lab tracker {}: {}", labTracker.getId(), e.getMessage());
            return;
        }

        Map<String, Optional<User>> users = new HashMap<>();
        for (WebSocketSession session : this.currentSessions) {
            if (session.isOpen() && canSeeTeam(session, teamId, users)) {
                send(session, message);
            }
        }
    }

    // Looked up on every change so a role, team or disabled flag change applies to open sockets too
    private boolean canSeeTeam(WebSocketSession session, String teamId, Map<String, Optional<User>> users) {
        if (!(session.getAttributes().get(USER_ID) instanceof String userId)) {
            return false;
        }
        Optional<User> user = users.computeIfAbsent(userId, userRepository::findById);
        if (user.isEmpty() || !user.get().isEnabled()) {
            return false;
        }
        if (user.get().getRole() == Role.ADMIN || user.get().getRole() == Role.FACILITATOR) {
            return true;
        }
        return teamId != null && user.get().getTeam() != null && teamId.equals(user.get().getTeam().getId());
    }

    /** Tells every signed-in client that points or attempts changed in a room, so open views can reload. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onRoomActivity(RoomActivityEvent event) {
        String message;
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", "roomActivity");
            node.put("roomId", event.roomId());
            message = objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return;
        }
        for (WebSocketSession session : this.currentSessions) {
            if (session.isOpen()) {
                send(session, message);
            }
        }
    }

    /** Sends a new notification to its recipient's open sockets only. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        String recipientId = event.notification().getRecipientId();
        String message;
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", "notification");
            node.set("notification", objectMapper.valueToTree(event.notification()));
            message = objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("WEBSOCKET Could not serialize notification {}: {}", event.notification().getId(), e.getMessage());
            return;
        }
        for (WebSocketSession session : this.currentSessions) {
            if (session.isOpen() && recipientId != null && recipientId.equals(session.getAttributes().get(USER_ID))) {
                send(session, message);
            }
        }
    }

    private void send(WebSocketSession session, String message) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException | IllegalStateException e) {
            log.warn("WEBSOCKET Error sending message to session {}: {}", session.getId(), e.getMessage());
        }
    }

    @Scheduled(fixedRateString = "${SOCKET_POLL_MS:30000}")
    public void sendHeartbeatToClients() {
        for (WebSocketSession session : this.currentSessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                send(session, objectMapper.writeValueAsString(HeartbeatDTO.builder()
                        .type("heartbeat")
                        .timestamp(LocalDateTime.now())
                        .session(session.getId())
                        .build()));
            } catch (JsonProcessingException e) {
                log.warn("WEBSOCKET issue sending heartbeat: {}", session.getId(), e);
            }
        }
    }

    @Data
    @Builder
    private static class HeartbeatDTO {
        private String type;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime timestamp;
        private String session;
    }
}
