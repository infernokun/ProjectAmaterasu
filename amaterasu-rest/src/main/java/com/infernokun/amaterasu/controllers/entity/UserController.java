package com.infernokun.amaterasu.controllers.entity;

import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.dto.UserDTO;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.services.entity.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController extends BaseController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        List<User> users = userService.findAllUsers();
        return ResponseEntity.ok(ApiResponse.<List<User>>builder()
                .code(HttpStatus.OK.value())
                .message("Users retrieved successfully.")
                .data(users)
                .build());
    }

    @GetMapping("{id}")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<User>builder()
                .code(HttpStatus.OK.value())
                .message("Retrieved user!")
                .data(userService.findUserById(id))
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<User>> createUser(@RequestBody User user) {
        User createdUser = userService.createUser(user);
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

    @PutMapping("/team")
    public ResponseEntity<ApiResponse<User>> updateUserTeam(@RequestParam String userId, @RequestParam String teamId) {
        return ResponseEntity.ok(ApiResponse.<User>builder()
                .code(HttpStatus.OK.value())
                .message("User updated successfully.")
                .data(userService.updateUserTeam(userId, teamId))
                .build());
    }
}
