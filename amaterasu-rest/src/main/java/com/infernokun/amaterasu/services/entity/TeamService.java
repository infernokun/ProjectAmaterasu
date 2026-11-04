package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.exceptions.ResourceInUseException;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class TeamService extends BaseService {
    private static final Set<LabStatus> NOT_DEPLOYED = Set.of(LabStatus.STOPPED, LabStatus.DELETED, LabStatus.NONE);

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final LabTrackerRepository labTrackerRepository;

    public TeamService(TeamRepository teamRepository, UserRepository userRepository,
                       LabTrackerRepository labTrackerRepository) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.labTrackerRepository = labTrackerRepository;
    }

    public List<Team> findAllTeams() {
        return teamRepository.findAll();
    }

    public Team findTeamById(String id) {
        return this.teamRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Team not found!"));
    }

    public Team createTeam(Team team) {
        team.setName(requireUniqueName(team.getName(), null));
        return teamRepository.save(team);
    }

    public List<Team> createManyTeams(List<Team> teams) {
        return teamRepository.saveAll(teams);
    }

    /**
     * Deletes a team whose labs are all stopped. Members stay as accounts without a team, and the
     * team's stopped labs go with it.
     */
    public Team deleteTeam(String id) {
        Team team = findTeamById(id);
        List<LabTracker> trackers = labTrackerRepository.findLabTrackersByLabOwner(team);
        long deployed = trackers.stream().filter(TeamService::isDeployed).count();
        if (deployed > 0) {
            throw new ResourceInUseException(team.getName() + " still has " + deployed
                    + (deployed == 1 ? " lab" : " labs") + " deployed. Stop or remove them first.");
        }
        List<User> members = members(id);
        members.forEach(member -> member.setTeam(null));
        userRepository.saveAll(members);
        labTrackerRepository.deleteAll(trackers);
        teamRepository.delete(team);
        return team;
    }

    /** Internal saves, such as the lab lists a lab action keeps up to date. */
    public Team updateTeam(Team team) {
        findTeamById(team.getId());
        team.setUpdatedAt(LocalDateTime.now());
        return teamRepository.save(team);
    }

    /** Renames a team or changes its description, leaving its labs alone. */
    public Team editTeam(String id, String name, String description) {
        Team team = findTeamById(id);
        if (name != null) {
            team.setName(requireUniqueName(name, id));
        }
        if (description != null) {
            team.setDescription(description.trim());
        }
        team.setUpdatedAt(LocalDateTime.now());
        return teamRepository.save(team);
    }

    public List<User> members(String teamId) {
        return userRepository.findAll().stream()
                .filter(user -> user.getTeam() != null && teamId.equals(user.getTeam().getId()))
                .toList();
    }

    private String requireUniqueName(String name, String exceptId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Team name required");
        }
        boolean taken = teamRepository.findAll().stream()
                .anyMatch(other -> other.getName() != null && other.getName().equalsIgnoreCase(trimmed)
                        && !other.getId().equals(exceptId));
        if (taken) {
            throw new IllegalArgumentException("A team named " + trimmed + " already exists");
        }
        return trimmed;
    }

    private static boolean isDeployed(LabTracker tracker) {
        return tracker.getLabStatus() != null && !NOT_DEPLOYED.contains(tracker.getLabStatus());
    }
}
