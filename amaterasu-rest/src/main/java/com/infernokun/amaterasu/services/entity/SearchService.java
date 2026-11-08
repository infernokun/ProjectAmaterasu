package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.LabType;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.models.enums.ServerType;
import com.infernokun.amaterasu.models.search.SearchResults;
import com.infernokun.amaterasu.models.search.SearchResults.Hit;
import com.infernokun.amaterasu.models.search.SearchResults.Type;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The top bar search. Everyone finds their team's lab copies, the lab catalog, published labs and
 * their released challenges. Authors also find every lab, room and challenge; admins also find
 * users, teams and servers.
 */
@Service
@RequiredArgsConstructor
public class SearchService {
    public static final int MIN_QUERY = 2;
    public static final int MAX_LIMIT = 20;

    private final EntityManager entityManager;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public SearchResults search(String rawQuery, int limit) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < MIN_QUERY) {
            return SearchResults.empty(query);
        }
        int max = Math.min(Math.max(limit, 1), MAX_LIMIT);
        String pattern = "%" + escape(query.toLowerCase(Locale.ROOT)) + "%";

        User caller = currentUser.user();
        boolean admin = currentUser.isAdmin();
        boolean author = admin || currentUser.hasRole(Role.CREATOR);
        List<Hit> hits = new ArrayList<>(teamLabs(caller.getTeam(), pattern, query, max));
        hits.addAll(labs(author, pattern, query, max));
        hits.addAll(rooms(author, pattern, query, max));
        hits.addAll(challenges(author, pattern, query, max));
        if (admin) {
            hits.addAll(users(pattern, query, max));
            hits.addAll(teams(pattern, query, max));
            hits.addAll(servers(pattern, query, max));
        }
        return new SearchResults(query, hits);
    }

    private List<Hit> teamLabs(Team team, String pattern, String query, int max) {
        if (team == null) {
            return List.of();
        }
        List<LabTracker> trackers = list(entityManager.createQuery(
                "SELECT t FROM LabTracker t WHERE t.labOwner.id = :team AND t.labStatus <> :deleted"
                        + " AND LOWER(t.labStarted.name) LIKE :q ESCAPE '\\' ORDER BY t.labStarted.name", LabTracker.class)
                .setParameter("team", team.getId())
                .setParameter("deleted", LabStatus.DELETED)
                .setParameter("q", pattern), max);
        return ranked(trackers, tracker -> tracker.getLabStarted().getName(), query, max).stream()
                .map(tracker -> new Hit(Type.TEAM_LAB, tracker.getId(), tracker.getLabStarted().getName(),
                        join(typeLabel(tracker.getLabStarted().getLabType()),
                                tracker.getRemoteServer() != null ? "on " + tracker.getRemoteServer().getName() : null),
                        null, tracker.getLabStatus() != null ? tracker.getLabStatus().name() : null))
                .toList();
    }

    private List<Hit> labs(boolean author, String pattern, String query, int max) {
        List<Lab> labs = list(entityManager.createQuery(
                "SELECT l FROM Lab l WHERE (LOWER(l.name) LIKE :q ESCAPE '\\' OR LOWER(l.description) LIKE :q ESCAPE '\\')"
                        + (author ? "" : " AND l.ready = true") + " ORDER BY l.name", Lab.class)
                .setParameter("q", pattern), max);
        return ranked(labs, Lab::getName, query, max).stream()
                .map(lab -> new Hit(Type.LAB, lab.getId(), lab.getName(),
                        join(typeLabel(lab.getLabType()), author && !lab.isReady() ? "not ready" : null), null, null))
                .toList();
    }

    private List<Hit> rooms(boolean author, String pattern, String query, int max) {
        List<Room> rooms = list(entityManager.createQuery(
                "SELECT r FROM Room r WHERE LOWER(r.name) LIKE :q ESCAPE '\\'"
                        + (author ? "" : " AND (r.published IS NULL OR r.published = true)") + " ORDER BY r.name", Room.class)
                .setParameter("q", pattern), max);
        return ranked(rooms, Room::getName, query, max).stream()
                .map(room -> new Hit(Type.ROOM, room.getId(), room.getName(),
                        room.isLive() ? null : "Draft", null, null))
                .toList();
    }

    private List<Hit> challenges(boolean author, String pattern, String query, int max) {
        String jpql = "SELECT c FROM CTFEntity c WHERE c.room IS NOT NULL"
                + " AND (LOWER(c.question) LIKE :q ESCAPE '\\' OR LOWER(c.category) LIKE :q ESCAPE '\\')"
                + (author ? "" : " AND (c.room.published IS NULL OR c.room.published = true) AND (c.visible IS NULL OR c.visible = true)"
                + " AND (c.isActive IS NULL OR c.isActive = true) AND (c.releaseDate IS NULL OR c.releaseDate <= :now)")
                + " ORDER BY c.question";
        TypedQuery<CTFEntity> typed = entityManager.createQuery(jpql, CTFEntity.class).setParameter("q", pattern);
        if (!author) {
            typed.setParameter("now", LocalDateTime.now());
        }
        LocalDateTime now = LocalDateTime.now();
        return ranked(list(typed, max), CTFEntity::getQuestion, query, max).stream()
                .map(challenge -> new Hit(Type.CHALLENGE, challenge.getId(), challenge.getQuestion(),
                        join(challenge.getRoom().getName(), blankToNull(challenge.getCategory()),
                                (challenge.getPoints() == null ? 0 : challenge.getPoints()) + " pts"),
                        challenge.getRoom().getId(), challengeStatus(challenge, now)))
                .toList();
    }

    private List<Hit> users(String pattern, String query, int max) {
        List<User> users = list(entityManager.createQuery(
                "SELECT u FROM User u WHERE LOWER(u.username) LIKE :q ESCAPE '\\' OR LOWER(u.displayName) LIKE :q ESCAPE '\\'"
                        + " OR LOWER(u.email) LIKE :q ESCAPE '\\' ORDER BY u.username", User.class)
                .setParameter("q", pattern), max);
        return ranked(users, User::getUsername, query, max).stream()
                .map(user -> new Hit(Type.USER, user.getId(), user.getUsername(),
                        join(roleLabel(user.getRole()), user.getTeam() != null ? user.getTeam().getName() : "No team",
                                user.isEnabled() ? null : "disabled"),
                        null, user.getRole() != null ? user.getRole().name() : null))
                .toList();
    }

    private List<Hit> teams(String pattern, String query, int max) {
        List<Team> teams = ranked(list(entityManager.createQuery(
                "SELECT t FROM Team t WHERE LOWER(t.name) LIKE :q ESCAPE '\\' ORDER BY t.name", Team.class)
                .setParameter("q", pattern), max), Team::getName, query, max);
        Map<String, Long> members = new HashMap<>();
        if (!teams.isEmpty()) {
            entityManager.createQuery("SELECT u.team.id, COUNT(u) FROM User u WHERE u.team.id IN :ids GROUP BY u.team.id",
                            Object[].class)
                    .setParameter("ids", teams.stream().map(Team::getId).toList())
                    .getResultList()
                    .forEach(row -> members.put((String) row[0], (Long) row[1]));
        }
        return teams.stream()
                .map(team -> {
                    long count = members.getOrDefault(team.getId(), 0L);
                    return new Hit(Type.TEAM, team.getId(), team.getName(),
                            count + (count == 1 ? " member" : " members"), null, null);
                })
                .toList();
    }

    private List<Hit> servers(String pattern, String query, int max) {
        List<RemoteServer> servers = list(entityManager.createQuery(
                "SELECT s FROM RemoteServer s WHERE LOWER(s.name) LIKE :q ESCAPE '\\' OR LOWER(s.ipAddress) LIKE :q ESCAPE '\\'"
                        + " OR LOWER(s.nodeName) LIKE :q ESCAPE '\\' ORDER BY s.name", RemoteServer.class)
                .setParameter("q", pattern), max);
        return ranked(servers, RemoteServer::getName, query, max).stream()
                .map(server -> {
                    LabStatus status = server.getRemoteServerStats() != null ? server.getRemoteServerStats().getStatus() : null;
                    return new Hit(Type.SERVER, server.getId(), server.getName(),
                            join(server.getServerType() == ServerType.PROXMOX ? "Proxmox node" : "Docker host",
                                    server.getIpAddress()),
                            null, status != null ? status.name() : null);
                })
                .toList();
    }

    // Enough candidates to rank by prefix without loading whole tables
    private static <T> List<T> list(TypedQuery<T> query, int max) {
        return query.setMaxResults(max * 4).getResultList();
    }

    /** Names that start with the query first, then the rest, each alphabetically. */
    static <T> List<T> ranked(Collection<T> items, Function<T, String> name, String query, int max) {
        String q = query.toLowerCase(Locale.ROOT);
        return items.stream()
                .sorted(Comparator.comparing((T item) -> {
                            String value = name.apply(item);
                            return value == null || !value.toLowerCase(Locale.ROOT).startsWith(q);
                        })
                        .thenComparing(item -> name.apply(item) == null ? "" : name.apply(item).toLowerCase(Locale.ROOT)))
                .limit(max)
                .toList();
    }

    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String challengeStatus(CTFEntity challenge, LocalDateTime now) {
        if (Boolean.FALSE.equals(challenge.getVisible()) || Boolean.FALSE.equals(challenge.getIsActive())) {
            return "HIDDEN";
        }
        if (challenge.getReleaseDate() != null && now.isBefore(challenge.getReleaseDate())) {
            return "UNRELEASED";
        }
        return null;
    }

    private static String typeLabel(LabType type) {
        if (type == null) {
            return "Lab";
        }
        return switch (type) {
            case DOCKER_COMPOSE -> "Docker Compose";
            case DOCKER_CONTAINER -> "Docker container";
            case VIRTUAL_MACHINE -> "Proxmox VM";
            case KUBERNETES -> "Kubernetes";
            default -> "Lab";
        };
    }

    private static String roleLabel(Role role) {
        if (role == null) {
            return "No role";
        }
        String name = role.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String join(String... parts) {
        return java.util.Arrays.stream(parts).filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" · "));
    }
}
