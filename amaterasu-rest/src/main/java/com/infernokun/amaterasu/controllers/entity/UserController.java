package com.infernokun.amaterasu.controllers.entity;

import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
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
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String id) {
        boolean isDeleted = userService.deleteUser(id);

        if (isDeleted) {
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .code(HttpStatus.OK.value())
                    .message("User deleted successfully.")
                    .data(null) // No additional data for delete operation
                    .build());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<Void>builder()
                    .code(HttpStatus.NOT_FOUND.value())
                    .message("User not found or a conflict occurred.")
                    .data(null)
                    .build());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> updateUser(@PathVariable String id, @RequestBody User user) {
        User updatedUser = userService.updateUser(id, user);
        if (updatedUser != null) {
            return ResponseEntity.ok(ApiResponse.<User>builder()
                    .code(HttpStatus.OK.value())
                    .message("User updated successfully.")
                    .data(updatedUser)
                    .build());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<User>builder()
                    .code(HttpStatus.NOT_FOUND.value())
                    .message("User not found.")
                    .data(null)
                    .build());
        }
    }
}
