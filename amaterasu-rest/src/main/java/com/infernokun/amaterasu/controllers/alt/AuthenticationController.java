package com.infernokun.amaterasu.controllers.alt;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.entities.alt.ChangePasswordRequest;
import com.infernokun.amaterasu.services.alt.LoginAttemptService;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.alt.LoginResponse;
import com.infernokun.amaterasu.models.entities.alt.RefreshTokenRequest;
import com.infernokun.amaterasu.models.entities.alt.RegistrationRequest;
import com.infernokun.amaterasu.services.alt.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthenticationController extends BaseController {
    private final AuthenticationService authenticationService;
    private final LoginAttemptService loginAttemptService;
    private final CurrentUser currentUser;

    @PostMapping(value = "/register", consumes = "application/json")
    public ResponseEntity<ApiResponse<Boolean>> registerUser(@RequestBody RegistrationRequest registrationRequest,
                                                             HttpServletRequest request) {
        loginAttemptService.throttle("register", request.getRemoteAddr(), 50, Duration.ofMinutes(10));
        return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                .code(HttpStatus.OK.value())
                .message("User registered successfully.")
                .data(authenticationService.registerUser(registrationRequest))
                .build());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody RegistrationRequest credentials,
            HttpServletRequest request) {

        LoginResponse response = authenticationService.login(
                credentials.getUsername(),
                credentials.getPassword(),
                request);

        return ResponseEntity.ok(ApiResponse.<LoginResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Login successful")
                .data(response)
                .build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        loginAttemptService.throttle("refresh", httpRequest.getRemoteAddr(), 60, Duration.ofMinutes(1));

        LoginResponse response = authenticationService.refreshToken(
                request.getRefreshToken(), httpRequest);

        return ResponseEntity.ok(ApiResponse.<LoginResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Token refreshed successfully")
                .data(response)
                .build());
    }

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<LoginResponse>> changePassword(@RequestBody ChangePasswordRequest change,
                                                                     HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.<LoginResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Password changed; other sessions were signed out")
                .data(authenticationService.changePassword(currentUser.id(), change, request))
                .build());
    }

    @PostMapping("/refresh/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateRefreshToken(
            @RequestBody RefreshTokenRequest request) {

        boolean isValid = authenticationService.isRefreshTokenValid(request.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                .code(HttpStatus.OK.value())
                .message("Token validation complete")
                .data(isValid)
                .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(@RequestBody RefreshTokenRequest request) {
        authenticationService.logout(request.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .code(HttpStatus.OK.value())
                .message("Logout successful")
                .data("User logged out successfully")
                .build());
    }
}
