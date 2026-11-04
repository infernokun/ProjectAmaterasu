package com.infernokun.amaterasu.controllers.alt;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.components.RefreshCookie;
import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.exceptions.TokenException;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.alt.ChangePasswordRequest;
import com.infernokun.amaterasu.models.entities.alt.LoginResponse;
import com.infernokun.amaterasu.models.entities.alt.RegistrationRequest;
import com.infernokun.amaterasu.services.alt.AuthenticationService;
import com.infernokun.amaterasu.services.alt.LoginAttemptService;
import com.infernokun.amaterasu.services.entity.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final RefreshCookie refreshCookie;

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
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody RegistrationRequest credentials,
                                                            HttpServletRequest request, HttpServletResponse response) {
        RefreshTokenService.Tokens tokens = authenticationService.login(
                credentials.getUsername(), credentials.getPassword(), request);
        return respond(tokens, "Login successful", request, response);
    }

    // Every page load in the web app refreshes once, so the limit allows for a classroom behind one address
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(HttpServletRequest request,
                                                                   HttpServletResponse response) {
        loginAttemptService.throttle("refresh", request.getRemoteAddr(), 300, Duration.ofMinutes(1));
        try {
            RefreshTokenService.Tokens tokens = authenticationService.refreshToken(refreshCookie.read(request), request);
            return respond(tokens, "Token refreshed successfully", request, response);
        } catch (TokenException e) {
            refreshCookie.clear(request, response);
            throw e;
        }
    }

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<LoginResponse>> changePassword(@RequestBody ChangePasswordRequest change,
                                                                     HttpServletRequest request,
                                                                     HttpServletResponse response) {
        RefreshTokenService.Tokens tokens = authenticationService.changePassword(currentUser.id(), change, request);
        return respond(tokens, "Password changed; other sessions were signed out", request, response);
    }

    @PostMapping("/refresh/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateRefreshToken(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                .code(HttpStatus.OK.value())
                .message("Token validation complete")
                .data(authenticationService.isRefreshTokenValid(refreshCookie.read(request)))
                .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(HttpServletRequest request, HttpServletResponse response) {
        authenticationService.logout(refreshCookie.read(request));
        refreshCookie.clear(request, response);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .code(HttpStatus.OK.value())
                .message("Logout successful")
                .data("User logged out successfully")
                .build());
    }

    private ResponseEntity<ApiResponse<LoginResponse>> respond(RefreshTokenService.Tokens tokens, String message,
                                                               HttpServletRequest request,
                                                               HttpServletResponse response) {
        // No new refresh token when a concurrent refresh already rotated it; the browser has the newer cookie
        if (tokens.refreshToken() != null) {
            refreshCookie.write(tokens.refreshToken(), request, response);
        }
        return ResponseEntity.ok(ApiResponse.<LoginResponse>builder()
                .code(HttpStatus.OK.value())
                .message(message)
                .data(new LoginResponse(tokens.accessToken(), tokens.user()))
                .build());
    }
}
