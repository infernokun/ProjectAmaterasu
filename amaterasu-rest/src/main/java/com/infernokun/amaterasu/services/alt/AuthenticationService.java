package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.exceptions.AuthFailedException;
import com.infernokun.amaterasu.exceptions.WrongPasswordException;
import com.infernokun.amaterasu.models.entities.alt.LoginResponse;
import com.infernokun.amaterasu.models.entities.alt.RegistrationRequest;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.entity.RefreshTokenService;
import com.infernokun.amaterasu.services.entity.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;


@Service
@RequiredArgsConstructor
public class AuthenticationService extends BaseService {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{3,32}");
    private static final int MIN_PASSWORD_LENGTH = 8;
    // bcrypt only uses the first 72 bytes
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;
    private final LoginAttemptService loginAttemptService;

    public boolean registerUser(RegistrationRequest request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            throw new IllegalArgumentException("Username and password required");
        }
        String username = request.getUsername().trim();
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("Username must be 3 to 32 letters, numbers, dots, dashes or underscores");
        }
        int passwordLength = request.getPassword().getBytes(StandardCharsets.UTF_8).length;
        if (passwordLength < MIN_PASSWORD_LENGTH || passwordLength > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be " + MIN_PASSWORD_LENGTH + " to "
                    + MAX_PASSWORD_LENGTH + " characters");
        }
        if (userService.existsByUsernameIgnoreCase(username)) {
            throw new AuthFailedException("Username already exists!");
        }

        // Self-registration always creates a plain member; roles are granted by an admin
        User newUser = new User(username, passwordEncoder.encode(request.getPassword()));
        newUser.setRole(Role.MEMBER);
        userRepository.save(newUser);

        LOGGER.info("User registered: {}", newUser.getUsername());
        return true;
    }

    public LoginResponse login(String username, String password, HttpServletRequest request) {
        String address = request.getRemoteAddr();
        loginAttemptService.checkAllowed(username, address);
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            User user = (User) auth.getPrincipal();
            loginAttemptService.recordSuccess(username);

            RefreshTokenService.Tokens tokens = refreshTokenService.issue(user, request);
            return new LoginResponse(tokens.accessToken(), user, tokens.refreshToken());
        } catch (AuthenticationException e) {
            // Same answer for an unknown user and a wrong password
            loginAttemptService.recordFailure(username, address);
            LOGGER.warn("Login failed for username {} from {}", username, address);
            throw new WrongPasswordException("Invalid username or password");
        }
    }

    public LoginResponse refreshToken(String refreshToken, HttpServletRequest request) {
        RefreshTokenService.Tokens tokens = refreshTokenService.refresh(refreshToken, request);
        return new LoginResponse(tokens.accessToken(), tokens.user(), tokens.refreshToken());
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeSession(refreshToken, "User logout");
        }
    }

    public boolean isRefreshTokenValid(String refreshToken) {
        return refreshToken != null && !refreshToken.isBlank() && refreshTokenService.isValid(refreshToken);
    }
}
