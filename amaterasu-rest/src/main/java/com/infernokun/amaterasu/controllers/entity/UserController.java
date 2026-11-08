package com.infernokun.amaterasu.controllers.entity;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.services.alt.AuthenticationService;
import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.alt.CreateUserRequest;
import com.infernokun.amaterasu.models.entities.alt.UserImport;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.services.entity.AuditService;
import com.infernokun.amaterasu.services.entity.TeamService;
import com.infernokun.amaterasu.services.entity.UserImportService;
import com.infernokun.amaterasu.services.entity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController extends BaseController {
    private final UserService userService;
    private final AuthenticationService authenticationService;
    private final CurrentUser currentUser;
    private final TeamService teamService;
    private final AuditService auditService;
    private final UserImportService userImportService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        List<User> users = userService.findAllUsers();
        return ResponseEntity.ok(ApiResponse.<List<User>>builder()
                .code(HttpStatus.OK.value())
                .message("Users retrieved successfully.")
                .data(users)
                .build());
    }

    @GetMapping("/by")
    public ResponseEntity<ApiResponse<User>> getUserBy(@RequestParam Map<String, String> params) {
        User user = null;
        String message = "User retrieved successfully.";

        if (params.containsKey("username")) {
            String username = params.get("username");
            user = this.userService.findUserByUsername(username);
            currentUser.requireSelfOrAdmin(user.getId());
            message = String.format("User with username '%s' retrieved successfully.", username);

        } else if (params.containsKey("id")) {
            String userId = params.get("id");
            currentUser.requireSelfOrAdmin(userId);
            user = this.userService.findUserById(userId);
            message = String.format("User with ID '%s' retrieved successfully.", userId);

        } else {
            throw new IllegalArgumentException("At least one search parameter is required: username, or id");
        }

        return ResponseEntity.ok(
                ApiResponse.<User>builder()
                        .code(HttpStatus.OK.value())
                        .message(message)
                        .data(user)
                        .build()
        );
    }

    @GetMapping("{id}")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable String id) {
        currentUser.requireSelfOrAdmin(id);
        return ResponseEntity.ok(ApiResponse.<User>builder()
                .code(HttpStatus.OK.value())
                .message("Retrieved user!")
                .data(userService.findUserById(id))
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<User>> createUser(@RequestBody CreateUserRequest request) {
        Team team = request.getTeamId() == null || request.getTeamId().isBlank()
                ? null : teamService.findTeamById(request.getTeamId());
        User createdUser = authenticationService.createUser(request.getUsername(), request.getPassword(),
                request.getRole(), team);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<User>builder()
                .code(HttpStatus.CREATED.value())
                .message("User created successfully.")
                .data(createdUser)
                .build());
    }

    @PostMapping("/many")
    public ResponseEntity<ApiResponse<List<User>>> createManyUsers(@RequestBody List<User> users) {
        List<User> createdUsers = userService.createManyUsers(users);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<List<User>>builder()
                .code(HttpStatus.CREATED.value())
                .message("Users created successfully.")
                .data(createdUsers)
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> deleteUser(@PathVariable String id) {
        User deletedUser = userService.deleteUser(id);

        return ResponseEntity.ok(ApiResponse.<User>builder()
                .code(HttpStatus.OK.value())
                .message("User deleted successfully.")
                .data(deletedUser)
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> updateUser(@PathVariable String id, @RequestBody User user) {
        User updatedUser = userService.updateUser(id, user);

        return ResponseEntity.ok(ApiResponse.<User>builder()
                .code(HttpStatus.OK.value())
                .message("User updated successfully.")
                .data(updatedUser)
                .build());

    }

    @PutMapping("/{id}/password")
    public ResponseEntity<ApiResponse<User>> resetPassword(@PathVariable String id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.<User>builder()
                .code(HttpStatus.OK.value())
                .message("Password reset; the user's sessions were signed out.")
                .data(authenticationService.resetPassword(id, body.get("password")))
                .build());
    }

    @PutMapping("/{id}/disabled")
    public ResponseEntity<ApiResponse<User>> setDisabled(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        boolean disabled = Boolean.TRUE.equals(body.get("disabled"));
        return ResponseEntity.ok(ApiResponse.<User>builder()
                .code(HttpStatus.OK.value())
                .message(disabled ? "User disabled." : "User enabled.")
                .data(authenticationService.setDisabled(id, disabled, currentUser.id()))
                .build());
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<ApiResponse<User>> changeRole(@PathVariable String id, @RequestBody Map<String, Role> body) {
        return ResponseEntity.ok(ApiResponse.<User>builder()
                .code(HttpStatus.OK.value())
                .message("Role changed.")
                .data(authenticationService.changeRole(id, body.get("role"), currentUser.id()))
                .build());
    }

    /** Creates accounts from rows of a CSV file; bad rows are skipped and reported with the reason. */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<UserImport.Result>> importUsers(@RequestBody UserImport.Request request) {
        UserImport.Result result = userImportService.importUsers(request == null ? null : request.users());
        return ResponseEntity.ok(ApiResponse.<UserImport.Result>builder()
                .code(HttpStatus.OK.value())
                .message(result.created().size() + " created, " + result.skipped().size() + " skipped.")
                .data(result)
                .build());
    }

    /** Moves several users to one team, or off their team when teamId is left out, in one transaction. */
    @PutMapping("/team/bulk")
    public ResponseEntity<ApiResponse<List<User>>> moveUsers(@RequestBody UserImport.BulkMove request) {
        String teamId = request == null || request.teamId() == null || request.teamId().isBlank() ? null : request.teamId();
        List<User> users = userService.updateUsersTeam(request == null ? null : request.userIds(), teamId);
        Team team = teamId == null ? null : teamService.findTeamById(teamId);
        for (User user : users) {
            auditService.record("USER_TEAM_CHANGED", "USER", user.getId(), user.getUsername(), teamId,
                    team != null ? "Moved to " + team.getName() : "Removed from team");
        }
        return ResponseEntity.ok(ApiResponse.<List<User>>builder()
                .code(HttpStatus.OK.value())
                .message(users.size() + (users.size() == 1 ? " user moved." : " users moved."))
                .data(users)
                .build());
    }

    /** Moves a user to a team; leave teamId out to take them off their team. */
    @PutMapping("/team")
    public ResponseEntity<ApiResponse<User>> updateUserTeam(@RequestParam String userId,
                                                            @RequestParam(required = false) String teamId) {
        User user = userService.updateUserTeam(userId, teamId == null || teamId.isBlank() ? null : teamId);
        auditService.record("USER_TEAM_CHANGED", "USER", user.getId(), user.getUsername(),
                user.getTeam() != null ? user.getTeam().getId() : null,
                user.getTeam() != null ? "Moved to " + user.getTeam().getName() : "Removed from team");
        return ResponseEntity.ok(ApiResponse.<User>builder()
                .code(HttpStatus.OK.value())
                .message("User updated successfully.")
                .data(user)
                .build());
    }
}
