package com.infernokun.amaterasu.components;

import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.LabTeamMember;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.repositories.ctf.LabTeamMemberRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Which team a user plays on in a lab. Everything team-related in a lab goes through
 * {@link #teamOf}: SITE gives the user's site team, LAB their team in that lab, NONE no team.
 */
@Component
@RequiredArgsConstructor
public class TeamResolver {
    /** The key {@link #memberCounts} uses for players without a team. */
    public static final String SOLO = "solo";

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final LabTeamMemberRepository memberRepository;
    private final RoomRepository roomRepository;

    /** The user's team in the lab, or null when they play solo there. */
    public Team teamOf(Room room, User user) {
        if (room == null || user == null) {
            return null;
        }
        return switch (room.effectiveTeamMode()) {
            case SITE -> user.getTeam();
            case NONE -> null;
            case LAB -> user.getId() == null ? null : memberRepository.findByRoomIdAndUserId(room.getId(), user.getId())
                    .map(LabTeamMember::getTeam).orElse(null);
        };
    }

    /** Every user's team in the lab, by user id. Users without one are left out. */
    public Map<String, Team> teamsByUser(Room room) {
        Map<String, Team> teams = new HashMap<>();
        switch (room.effectiveTeamMode()) {
            case SITE -> userRepository.findAll().stream().filter(user -> user.getTeam() != null)
                    .forEach(user -> teams.put(user.getId(), user.getTeam()));
            case LAB -> memberRepository.findByRoomId(room.getId())
                    .forEach(member -> teams.put(member.getUser().getId(), member.getTeam()));
            case NONE -> { }
        }
        return teams;
    }

    /** The teams that play in the lab, by name: the site teams, the lab's own, or none. */
    public List<Team> teamsIn(Room room) {
        return switch (room.effectiveTeamMode()) {
            case SITE -> teamRepository.findByLabIdIsNullOrderByNameAsc();
            case LAB -> teamRepository.findByLabIdOrderByNameAsc(room.getId());
            case NONE -> List.of();
        };
    }

    /** Whether the team plays in the lab as it is set up now. */
    public boolean playsIn(Room room, Team team) {
        return team != null && teamsIn(room).stream().anyMatch(other -> other.getId().equals(team.getId()));
    }

    /** Accounts per team id in the lab, with {@link #SOLO} counting everyone without a team. */
    public Map<String, Long> memberCounts(Room room) {
        Map<String, Team> teams = teamsByUser(room);
        Map<String, Long> counts = new HashMap<>();
        for (User user : userRepository.findAll()) {
            Team team = teams.get(user.getId());
            counts.merge(team != null ? team.getId() : SOLO, 1L, Long::sum);
        }
        return counts;
    }

    /** The users on a team in the lab. */
    public List<User> members(Room room, Team team) {
        if (team == null) {
            return List.of();
        }
        if (team.isLabTeam()) {
            return memberRepository.findByTeamId(team.getId()).stream().map(LabTeamMember::getUser).toList();
        }
        return userRepository.findAll().stream()
                .filter(user -> user.getTeam() != null && team.getId().equals(user.getTeam().getId()))
                .toList();
    }

    /** Members of a team wherever it plays: a site team's accounts, or a lab team's members. */
    public List<User> members(Team team) {
        return members(null, team);
    }

    /**
     * Whether the user works in a team or solo copy: some per-team lab running its environment
     * resolves them to it. A copy no lab runs falls back to the site team that owns it.
     */
    public boolean worksIn(User user, LabTracker tracker) {
        if (user == null || tracker == null || tracker.isShared()) {
            return false;
        }
        String environmentId = tracker.getLabStarted() != null ? tracker.getLabStarted().getId() : null;
        List<Room> rooms = environmentId == null ? List.of() : roomRepository.findAll().stream()
                .filter(room -> room.effectiveMode() == EnvironmentMode.PER_TEAM
                        && environmentId.equals(room.getEnvironmentId()))
                .toList();
        if (rooms.isEmpty()) {
            Team owner = tracker.getLabOwner();
            if (tracker.isSolo()) {
                return user.getTeam() == null;
            }
            if (owner == null) {
                return false;
            }
            return owner.isLabTeam()
                    ? memberRepository.findByTeamId(owner.getId()).stream().anyMatch(member -> member.getUser().getId().equals(user.getId()))
                    : user.getTeam() != null && owner.getId().equals(user.getTeam().getId());
        }
        return rooms.stream().anyMatch(room -> owns(tracker, teamOf(room, user)));
    }

    /** Whether the copy is the one a player on this team (or none) uses. */
    public static boolean owns(LabTracker tracker, Team team) {
        if (tracker.isSolo()) {
            return team == null;
        }
        return team != null && tracker.getLabOwner() != null && Objects.equals(team.getId(), tracker.getLabOwner().getId());
    }
}
