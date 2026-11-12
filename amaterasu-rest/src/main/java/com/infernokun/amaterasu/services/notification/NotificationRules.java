package com.infernokun.amaterasu.services.notification;

import com.infernokun.amaterasu.models.entities.AuditEvent;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns events that already happen (audit events, lab tracker changes, challenge releases, server
 * health) into notifications for the people they concern. Each rule runs in its own transaction.
 */
@Component
@RequiredArgsConstructor
public class NotificationRules {
    public static final String LAB_FAILED = "LAB_FAILED";
    public static final String LAB_DEGRADED = "LAB_DEGRADED";
    public static final String LAB_ATTENTION = "LAB_ATTENTION";
    public static final String POINTS_AWARDED = "POINTS_AWARDED";
    public static final String CHALLENGE_RELEASED = "CHALLENGE_RELEASED";
    public static final String SESSION_SIGNED_OUT = "SESSION_SIGNED_OUT";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String ROLE_CHANGED = "ROLE_CHANGED";
    public static final String TEAM_CHANGED = "TEAM_CHANGED";
    public static final String SERVER_OFFLINE = "SERVER_OFFLINE";
    public static final String SERVER_ONLINE = "SERVER_ONLINE";

    // "Start failed on dock-01: reason"
    private static final Pattern ACTION_FAILED = Pattern.compile("^(\\w+) failed(?: on [^:]+)?(?::\\s*(.*))?$", Pattern.DOTALL);
    // "50 pts in Winter Range: reason"
    private static final Pattern AWARD = Pattern.compile("^(-?\\d+) pts in (.+?): (.*)$", Pattern.DOTALL);
    private static final Duration LAB_QUIET = Duration.ofMinutes(10);

