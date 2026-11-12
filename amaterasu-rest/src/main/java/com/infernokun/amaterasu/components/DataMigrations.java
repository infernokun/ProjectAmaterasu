package com.infernokun.amaterasu.components;

import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.TeamMode;
import com.infernokun.amaterasu.repositories.ctf.CTFEntityRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomRepository;
import com.infernokun.amaterasu.repositories.lab.LabRepository;
import com.infernokun.amaterasu.utils.AmaterasuConstants;
import com.infernokun.amaterasu.utils.ScenarioText;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fills columns added after data already existed. Hibernate adds the columns empty; each step
 * here only touches rows where they are still empty, so running it again changes nothing.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class DataMigrations implements ApplicationRunner {
    private final EntityManager entityManager;
    private final CTFEntityRepository ctfEntityRepository;
    private final RoomRepository roomRepository;
    private final LabRepository labRepository;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        // Plain SQL on columns the entities no longer map, so it runs on its own first
        transaction.executeWithoutResult(status -> siteWideTeams());
        transaction.executeWithoutResult(status -> {
            publishExistingLabs();
            publishExistingRooms();
            siteTeamsForExistingLabs();
            orderExistingChallenges();
            mergeRoomsAndLabs();
        });
    }

    /** Labs from before drafts existed were already visible to teams, so they stay published. */
    void publishExistingLabs() {
        int updated = entityManager.createQuery("UPDATE Lab l SET l.published = true WHERE l.published IS NULL")
                .executeUpdate();
        if (updated > 0) {
            log.info("Marked {} existing labs as published", updated);
        }
    }

    /** Labs (rooms) from before drafts existed were already open to players. */
    void publishExistingRooms() {
        int updated = entityManager.createQuery("UPDATE Room r SET r.published = true WHERE r.published IS NULL")
                .executeUpdate();
        if (updated > 0) {
            log.info("Marked {} existing labs as published", updated);
        }
    }

    /** Labs from before per-lab teams keep using the site teams. */
    public void siteTeamsForExistingLabs() {
        int updated = entityManager.createQuery("UPDATE Room r SET r.teamMode = :site WHERE r.teamMode IS NULL")
                .setParameter("site", TeamMode.SITE)
                .executeUpdate();
        if (updated > 0) {
            log.info("Existing labs keep the site teams: {} set to team mode SITE", updated);
        }
    }

    /**
     * Challenges without a position get one after the room's positioned challenges, in the order
     * rooms used to show them: by category, then points.
     */
    void orderExistingChallenges() {
        List<CTFEntity> unordered = ctfEntityRepository.findAll().stream()
                .filter(challenge -> challenge.getSortOrder() == null && challenge.getRoom() != null)
                .toList();
        if (unordered.isEmpty()) {
            return;
        }
        Map<String, List<CTFEntity>> byRoom = unordered.stream()
                .collect(Collectors.groupingBy(challenge -> challenge.getRoom().getId()));
        byRoom.forEach((roomId, challenges) -> {
            int next = ctfEntityRepository.findByRoomId(roomId).stream()
                    .map(CTFEntity::getSortOrder).filter(Objects::nonNull)
                    .max(Integer::compare).map(max -> max + 1).orElse(0);
            List<CTFEntity> sorted = challenges.stream().sorted(Comparator
                    .comparing((CTFEntity challenge) -> challenge.getCategory() == null ? "" : challenge.getCategory())
                    .thenComparing(challenge -> challenge.getPoints() == null ? 0 : challenge.getPoints())
                    .thenComparing(challenge -> challenge.getQuestion() == null ? "" : challenge.getQuestion()))
                    .toList();
            for (CTFEntity challenge : sorted) {
                challenge.setSortOrder(next++);
            }
            ctfEntityRepository.saveAll(sorted);
        });
        log.info("Gave {} existing challenges a position in their room", unordered.size());
    }

    /**
     * Rooms and labs became one concept. On the first start after that, each room is paired with the
     * lab definition most of its challenges used (each team keeps its own copy, as before) or with
     * none, and gets a scenario from what it had. Every published lab definition no room uses gets a
     * lab of its own, so it can still be started. Later starts change nothing.
     */
    public void mergeRoomsAndLabs() {
        List<Room> rooms = roomRepository.findAll();
        List<Room> unpaired = rooms.stream().filter(room -> room.getEnvironmentMode() == null).toList();
        boolean firstRun = rooms.isEmpty() || !unpaired.isEmpty();
        if (!firstRun) {
            return;
        }
        Map<String, Lab> labs = labRepository.findAll().stream()
                .collect(Collectors.toMap(Lab::getId, lab -> lab, (a, b) -> a, LinkedHashMap::new));
        for (Room room : unpaired) {
            List<CTFEntity> challenges = ctfEntityRepository.findByRoomId(room.getId());
            Optional<Lab> environment = mostUsedLab(challenges, labs);
            room.setEnvironment(environment.orElse(null));
            room.setEnvironmentMode(environment.isPresent() ? EnvironmentMode.PER_TEAM : EnvironmentMode.NONE);
            if (room.getScenario() == null || room.getScenario().isBlank()) {
                room.setScenario(ScenarioText.forExisting(room.getName(), environment.orElse(null), challenges.size()));
            }
            roomRepository.save(room);
            log.info("Room {} is now a lab with {}", room.getName(),
                    environment.map(lab -> "environment " + lab.getName() + " (one copy per team)").orElse("no environment"));
        }

        Set<String> used = roomRepository.findAll().stream().map(Room::getEnvironmentId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> names = new HashSet<>();
        roomRepository.findAll().forEach(room -> names.add(room.getName() == null ? "" : room.getName().toLowerCase()));
        for (Lab lab : labs.values()) {
            if (used.contains(lab.getId()) || !Boolean.TRUE.equals(lab.getPublished())) {
                continue;
            }
            String name = freeName(lab.getName(), names);
            names.add(name.toLowerCase());
            Room room = Room.builder()
                    .name(name)
                    .surroundTag(AmaterasuConstants.DEFAULT_SURROUND_TAG)
                    .scenario(ScenarioText.forExisting(name, lab, 0))
                    .environment(lab)
                    .environmentMode(EnvironmentMode.PER_TEAM)
                    .build();
            roomRepository.save(room);
            log.info("Lab definition {} had no room, so it got the lab {}", lab.getName(), name);
        }
    }

    /** The lab definition most of the challenges use; ties go to the one used first. */
    static Optional<Lab> mostUsedLab(List<CTFEntity> challenges, Map<String, Lab> labs) {
        Map<String, Integer> uses = new LinkedHashMap<>();
        challenges.stream()
                .sorted(Comparator.comparing(CTFEntity::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(CTFEntity::getLabId)
                .filter(id -> id != null && labs.containsKey(id))
                .forEach(id -> uses.merge(id, 1, Integer::sum));
        return uses.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry<String, Integer>::getValue)
                        .thenComparing(entry -> -indexOf(uses, entry.getKey())))
                .map(entry -> labs.get(entry.getKey()));
    }

    private static int indexOf(Map<String, Integer> uses, String key) {
        int i = 0;
        for (String id : uses.keySet()) {
            if (id.equals(key)) {
                return i;
            }
            i++;
        }
        return i;
    }

    private static String freeName(String name, Set<String> taken) {
        String base = name == null || name.isBlank() ? "Lab" : name.trim();
        String candidate = base;
        for (int n = 2; taken.contains(candidate.toLowerCase()); n++) {
            candidate = base + " (" + n + ")";
        }
        return candidate;
    }

    /**
     * Teams became site-wide and admin-managed, and nobody joins a lab. Each team that lived inside a
     * lab becomes a site team, merged into the one with the same name if there is one; its copies go
     * with it. A user who was on several keeps the one they joined most recently. Copies with no team
     * and no lab become the solo copy. Runs while room_user still has its lab team column, which it
     * drops at the end.
     */
    public void siteWideTeams() {
        if (!columnExists("room_user", "lab_team_id") || !columnExists("team", "room_id")) {
            return;
        }
        record TeamRow(String id, String name, String roomId) {
        }
        List<TeamRow> teams = jdbc.query("SELECT id, name, room_id FROM team ORDER BY created_at NULLS FIRST, id",
                (rs, i) -> new TeamRow(rs.getString("id"), rs.getString("name"), rs.getString("room_id")));
        Map<String, String> byName = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        teams.forEach(team -> names.put(team.id(), team.name()));
        teams.stream().filter(team -> team.roomId() == null)
                .forEach(team -> byName.putIfAbsent(key(team.name()), team.id()));
        Map<String, String> target = new LinkedHashMap<>();
        for (TeamRow team : teams) {
            if (team.roomId() == null) {
                continue;
            }
            String site = byName.putIfAbsent(key(team.name()), team.id());
            target.put(team.id(), site != null ? site : team.id());
        }

        record Membership(String userId, String teamId, LocalDateTime at) {
        }
        List<Membership> memberships = new ArrayList<>(jdbc.query(
                "SELECT id, team_id, updated_at, created_at FROM users WHERE team_id IS NOT NULL",
                (rs, i) -> new Membership(rs.getString("id"), rs.getString("team_id"),
                        time(rs.getTimestamp("updated_at"), rs.getTimestamp("created_at")))));
        memberships.addAll(jdbc.query(
                "SELECT user_id, lab_team_id, updated_at, created_at FROM room_user WHERE lab_team_id IS NOT NULL",
                (rs, i) -> new Membership(rs.getString("user_id"),
                        target.getOrDefault(rs.getString("lab_team_id"), rs.getString("lab_team_id")),
                        time(rs.getTimestamp("updated_at"), rs.getTimestamp("created_at")))));
        Map<String, List<Membership>> byUser = memberships.stream()
                .collect(Collectors.groupingBy(Membership::userId, LinkedHashMap::new, Collectors.toList()));
        byUser.forEach((userId, list) -> {
            Membership latest = list.stream().max(Comparator.comparing(Membership::at)).orElseThrow();
            jdbc.update("UPDATE users SET team_id = ? WHERE id = ?", latest.teamId(), userId);
            Set<String> on = list.stream().map(Membership::teamId).collect(Collectors.toCollection(java.util.TreeSet::new));
            String username = jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, userId);
            if (on.size() > 1) {
                log.info("Teams: {} was on {}; kept {}, the most recent", username,
                        on.stream().map(names::get).toList(), names.get(latest.teamId()));
            } else {
                log.info("Teams: {} is on {}", username, names.get(latest.teamId()));
            }
        });

        jdbc.update("UPDATE room_user SET lab_team_id = NULL");
        target.forEach((labTeam, site) -> {
            if (labTeam.equals(site)) {
                log.info("Teams: {} is now a site team", names.get(labTeam));
                return;
            }
            int moved = jdbc.update("UPDATE lab_tracker SET team_id = ? WHERE team_id = ?", site, labTeam);
            jdbc.update("UPDATE users SET team_id = ? WHERE team_id = ?", site, labTeam);
            jdbc.update("DELETE FROM team WHERE id = ?", labTeam);
            log.info("Teams: {} ({}) merged into the site team of that name, with {} copies", names.get(labTeam), labTeam, moved);
        });
        int solo = jdbc.update("UPDATE lab_tracker SET solo = TRUE WHERE team_id IS NULL AND room_id IS NULL"
                + " AND solo IS NULL AND lab_status <> 'DELETED'");
        if (solo > 0) {
            log.info("Teams: {} copies without a team or lab became solo copies", solo);
        }
        jdbc.execute("ALTER TABLE room_user DROP COLUMN lab_team_id");
        jdbc.execute("ALTER TABLE team DROP COLUMN room_id");
        jdbc.execute("ALTER TABLE room DROP COLUMN IF EXISTS team_mode");
        jdbc.execute("ALTER TABLE room DROP COLUMN IF EXISTS max_team_size");
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns"
                + " WHERE LOWER(table_name) = ? AND LOWER(column_name) = ?", Integer.class, table, column);
        return count != null && count > 0;
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static LocalDateTime time(java.sql.Timestamp updated, java.sql.Timestamp created) {
        java.sql.Timestamp at = updated != null ? updated : created;
        return at != null ? at.toLocalDateTime() : LocalDateTime.MIN;
    }
}
