package com.infernokun.amaterasu.components;

import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.repositories.ctf.CTFEntityRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomRepository;
import com.infernokun.amaterasu.repositories.lab.LabRepository;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomUserRepository;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.TeamCredit;
import com.infernokun.amaterasu.models.enums.TeamMode;
import com.infernokun.amaterasu.utils.AmaterasuConstants;
import com.infernokun.amaterasu.utils.ScenarioText;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
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
    private final RoomUserRepository roomUserRepository;
    private final TeamRepository teamRepository;
    private final LabTrackerRepository labTrackerRepository;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            publishExistingLabs();
            publishExistingRooms();
            orderExistingChallenges();
            mergeRoomsAndLabs();
            moveTeamsIntoLabs();
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
     * Teams moved inside labs. For each lab from before (no team mode yet), the Admin teams of the
     * players who joined it become teams in that lab with the same members, and their copies of the
     * lab's environment move to those teams. Labs then allow teams, sized for the biggest one.
     */
    public void moveTeamsIntoLabs() {
        for (Room room : roomRepository.findAll()) {
            if (room.getTeamMode() != null) {
                continue;
            }
            Map<String, Team> byGlobal = new LinkedHashMap<>();
            for (RoomUser seat : roomUserRepository.findByRoomId(room.getId())) {
                Team global = seat.getUser() != null ? seat.getUser().getTeam() : null;
                if (seat.getTeam() != null || global == null || global.getRoomId() != null) {
                    continue;
                }
                Team labTeam = byGlobal.computeIfAbsent(global.getId(), id -> teamRepository.findByRoomId(room.getId()).stream()
                        .filter(team -> team.getName() != null && team.getName().equalsIgnoreCase(global.getName()))
                        .findFirst()
                        .orElseGet(() -> teamRepository.save(Team.builder().name(global.getName())
                                .description(global.getDescription()).roomId(room.getId()).build())));
                seat.setTeam(labTeam);
                roomUserRepository.save(seat);
            }
            if (room.getEnvironment() != null && room.effectiveMode() == EnvironmentMode.PER_TEAM) {
                byGlobal.forEach((globalId, labTeam) -> {
                    Team global = teamRepository.findById(globalId).orElse(null);
                    if (global == null) {
                        return;
                    }
                    for (LabTracker tracker : labTrackerRepository.findAllByLabStartedAndLabOwnerAndLabStatusNot(
                            room.getEnvironment(), global, LabStatus.DELETED)) {
                        if (tracker.isShared()) {
                            continue;
                        }
                        tracker.setLabOwner(labTeam);
                        labTrackerRepository.save(tracker);
                        global.getTeamActiveLabs().remove(tracker.getId());
                        if (!labTeam.getTeamActiveLabs().contains(tracker.getId())) {
                            labTeam.getTeamActiveLabs().add(tracker.getId());
                        }
                        log.info("{}'s copy of {} now belongs to its team in {}", global.getName(),
                                room.getEnvironment().getName(), room.getName());
                    }
                    teamRepository.save(global);
                    teamRepository.save(labTeam);
                });
            }
            int largest = byGlobal.values().stream()
                    .mapToInt(team -> (int) roomUserRepository.findByTeamId(team.getId()).size()).max().orElse(0);
            room.setTeamMode(TeamMode.TEAMS_OPTIONAL);
            room.setMaxTeamSize(Math.max(4, largest));
            room.setTeamCredit(TeamCredit.SEE_TEAMMATES);
            roomRepository.save(room);
            log.info("{} now has {} team(s) inside it", room.getName(), byGlobal.size());
        }
    }
}