    private final NotificationService notifications;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAudit(AuditEvent event) {
        String action = event.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case "POINTS_AWARDED" -> pointsAwarded(event);
            case "PASSWORD_CHANGED" -> notifications.send(List.of(event.getTargetId()), PASSWORD_CHANGED,
                    "Your password was changed",
                    "Your other sessions were signed out. If this was not you, ask an admin to reset your password.",
                    "/account");
            case "PASSWORD_RESET" -> notifications.send(List.of(event.getTargetId()), PASSWORD_CHANGED,
                    "An admin reset your password", "You were signed out everywhere.", "/account");
            case "SESSION_REVOKED" -> notifications.send(List.of(event.getActorId()), SESSION_SIGNED_OUT,
                    event.getDetail() != null ? capitalize(event.getDetail()) : "A session of yours was signed out",
                    "Signed out from Account. That device has to sign in again.", "/account");
            case "USER_ROLE_CHANGED" -> roleChanged(event);
            case "USER_TEAM_CHANGED" -> notifications.send(List.of(event.getTargetId()), TEAM_CHANGED,
                    event.getDetail() != null && event.getDetail().startsWith("Moved to ")
                            ? "You are now on " + event.getDetail().substring("Moved to ".length())
                            : "You were taken off your team",
                    "Your team labs and team scores follow your new team.", "/home");
            case "LAB_ACTION_FAILED" -> labActionFailed(event);
            case "CHALLENGE_CREATED" -> challengeCreated(event);
            default -> { }
        }
    }

    /** A lab went from working to partly or fully broken outside a lab action, for example a container died. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLabDegraded(LabTracker tracker) {
        if (tracker.getLabOwner() == null || tracker.getId() == null) {
            return;
        }
        String lab = labName(tracker);
        String team = tracker.getLabOwner().getName();
        String status = tracker.getLabStatus() == LabStatus.DEGRADED ? "is degraded" : "has failing parts";
        String body = tracker.getLabStatus() == LabStatus.DEGRADED
                ? "Some of its services or VMs are not running. Open the environment to see which."
                : "It is up, but part of it did not come back. Open the environment to see which.";
        notifications.send(teamMembers(tracker.getLabOwner().getId()), LAB_DEGRADED, lab + " " + status, body,
                "/environments/" + tracker.getId(), LAB_QUIET);
        notifications.send(facilitators(), LAB_ATTENTION, team + ": " + lab + " " + status, body, "/run/live", LAB_QUIET);
    }

    /**
     * Challenges whose scheduled release time passed in (from, to]. Challenges released the moment
     * they were created are announced from the audit event instead. Several at once in one room
     * become one notification.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releasedBetween(LocalDateTime from, LocalDateTime to) {
        Map<String, List<CTFEntity>> byRoom = new java.util.LinkedHashMap<>();
        entityManager.createQuery(
                        "SELECT c FROM CTFEntity c WHERE c.room IS NOT NULL AND c.releaseDate > :from AND c.releaseDate <= :to"
                                + " ORDER BY c.releaseDate, c.createdAt", CTFEntity.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultStream()
                .filter(NotificationRules::playable)
                .filter(NotificationRules::scheduled)
                .forEach(challenge -> byRoom.computeIfAbsent(challenge.getRoom().getId(), id -> new ArrayList<>()).add(challenge));
        byRoom.values().forEach(challenges -> {
            if (challenges.size() == 1) {
                announce(challenges.getFirst());
            } else {
                announceMany(challenges);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, ServerHealth> serverHealth() {
        Map<String, ServerHealth> health = new java.util.HashMap<>();
        entityManager.createQuery("SELECT s.remoteServer.id, s.remoteServer.name, s.status FROM RemoteServerStats s"
                        + " WHERE s.remoteServer IS NOT NULL", Object[].class)
                .getResultList()
                .forEach(row -> health.put((String) row[0], new ServerHealth((String) row[1], (LabStatus) row[2])));
        return health;
    }

    public record ServerHealth(String name, LabStatus status) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void serverChanged(String serverId, String name, boolean online) {
        String link = "/admin/servers?server=" + serverId;
        if (online) {
            notifications.send(admins(), SERVER_ONLINE, name + " is online",
                    "It answers health checks again. Labs on it can start and change.", link);
        } else {
            notifications.send(admins(), SERVER_OFFLINE, name + " went offline",
                    "It stopped answering health checks. Labs on it cannot start or change until it answers again.", link);
        }
    }

    private void pointsAwarded(AuditEvent event) {
        Matcher matcher = AWARD.matcher(event.getDetail() == null ? "" : event.getDetail());
        if (event.getTargetId() == null || !matcher.matches()) {
            return;
        }
        int points = Integer.parseInt(matcher.group(1));
        String roomName = matcher.group(2);
        String reason = matcher.group(3);
        Optional<String> roomId = entityManager.createQuery(
                        "SELECT ru.room.id FROM RoomUser ru WHERE ru.user.id = :user AND ru.room.name = :name", String.class)
                .setParameter("user", event.getTargetId())
                .setParameter("name", roomName)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
        String link = roomId.map(id -> "/labs/" + id + "/scoreboard/" + event.getTargetLabel()).orElse("/scoreboard");
        String from = event.getActorUsername() != null && !"system".equals(event.getActorUsername())
                ? " · from " + event.getActorUsername() : "";
        notifications.send(List.of(event.getTargetId()), POINTS_AWARDED,
                (points >= 0 ? "+" : "") + points + " pts in " + roomName, reason + from, link);
    }

    private void roleChanged(AuditEvent event) {
        String detail = event.getDetail() == null ? "" : event.getDetail();
        int to = detail.lastIndexOf(" to ");
        String role = to >= 0 ? roleLabel(detail.substring(to + 4).trim()) : "changed";
        notifications.send(List.of(event.getTargetId()), ROLE_CHANGED, "Your role is now " + role,
                "You were signed out so the new role applies. Your menu shows what you can use now.", "/home");
    }

    private void labActionFailed(AuditEvent event) {
        if (event.getTeamId() == null || event.getTargetId() == null) {
            return;
        }
        Matcher matcher = ACTION_FAILED.matcher(event.getDetail() == null ? "" : event.getDetail().trim());
        String verb = matcher.matches() ? matcher.group(1).toLowerCase(Locale.ROOT) : "run";
        String reason = matcher.matches() && matcher.group(2) != null ? firstLine(matcher.group(2)) : null;
        String lab = event.getTargetLabel() != null ? event.getTargetLabel() : "A lab";
        String title = lab + " failed to " + verb;
        String body = reason != null && !reason.isBlank() && !"no output".equals(reason)
                ? reason : "Open the lab to see its logs.";

        List<String> team = new ArrayList<>(teamMembers(event.getTeamId()));
        // The person who ran the action already saw why
        team.remove(event.getActorId());
        notifications.send(team, LAB_FAILED, title, body, "/environments/" + event.getTargetId());

        String teamName = entityManager.createQuery("SELECT t.name FROM Team t WHERE t.id = :id", String.class)
                .setParameter("id", event.getTeamId()).getResultStream().findFirst().orElse("A team");
        List<String> watchers = new ArrayList<>(facilitators());
        watchers.remove(event.getActorId());
        notifications.send(watchers, LAB_ATTENTION, teamName + ": " + title, body, "/run/live");
    }

    private void challengeCreated(AuditEvent event) {
        if (event.getTargetId() == null) {
            return;
        }
        CTFEntity challenge = entityManager.find(CTFEntity.class, event.getTargetId());
        if (challenge != null && challenge.getRoom() != null && playable(challenge)
                && (challenge.getReleaseDate() == null || !challenge.getReleaseDate().isAfter(LocalDateTime.now()))) {
            announce(challenge);
        }
    }

    private void announce(CTFEntity challenge) {
        List<String> players = joinedPlayers(challenge.getRoom().getId());
        String body = (challenge.getCategory() != null && !challenge.getCategory().isBlank() ? challenge.getCategory() + " · " : "")
                + (challenge.getPoints() == null ? 0 : challenge.getPoints()) + " pts";
        notifications.send(players, CHALLENGE_RELEASED, challenge.getQuestion() + " is open in " + challenge.getRoom().getName(),
                body, "/labs/" + challenge.getRoom().getId() + "?challenge=" + challenge.getId());
    }

    private void announceMany(List<CTFEntity> challenges) {
        CTFEntity first = challenges.getFirst();
        String names = String.join(", ", challenges.stream().limit(3).map(CTFEntity::getQuestion).toList())
                + (challenges.size() > 3 ? " and " + (challenges.size() - 3) + " more" : "");
        notifications.send(joinedPlayers(first.getRoom().getId()), CHALLENGE_RELEASED,
                challenges.size() + " new challenges are open in " + first.getRoom().getName(), names,
                "/labs/" + first.getRoom().getId());
    }

    private List<String> joinedPlayers(String roomId) {
        return entityManager.createQuery(
                        "SELECT ru.user.id FROM RoomUser ru WHERE ru.room.id = :room AND ru.roomUserStatus = :joined"
                                + " AND (ru.user.disabled IS NULL OR ru.user.disabled = false)", String.class)
                .setParameter("room", roomId)
                .setParameter("joined", RoomUserStatus.JOINED)
                .getResultList();
    }

    /** A site team's accounts, or a lab team's members. */
    List<String> teamMembers(String teamId) {
        List<String> site = entityManager.createQuery("SELECT u.id FROM User u WHERE u.team.id = :team"
                        + " AND (u.disabled IS NULL OR u.disabled = false)", String.class)
                .setParameter("team", teamId)
                .getResultList();
        List<String> lab = entityManager.createQuery("SELECT m.user.id FROM LabTeamMember m WHERE m.team.id = :team"
                        + " AND (m.user.disabled IS NULL OR m.user.disabled = false)", String.class)
                .setParameter("team", teamId)
                .getResultList();
        List<String> all = new ArrayList<>(site);
        lab.stream().filter(id -> !all.contains(id)).forEach(all::add);
        return all;
    }

    /** Facilitators, plus anyone named as a facilitator of a room. */
    List<String> facilitators() {
        Set<String> ids = new LinkedHashSet<>(entityManager.createQuery("SELECT u.id FROM User u WHERE u.role = :role"
                        + " AND (u.disabled IS NULL OR u.disabled = false)", String.class)
                .setParameter("role", Role.FACILITATOR)
                .getResultList());
        List<String> named = entityManager.createQuery("SELECT DISTINCT f FROM Room r JOIN r.facilitators f", String.class)
                .getResultList();
        if (!named.isEmpty()) {
            ids.addAll(entityManager.createQuery("SELECT u.id FROM User u WHERE (u.id IN :named OR u.username IN :named)"
                            + " AND (u.disabled IS NULL OR u.disabled = false)", String.class)
                    .setParameter("named", named)
                    .getResultList());
        }
        return new ArrayList<>(ids);
    }

    List<String> admins() {
        return entityManager.createQuery("SELECT u.id FROM User u WHERE u.role = :role"
                        + " AND (u.disabled IS NULL OR u.disabled = false)", String.class)
                .setParameter("role", Role.ADMIN)
                .getResultList();
    }

    // Set to open later than it was written, not open on creation
    private static boolean scheduled(CTFEntity challenge) {
        return challenge.getCreatedAt() == null
                || challenge.getReleaseDate().isAfter(challenge.getCreatedAt().plusSeconds(5));
    }

    private static boolean playable(CTFEntity challenge) {
        return !Boolean.FALSE.equals(challenge.getVisible()) && !Boolean.FALSE.equals(challenge.getIsActive());
    }

    private static String labName(LabTracker tracker) {
        return tracker.getLabStarted() != null && tracker.getLabStarted().getName() != null
                ? tracker.getLabStarted().getName() : "A lab";
    }

    private static String roleLabel(String role) {
        String name = role.replace('_', ' ').toLowerCase(Locale.ROOT);
        return name.isEmpty() ? role : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String firstLine(String text) {
        String line = text.strip().lines().findFirst().orElse("");
        return line.length() > 300 ? line.substring(0, 299) + "…" : line;
    }
}
