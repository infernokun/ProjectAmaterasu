package com.infernokun.amaterasu.controllers.entity;

import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.services.entity.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/team")
public class TeamController extends BaseController {
    private final TeamService teamService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Team>>> getAllTeams() {
        List<Team> teams = teamService.findAllTeams();
        return ResponseEntity.ok(ApiResponse.<List<Team>>builder()
                .code(HttpStatus.OK.value())
                .message("Teams retrieved successfully.")
                .data(teams)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Team>> getTeamById(@PathVariable String id) {
        Team foundTeam = teamService.findTeamById(id);
        return ResponseEntity.status( HttpStatus.OK)
                .body(ApiResponse.<Team>builder()
                        .code(HttpStatus.OK.value())
                        .message("Found a team.")
                        .data(foundTeam)
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
    public ResponseEntity<ApiResponse<Team>> deleteTeam(@PathVariable String id) {
        Team deletedTeam = teamService.deleteTeam(id);
        return ResponseEntity.ok(ApiResponse.<Team>builder()
                .code(HttpStatus.OK.value())
                .message("Team deleted successfully.")
                .data(deletedTeam)
                .build());
    }

    @PutMapping()
    public ResponseEntity<ApiResponse<Team>> updateTeam(@RequestBody Team team) {
        Team updatedTeam = teamService.updateTeam(team);
        return ResponseEntity.ok(ApiResponse.<Team>builder()
                .code(HttpStatus.OK.value())
                .message("Team updated successfully.")
                .data(updatedTeam)
                .build());
    }
}
