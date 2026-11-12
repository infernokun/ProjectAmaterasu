package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.exceptions.ResourceInUseException;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.LabTeamMember;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.ctf.dto.manage.LabTeamsView;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.EnvironmentStatus;
import com.infernokun.amaterasu.models.enums.TeamMode;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.repositories.ctf.LabTeamMemberRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomUserRepository;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.services.entity.lab.EnvironmentStatuses;
import com.infernokun.amaterasu.services.entity.lab.LabTrackerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A lab's own teams: admins and the lab's facilitators make them, name them and put people on them.
 * They count while the lab's team mode is LAB. A user is on at most one team per lab, whatever their
 * site team is.
 */
@Service
@RequiredArgsConstructor
public class LabTeamService {
    static final int MAX_NAME = 80;
    static final int MAX_DESCRIPTION = 200;

    private final TeamRepository teamRepository;
    private final LabTeamMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RoomUserRepository roomUserRepository;
    private final LabTrackerRepository labTrackerRepository;
    private final LabTrackerService labTrackerService;

    @Transactional(readOnly = true)
    public LabTeamsView view(Room room) {
        List<Team> labTeams = teamRepository.findByLabIdOrderByNameAsc(room.getId());
        Map<String, List<LabTeamMember>> members = memberRepository.findByRoomId(room.getId()).stream()
                .collect(Collectors.groupingBy(member -> member.getTeam().getId()));
        List<LabTeamsView.TeamView> own = labTeams.stream().map(team -> teamView(room, team,
                members.getOrDefault(team.getId(), List.of()).stream().map(LabTeamMember::getUser).toList())).toList();

        Map<String, List<User>> siteMembers = userRepository.findAll().stream().filter(user -> user.getTeam() != null)
                .collect(Collectors.groupingBy(user -> user.getTeam().getId()));
        List<LabTeamsView.TeamView> site = teamRepository.findByLabIdIsNullOrderByNameAsc().stream()
                .map(team -> teamView(room, team, siteMembers.getOrDefault(team.getId(), List.of()))).toList();
        return new LabTeamsView(room.getId(), room.getName(), room.effectiveTeamMode(), room.effectiveTeamCredit(),
                room.effectiveMode(), own, site);
    }

    /** Everyone who can be put on a lab team, with their site team and lab team. */
    @Transactional(readOnly = true)
    public List<LabTeamsView.Candidate> candidates(Room room) {
        Map<String, Team> labTeamOf = new HashMap<>();
        memberRepository.findByRoomId(room.getId()).forEach(member -> labTeamOf.put(member.getUser().getId(), member.getTeam()));
        Set<String> played = roomUserRepository.findByRoomId(room.getId()).stream()
                .map(RoomUser::getUser).filter(java.util.Objects::nonNull).map(User::getId).collect(Collectors.toSet());
        return userRepository.findAll().stream()
                .filter(user -> !Boolean.TRUE.equals(user.getDisabled()))
                .sorted(Comparator.comparing(User::getUsername, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(user -> {
                    Team labTeam = labTeamOf.get(user.getId());
                    return new LabTeamsView.Candidate(user.getId(), user.getUsername(), user.getDisplayName(),
                            user.getRole() != null ? user.getRole().name() : null,
                            user.getTeam() != null ? user.getTeam().getName() : null,
                            labTeam != null ? labTeam.getId() : null, labTeam != null ? labTeam.getName() : null,
                            played.contains(user.getId()));
                }).toList();
    }

    @Transactional
    public Team create(Room room, String name, String description) {
        Team team = Team.builder()
                .name(requireUniqueName(room, name, null))
                .description(cleanDescription(description))
                .labId(room.getId())
                .build();
        return teamRepository.save(team);
    }

    @Transactional
    public Team edit(Room room, String teamId, String name, String description) {
        Team team = requireLabTeam(room, teamId);
        if (name != null) {
            team.setName(requireUniqueName(room, name, teamId));
        }
        if (description != null) {
            team.setDescription(cleanDescription(description));
        }
        team.setUpdatedAt(LocalDateTime.now());
        return teamRepository.save(team);
    }

    /** Deletes a lab team once its copy is stopped. Its members play solo until put on another team. */
    @Transactional
    public Team delete(Room room, String teamId) {
        Team team = requireLabTeam(room, teamId);
        List<LabTracker> copies = labTrackerRepository.findLabTrackersByLabOwner(team);
        if (copies.stream().anyMatch(RoomService::deployed)) {
            throw new ResourceInUseException(team.getName() + "'s environment is still deployed. Stop it first.");
        }
        memberRepository.deleteAll(memberRepository.findByTeamId(team.getId()));
        labTrackerRepository.deleteAll(copies);
        teamRepository.delete(team);
        return team;
    }

    /** Puts users on the team, taking them off any other team in this lab. Returns who was added. */
    @Transactional
    public List<User> addMembers(Room room, String teamId, List<String> userIds) {
        Team team = requireLabTeam(room, teamId);
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("Pick at least one person to add");
        }
        List<User> users = new ArrayList<>();
        for (String userId : new LinkedHashSet<>(userIds)) {
            users.add(userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("One of the picked accounts no longer exists")));
        }
        List<User> added = new ArrayList<>();
        for (User user : users) {
            Optional<LabTeamMember> existing = memberRepository.findByRoomIdAndUserId(room.getId(), user.getId());
            if (existing.isPresent() && existing.get().getTeam().getId().equals(team.getId())) {
                continue;
            }
            LabTeamMember member = existing.orElseGet(() -> LabTeamMember.builder().roomId(room.getId()).user(user).build());
            member.setTeam(team);
            member.setUpdatedAt(LocalDateTime.now());
            memberRepository.save(member);
            added.add(user);
        }
        return added;
    }

