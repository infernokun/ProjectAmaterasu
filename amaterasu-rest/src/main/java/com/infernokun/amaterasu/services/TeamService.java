package com.infernokun.amaterasu.services;

import com.infernokun.amaterasu.models.entities.Team; // Assuming you have a Team entity
import com.infernokun.amaterasu.repositories.TeamRepository; // Assuming you have a TeamRepository
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TeamService {

    private final TeamRepository teamRepository;

    @Autowired
    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    // Retrieve all teams
    public List<Team> findAllTeams() {
        return teamRepository.findAll();
    }

    // Create a new team
    public Team createTeam(Team team) {
        return teamRepository.save(team);
    }

    // Create multiple teams
    public List<Team> createManyTeams(List<Team> teams) {
        return teamRepository.saveAll(teams);
    }

    // Delete a team by ID
    public boolean deleteTeam(String id) {
        try {
            teamRepository.deleteById(id);
            return true; // Deletion successful
        } catch (Exception e) {
            return false; // Deletion failed (e.g., team not found)
        }
    }

    // Update an existing team
    public Team updateTeam(String id, Team team) {
        Optional<Team> existingTeamOpt = teamRepository.findById(id);
        if (existingTeamOpt.isPresent()) {
            Team existingTeam = existingTeamOpt.get();
            // Update fields as necessary
            existingTeam.setName(team.getName()); // Assuming Team has a name field
            // Add other fields to update as needed
            return teamRepository.save(existingTeam); // Save the updated team
        }
        return null; // Team not found
    }
}
