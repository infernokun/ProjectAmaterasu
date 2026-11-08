package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.exceptions.ResourceInUseException;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.enums.TeamCredit;
import com.infernokun.amaterasu.models.enums.TeamMode;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomUserRepository;
import com.infernokun.amaterasu.repositories.lab.LabRepository;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.utils.ScenarioText;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoomService {
    private static final Set<LabStatus> NOT_DEPLOYED = Set.of(LabStatus.STOPPED, LabStatus.DELETED, LabStatus.NONE);
    public static final int MAX_TEAM_SIZE = 50;

    private final RoomRepository roomRepository;
    private final RoomUserRepository roomUserRepository;
    private final CTFEntityService ctfEntityService;
    private final LabRepository labRepository;
    private final LabTrackerRepository labTrackerRepository;
    private final LabFileService labFileService;
    private final TeamRepository teamRepository;

    public List<Room> findAllRooms() {
        return roomRepository.findAll();
    }

    public Room findByRoomName(String roomName) {
        return roomRepository.findByName(roomName)
                .orElseThrow(() -> new ResourceNotFoundException("Room with name '" + roomName + "' not found."));
    }

    public Room findByRoomId(String id) {
        return roomRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Room with id '" + id + "' not found."));
    }

    public List<Room> findAllByIds(List<String> ids) {
        return roomRepository.findAllByIdIn(ids);
    }

    public Room saveRoom(Room room) { return roomRepository.save(room); }

    public List<Room> saveRooms(List<Room> rooms) {
        return roomRepository.saveAll(rooms);
    }

    /**
     * Deletes a lab with its questions, the answers given and who joined it. Refused while its shared
     * environment is deployed; a stopped shared copy goes with it.
     */
    @Transactional
    public Room deleteRoom(String id) {
        Room room = findByRoomId(id);
        List<LabTracker> shared = labTrackerRepository.findByRoomId(id);
        if (shared.stream().anyMatch(RoomService::deployed)) {
            throw new ResourceInUseException(room.getName() + "'s shared environment is still deployed. Stop it first.");
        }
        List<Team> teams = teamRepository.findByRoomId(id);
        List<LabTracker> teamCopies = teams.stream()
                .flatMap(team -> labTrackerRepository.findLabTrackersByLabOwner(team).stream()).toList();
        if (teamCopies.stream().anyMatch(RoomService::deployed)) {
            throw new ResourceInUseException("A team in " + room.getName() + " still has its copy deployed. Stop it first.");
        }
        labTrackerRepository.deleteAll(shared);
        labTrackerRepository.deleteAll(teamCopies);
        labFileService.deleteAll(id);
        ctfEntityService.deleteByRoomId(id);
        roomUserRepository.deleteAll(roomUserRepository.findByRoomId(id));
        teamRepository.deleteAll(teams);
        roomRepository.delete(room);
        return room;
    }

    /** A trimmed, unused room name. */
    public String requireUniqueName(String name, String exceptId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Lab name required");
        }
        boolean taken = roomRepository.findAll().stream()
                .anyMatch(other -> trimmed.equalsIgnoreCase(other.getName()) && !other.getId().equals(exceptId));
        if (taken) {
            throw new IllegalArgumentException("A lab named " + trimmed + " already exists");
        }
        return trimmed;
    }

    public String requireScenario(String scenario) {
        if (scenario == null || scenario.isBlank()) {
            throw new IllegalArgumentException("A lab needs a scenario: the situation, objectives, how to get in and the rules");
        }
        if (scenario.length() > ScenarioText.MAX_LENGTH) {
            throw new IllegalArgumentException("Keep the scenario under " + ScenarioText.MAX_LENGTH + " characters");
        }
        return scenario.strip();
    }

    /** Sets a new lab's environment: none, or a lab definition run shared or per team. */
    public void applyEnvironment(Room room, EnvironmentMode mode, String environmentId) {
        if (mode == EnvironmentMode.NONE) {
            room.setEnvironment(null);
            room.setEnvironmentMode(EnvironmentMode.NONE);
            return;
        }
        if (environmentId == null || environmentId.isBlank()) {
            throw new IllegalArgumentException("Pick the environment players work in, or choose no environment");
        }
        Lab environment = labRepository.findById(environmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment " + environmentId + " not found"));
        room.setEnvironment(environment);
        room.setEnvironmentMode(mode);
    }

    /** Changes an existing lab's environment; refused while its shared copy is deployed. */
    public void changeEnvironment(Room room, EnvironmentMode mode, String environmentId) {
        boolean same = mode == room.effectiveMode()
                && (mode == EnvironmentMode.NONE || Objects.equals(environmentId, room.getEnvironmentId()));
        if (same) {
            return;
        }
        if (labTrackerRepository.findByRoomId(room.getId()).stream().anyMatch(RoomService::deployed)) {
            throw new ResourceInUseException(room.getName() + "'s shared environment is deployed. Stop it before changing the environment.");
        }
        applyEnvironment(room, mode, environmentId);
    }

    /** Sets who plays together. One copy per team needs teams. */
    public void applyTeams(Room room, TeamMode mode, Integer maxTeamSize, TeamCredit credit) {
        if (mode != null) {
            room.setTeamMode(mode);
        }
        if (maxTeamSize != null) {
            if (maxTeamSize < 0 || maxTeamSize > MAX_TEAM_SIZE) {
                throw new IllegalArgumentException("A team can have up to " + MAX_TEAM_SIZE + " players, or 0 for no limit");
            }
            room.setMaxTeamSize(maxTeamSize == 0 ? null : maxTeamSize);
        }
        if (credit != null) {
            room.setTeamCredit(credit);
        }
        if (room.effectiveMode() == EnvironmentMode.PER_TEAM && room.effectiveTeamMode() == TeamMode.SOLO_ONLY) {
            throw new IllegalArgumentException("One copy per team needs teams. Allow teams, or run one shared copy.");
        }
    }

    private static boolean deployed(LabTracker tracker) {
        return tracker.getLabStatus() != null && !NOT_DEPLOYED.contains(tracker.getLabStatus());
    }
}
