package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.exceptions.AuthFailedException;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.exceptions.WrongPasswordException;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.alt.ChangePasswordRequest;
import com.infernokun.amaterasu.models.entities.alt.RegistrationRequest;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.entity.AuditService;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    private final AuditService auditService;

    public boolean registerUser(RegistrationRequest request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            throw new IllegalArgumentException("Username and password required");
        }
        String username = request.getUsername().trim();
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("Username must be 3 to 32 letters, numbers, dots, dashes or underscores");
        }
        validatePassword(request.getPassword());
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

    public RefreshTokenService.Tokens login(String username, String password, HttpServletRequest request) {
        String address = request.getRemoteAddr();
        loginAttemptService.checkAllowed(username, address);
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            User user = (User) auth.getPrincipal();
            loginAttemptService.recordSuccess(username);
            user.setLastSignIn(Instant.now());
            user = userRepository.save(user);
            auditService.recordAs(user.getId(), user.getUsername(), "SIGN_IN", "USER", user.getId(),
                    user.getUsername(), teamIdOf(user), "From " + address);

            return refreshTokenService.issue(user, request);
        } catch (AuthenticationException e) {
            // Same answer for an unknown user and a wrong password
            loginAttemptService.recordFailure(username, address);
            LOGGER.warn("Login failed for username {} from {}", username, address);
            User known = username == null ? null : userService.findByUsernameIgnoreCase(username).orElse(null);
            auditService.recordAs(known != null ? known.getId() : null, username, "SIGN_IN_FAILED", "USER",
                    known != null ? known.getId() : null, username, known != null ? teamIdOf(known) : null,
                    "From " + address);
            throw new WrongPasswordException("Invalid username or password");
        }
    }

    /** An account made by an admin, with a role and optionally a team. */
    public User createUser(String username, String password, Role role, Team team) {
        String name = username == null ? "" : username.trim();
        if (!USERNAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Username must be 3 to 32 letters, numbers, dots, dashes or underscores");
        }
        validatePassword(password);
        if (userService.existsByUsernameIgnoreCase(name)) {
            throw new IllegalArgumentException("Username " + name + " is taken");
        }
        User user = new User(name, passwordEncoder.encode(password));
        user.setRole(role == null ? Role.MEMBER : role);
        user.setTeam(team);
        User saved = userRepository.save(user);
        auditService.record("USER_CREATED", "USER", saved.getId(), saved.getUsername(), teamIdOf(saved),
                "Role " + saved.getRole().name());
        return saved;
    }

    /**
     * Changes a user's role. Their access tokens stop working at once, so the next request refreshes
     * and picks up the new role; their sessions stay signed in.
     */
    public User changeRole(String userId, Role role, String actingUserId) {
        if (role == null) {
            throw new IllegalArgumentException("Role required");
        }
        if (userId.equals(actingUserId)) {
            throw new IllegalArgumentException("You cannot change your own role");
        }
        User user = userService.findUserById(userId);
        Role previous = user.getRole();
        if (previous == role) {
            return user;
        }
        user.setRole(role);
        user.setSessionsValidAfter(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        User saved = userRepository.save(user);
        auditService.record("USER_ROLE_CHANGED", "USER", saved.getId(), saved.getUsername(), teamIdOf(saved),
                (previous == null ? "None" : previous.name()) + " to " + role.name());
        return saved;
    }

    /** Changes the caller's password and signs out every other session, keeping this one signed in. */
    public RefreshTokenService.Tokens changePassword(String userId, ChangePasswordRequest change, HttpServletRequest request) {
        User user = userService.findUserById(userId);
        if (change == null || change.getCurrentPassword() == null
                || !passwordEncoder.matches(change.getCurrentPassword(), user.getPassword())) {
            throw new WrongPasswordException("Current password is incorrect");
        }
        validatePassword(change.getNewPassword());
        user.setPassword(passwordEncoder.encode(change.getNewPassword()));
        invalidateSessions(user);
        auditService.record("PASSWORD_CHANGED", "USER", user.getId(), user.getUsername(), teamIdOf(user),
                "Other sessions signed out");
        return refreshTokenService.issue(user, request);
    }

    public User resetPassword(String userId, String newPassword) {
        User user = userService.findUserById(userId);
        validatePassword(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        User saved = invalidateSessions(user);
        auditService.record("PASSWORD_RESET", "USER", saved.getId(), saved.getUsername(), teamIdOf(saved),
                "Signed out everywhere");
        return saved;
    }

    public User setDisabled(String userId, boolean disabled, String actingUserId) {
        if (userId.equals(actingUserId)) {
            throw new IllegalArgumentException("You cannot disable your own account");
        }
        User user = userService.findUserById(userId);
        user.setDisabled(disabled);
        User saved = disabled ? invalidateSessions(user) : userRepository.save(user);
        auditService.record(disabled ? "USER_DISABLED" : "USER_ENABLED", "USER", saved.getId(),
                saved.getUsername(), teamIdOf(saved), null);
        return saved;
    }

    /** The caller's signed in sessions, marking the one this request's refresh cookie belongs to. */
    public List<RefreshTokenService.SessionInfo> sessions(String userId, String refreshCookie) {
        return refreshTokenService.sessions(userId, refreshCookie);
    }

    /**
     * Signs out one of the caller's other sessions. Access tokens issued before now stop working, so the
     * caller gets a fresh one for this browser.
     */
    public RefreshTokenService.Tokens revokeSession(String userId, String sessionId, String refreshCookie) {
        if (sessionId.equals(refreshTokenService.sessionIdOf(refreshCookie))) {
            throw new IllegalArgumentException("Use Sign out to end the session in this browser");
        }
        User user = userService.findUserById(userId);
        if (!refreshTokenService.revokeUserSession(userId, sessionId, "Signed out from another device")) {
            throw new ResourceNotFoundException("Session not found");
        }
        auditService.record("SESSION_REVOKED", "SESSION", sessionId, user.getUsername(), teamIdOf(user), null);
        return refreshAccessOnly(user);
    }

    /** Signs out every session of the caller except the one in this browser. */
    public RefreshTokenService.Tokens revokeOtherSessions(String userId, String refreshCookie) {
        String current = refreshTokenService.sessionIdOf(refreshCookie);
        if (current == null) {
            throw new IllegalArgumentException("This browser has no session to keep");
        }
        User user = userService.findUserById(userId);
        int revoked = refreshTokenService.revokeOtherSessions(userId, current, "Signed out from another device");
        auditService.record("SESSION_REVOKED", "SESSION", null, user.getUsername(), teamIdOf(user),
                revoked + " other sessions signed out");
        return refreshAccessOnly(user);
    }

    private RefreshTokenService.Tokens refreshAccessOnly(User user) {
        user.setSessionsValidAfter(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        User saved = userRepository.save(user);
        return new RefreshTokenService.Tokens(refreshTokenService.generateAccessToken(saved), null, saved);
    }

    private static String teamIdOf(User user) {
        return user.getTeam() != null ? user.getTeam().getId() : null;
    }

    // Revokes refresh tokens and every access token issued before now
    private User invalidateSessions(User user) {
        user.setSessionsValidAfter(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        User saved = userRepository.save(user);
        refreshTokenService.revokeAllUserSessions(user.getId(), "Password changed or account disabled");
        return saved;
    }

    private static void validatePassword(String password) {
        int length = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_PASSWORD_LENGTH || length > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be " + MIN_PASSWORD_LENGTH + " to "
                    + MAX_PASSWORD_LENGTH + " characters");
        }
    }

    public RefreshTokenService.Tokens refreshToken(String refreshToken, HttpServletRequest request) {
        return refreshTokenService.refresh(refreshToken, request);
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
