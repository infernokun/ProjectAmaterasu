package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import com.infernokun.amaterasu.models.enums.TeamMode;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomUserRepository;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Teams inside a lab: joining solo or with a team, creating one, switching and leaving. */
@Service
@RequiredArgsConstructor
public class LabTeamService {
    public static final int MAX_NAME = 40;

    private final TeamRepository teamRepository;
    private final RoomUserRepository roomUserRepository;
    private final LabTrackerRepository labTrackerRepository;

    /** A lab team with the usernames on it. */
    public record LabTeamView(String id, String name, List<String> members, int size, boolean full) {
    }

    /** Joins with no team when both are null, else the team picked or a new one by that name. */
    public record JoinRequest(String teamId, String newTeamName) {
    }

    public List<LabTeamView> teams(Room room) {
        List<RoomUser> seats = roomUserRepository.findByRoomId(room.getId());
        return teamRepository.findByRoomId(room.getId()).stream()
                .map(team -> view(room, team, seats))
                .sorted(Comparator.comparing(LabTeamView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Optional<Team> teamOf(Room room, User user) {
        if (user == null) {
            return Optional.empty();
        }
        return roomUserRepository.findByUserAndRoom(user, room).map(RoomUser::getTeam);
    }

    /** The user's Admin team and every team they are on inside a lab they joined. */
    public List<Team> teamsOf(User user) {
        List<Team> teams = new java.util.ArrayList<>();
        if (user.getTeam() != null) {
            teams.add(user.getTeam());
        }
        roomUserRepository.findAllByUserId(user.getId()).stream()
                .filter(seat -> seat.getRoomUserStatus() == RoomUserStatus.JOINED && seat.getTeam() != null)
                .map(RoomUser::getTeam)
                .filter(team -> teams.stream().noneMatch(known -> known.getId().equals(team.getId())))
                .forEach(teams::add);
        return teams;
    }

    /** Whether the user is on the team: a lab team through their seat in its lab, else as an Admin team member. */
    public boolean isMember(User user, Team team) {
        if (user == null || team == null) {
            return false;
        }
        if (team.getRoomId() == null) {
            return user.getTeam() != null && team.getId().equals(user.getTeam().getId());
        }
        return roomUserRepository.findAllByUserId(user.getId()).stream()
                .anyMatch(seat -> seat.getRoomUserStatus() == RoomUserStatus.JOINED && seat.getTeam() != null
                        && team.getId().equals(seat.getTeam().getId()));
    }

    /** Joins the lab, or rejoins it, solo or on a team, as the lab allows. */
    @Transactional
    public RoomUser join(Room room, User user, JoinRequest request) {
        RoomUser seat = roomUserRepository.findByUserAndRoom(user, room).orElseGet(() -> {
            RoomUser fresh = new RoomUser();
            fresh.setUser(user);
            fresh.setRoom(room);
            fresh.setPoints(0);
            return fresh;
        });
        seat.setRoomUserStatus(RoomUserStatus.JOINED);
        seat.setUpdatedAt(LocalDateTime.now());
        // Rejoining without a choice keeps the team the player had
        if (request != null || seat.getTeam() == null) {
            seat.setTeam(pick(room, user, request, seat.getTeam()));
        }
        RoomUser saved = roomUserRepository.save(seat);
        return saved;
    }

    /** Moves a joined player to another team, a new one, or to playing solo. */
    @Transactional
    public RoomUser changeTeam(Room room, User user, JoinRequest request) {
        RoomUser seat = roomUserRepository.findByUserAndRoom(user, room)
                .filter(found -> found.getRoomUserStatus() == RoomUserStatus.JOINED)
                .orElseThrow(() -> new IllegalStateException("Join " + room.getName() + " first"));
        Team before = seat.getTeam();
        seat.setTeam(pick(room, user, request, before));
        seat.setUpdatedAt(LocalDateTime.now());
        RoomUser saved = roomUserRepository.save(seat);
        dropIfEmpty(before);
        return saved;
    }

    private Team pick(Room room, User user, JoinRequest request, Team current) {
        TeamMode mode = room.effectiveTeamMode();
        String teamId = request == null ? null : blankToNull(request.teamId());
        String newName = request == null ? null : blankToNull(request.newTeamName());
        if (teamId == null && newName == null) {
            if (mode == TeamMode.TEAMS_REQUIRED) {
                throw new IllegalArgumentException(room.getName() + " is played in teams. Join a team or create one.");
            }
            return null;
        }
        if (mode == TeamMode.SOLO_ONLY) {
            throw new IllegalArgumentException(room.getName() + " is played solo");
        }
        if (teamId != null) {
            Team team = teamRepository.findById(teamId)
                    .filter(found -> room.getId().equals(found.getRoomId()))
                    .orElseThrow(() -> new ResourceNotFoundException("That team is not in " + room.getName()));
            if (current != null && current.getId().equals(team.getId())) {
                return team;
            }
            int size = members(team).size();
            if (room.getMaxTeamSize() != null && size >= room.getMaxTeamSize()) {
                throw new IllegalStateException(team.getName() + " is full (" + room.getMaxTeamSize() + " players)");
            }
            return team;
        }
        String name = newName.trim();
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Team names can be up to " + MAX_NAME + " characters");
        }
        boolean taken = teamRepository.findByRoomId(room.getId()).stream()
                .anyMatch(other -> name.equalsIgnoreCase(other.getName()));
        if (taken) {
            throw new IllegalArgumentException("A team named " + name + " is already in " + room.getName());
        }
        Team team = Team.builder().name(name).roomId(room.getId()).build();
        team.setCreatedBy(user.getId());
        return teamRepository.save(team);
    }

    /** Joined players on the team. */
    public List<RoomUser> members(Team team) {
        return roomUserRepository.findByTeamId(team.getId()).stream()
                .filter(seat -> seat.getRoomUserStatus() == RoomUserStatus.JOINED)
                .toList();
    }

    /** A lab team nobody is on and that never ran a copy goes away. */
    private void dropIfEmpty(Team team) {
        if (team == null || team.getRoomId() == null || !roomUserRepository.findByTeamId(team.getId()).isEmpty()
                || !labTrackerRepository.findLabTrackersByLabOwner(team).isEmpty()) {
            return;
        }
        teamRepository.delete(team);
    }

    private LabTeamView view(Room room, Team team, List<RoomUser> seats) {
        List<String> members = seats.stream()
                .filter(seat -> seat.getRoomUserStatus() == RoomUserStatus.JOINED && seat.getTeam() != null
                        && team.getId().equals(seat.getTeam().getId()))
                .map(seat -> seat.getUser().getUsername())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        boolean full = room.getMaxTeamSize() != null && members.size() >= room.getMaxTeamSize();
        return new LabTeamView(team.getId(), team.getName(), members, members.size(), full);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