    @Transactional
    public User removeMember(Room room, String teamId, String userId) {
        Team team = requireLabTeam(room, teamId);
        LabTeamMember member = memberRepository.findByRoomIdAndUserId(room.getId(), userId)
                .filter(found -> found.getTeam().getId().equals(team.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("That person is not on " + team.getName()));
        memberRepository.delete(member);
        return member.getUser();
    }

    /** What copying the site teams did. */
    public record CopyResult(int teamsCreated, int membersAdded, int alreadyOnATeam) {
    }

    /**
     * Seeds the lab's teams from the site teams: a lab team of the same name for each (made when
     * missing) with the site team's members. People already on a team in this lab stay where they are.
     */
    @Transactional
    public CopyResult copyFromSite(Room room) {
        Map<String, Team> byName = new HashMap<>();
        teamRepository.findByLabIdOrderByNameAsc(room.getId()).forEach(team -> byName.put(key(team.getName()), team));
        Map<String, List<User>> siteMembers = userRepository.findAll().stream().filter(user -> user.getTeam() != null)
                .collect(Collectors.groupingBy(user -> user.getTeam().getId()));
        int created = 0;
        int added = 0;
        int skipped = 0;
        for (Team site : teamRepository.findByLabIdIsNullOrderByNameAsc()) {
            Team team = byName.get(key(site.getName()));
            if (team == null) {
                team = teamRepository.save(Team.builder().name(site.getName()).description(site.getDescription())
                        .labId(room.getId()).build());
                byName.put(key(site.getName()), team);
                created++;
            }
            for (User user : siteMembers.getOrDefault(site.getId(), List.of())) {
                if (memberRepository.findByRoomIdAndUserId(room.getId(), user.getId()).isPresent()) {
                    skipped++;
                    continue;
                }
                memberRepository.save(LabTeamMember.builder().roomId(room.getId()).team(team).user(user).build());
                added++;
            }
        }
        return new CopyResult(created, added, skipped);
    }

    /** One of this lab's own teams. */
    public Team requireLabTeam(Room room, String teamId) {
        return teamRepository.findById(teamId).filter(team -> room.getId().equals(team.getLabId()))
                .orElseThrow(() -> new ResourceNotFoundException("No team " + teamId + " in " + room.getName()));
    }

    private LabTeamsView.TeamView teamView(Room room, Team team, List<User> members) {
        EnvironmentStatus status = null;
        if (room.effectiveMode() == EnvironmentMode.PER_TEAM) {
            status = EnvironmentStatuses.of(labTrackerService.findTeamCopy(room.getEnvironment(), team).orElse(null));
        }
        return new LabTeamsView.TeamView(team.getId(), team.getName(), team.getDescription(),
                members.stream().sorted(Comparator.comparing(User::getUsername, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                        .map(user -> new LabTeamsView.Member(user.getId(), user.getUsername(), user.getDisplayName())).toList(),
                status);
    }

    private String requireUniqueName(Room room, String name, String exceptId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Team name required");
        }
        if (trimmed.length() > MAX_NAME) {
            throw new IllegalArgumentException("Keep the team name under " + MAX_NAME + " characters");
        }
        boolean taken = teamRepository.findByLabIdOrderByNameAsc(room.getId()).stream()
                .anyMatch(other -> key(other.getName()).equals(key(trimmed)) && !other.getId().equals(exceptId));
        if (taken) {
            throw new IllegalArgumentException(room.getName() + " already has a team named " + trimmed);
        }
        return trimmed;
    }

    private static String cleanDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("Keep the description under " + MAX_DESCRIPTION + " characters");
        }
        return trimmed;
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
