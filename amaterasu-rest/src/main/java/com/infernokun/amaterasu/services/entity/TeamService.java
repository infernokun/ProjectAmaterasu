package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TeamService extends BaseService {
    private final TeamRepository teamRepository;

    @Autowired
    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Cacheable(value = "teams", key = "'all'")
    public List<Team> findAllTeams() {
        return teamRepository.findAll();
    }

    @Cacheable(value = "teams", key = "#id")
    public Team findTeamById(String id) {
        return this.teamRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Team not found!"));
    }

    @CacheEvict(value = "teams", allEntries = true)
    public Team createTeam(Team team) {
        return teamRepository.save(team);
    }

    @CacheEvict(value = "teams", allEntries = true)
    public List<Team> createManyTeams(List<Team> teams) {
        return teamRepository.saveAll(teams);
    }

    @CacheEvict(value = "teams", key = "#id")
    public Team deleteTeam(String id) {
        Team deletedTeam = findTeamById(id);
        teamRepository.deleteById(deletedTeam.getId());
        return deletedTeam;
    }

    @CacheEvict(value = "teams", key = "#team.id")
    public Team updateTeam(Team team) {
        findTeamById(team.getId());
        team.setUpdatedAt(LocalDateTime.now());
        return teamRepository.save(team);
    }
}
