package com.infernokun.amaterasu.controllers;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.Team; // Assuming you have a Team entity
import com.infernokun.amaterasu.services.TeamService; // Assuming you have a TeamService
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/team")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Team>>> getAllTeams() {
        List<Team> teams = teamService.findAllTeams();
        return ResponseEntity.ok(ApiResponse.<List<Team>>builder()
                .code(HttpStatus.OK.value())
                .message("Teams retrieved successfully.")
                .data(teams)
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Team>> createTeam(@RequestBody Team team) {
        Team createdTeam = teamService.createTeam(team);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Team>builder()
                .code(HttpStatus.CREATED.value())
                .message("Team created successfully.")
                .data(createdTeam)
                .build());
    }

    @PostMapping("/many")
    public ResponseEntity<ApiResponse<List<Team>>> createManyTeams(@RequestBody List<Team> teams) {
        List<Team> createdTeams = teamService.createManyTeams(teams);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<List<Team>>builder()
                .code(HttpStatus.CREATED.value())
                .message("Teams created successfully.")
                .data(createdTeams)
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(@PathVariable String id) {
        boolean isDeleted = teamService.deleteTeam(id);

        if (isDeleted) {
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .code(HttpStatus.OK.value())
                    .message("Team deleted successfully.")
                    .data(null) // No additional data for delete operation
                    .build());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<Void>builder()
                    .code(HttpStatus.NOT_FOUND.value())
                    .message("Team not found or a conflict occurred.")
                    .data(null)
                    .build());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Team>> updateTeam(@PathVariable String id, @RequestBody Team team) {
        Team updatedTeam = teamService.updateTeam(id, team);
        if (updatedTeam != null) {
            return ResponseEntity.ok(ApiResponse.<Team>builder()
                    .code(HttpStatus.OK.value())
                    .message("Team updated successfully.")
                    .data(updatedTeam)
                    .build());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<Team>builder()
                    .code(HttpStatus.NOT_FOUND.value())
                    .message("Team not found.")
                    .data(null)
                    .build());
        }
    }
}
