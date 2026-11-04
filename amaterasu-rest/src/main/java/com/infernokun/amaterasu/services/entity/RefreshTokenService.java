package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.config.SecurityConfig;
import com.infernokun.amaterasu.exceptions.TokenException;
import com.infernokun.amaterasu.models.entities.RefreshToken;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.repositories.RefreshTokenRepository;
import com.infernokun.amaterasu.services.BaseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Opaque refresh tokens, stored as SHA-256 hashes and rotated on every use. Presenting a token that
 * was already rotated means it leaked, so every session of that user is revoked.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService extends BaseService {
    public static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
    static final String ROTATED = "Rotated";
    // Two tabs refreshing at once both present the same token; do not treat that as theft
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(30);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtEncoder jwtEncoder;

    /** refreshToken is null when the caller already holds the current one. */
    public record Tokens(String accessToken, String refreshToken, User user) {
    }

    public Tokens issue(User user, HttpServletRequest request) {
        cleanupExpiredTokensForUser(user.getId());
        RefreshToken refreshToken = createRefreshToken(user, request, UUID.randomUUID().toString(),
                Instant.now().plus(RefreshToken.MAX_LIFETIME));
        return new Tokens(generateAccessToken(user), refreshToken.getRawToken(), user);
    }

    // Commits on its own: a detected replay must revoke the sessions even though the request then fails
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = TokenException.class)
    public Tokens refresh(String rawToken, HttpServletRequest request) {
        RefreshToken current = findByToken(rawToken);

        if (current.isRevoked()) {
            boolean rotated = ROTATED.equals(current.getRevokedReason());
            boolean withinGrace = current.getRevokedAt() != null
                    && current.getRevokedAt().plus(ROTATION_GRACE).isAfter(Instant.now());
            if (rotated && withinGrace) {
                // Two tabs refreshed with the same cookie at once; the other one already rotated it
                RefreshToken successor = activeTokenForSession(current);
                if (successor != null && current.getUser().isEnabled()) {
                    return new Tokens(generateAccessToken(current.getUser()), null, current.getUser());
                }
            } else if (rotated) {
                LOGGER.warn("Refresh token reuse detected for user {}; revoking all sessions", current.getUser().getId());
                revokeAllUserSessions(current.getUser().getId(), "Refresh token reuse");
            }
            throw new TokenException("Refresh token expired or revoked");
        }
        if (!current.getUser().isEnabled()) {
            current.revoke("Account disabled");
            refreshTokenRepository.save(current);
            throw new TokenException("Refresh token expired or revoked");
        }
        if (current.isExpired()) {
            current.revoke("Expired");
            refreshTokenRepository.save(current);
            throw new TokenException("Refresh token expired or revoked");
        }

        current.revoke(ROTATED);
        refreshTokenRepository.save(current);

        // Same session and the same absolute expiry, so rotation never extends a session past its lifetime
        RefreshToken next = createRefreshToken(current.getUser(), request, current.getSessionId(),
                current.getExpirationDate());
        return new Tokens(generateAccessToken(current.getUser()), next.getRawToken(), current.getUser());
    }

    private RefreshToken activeTokenForSession(RefreshToken token) {
        return refreshTokenRepository.findAllByUserId(token.getUser().getId()).stream()
                .filter(other -> token.getSessionId().equals(other.getSessionId()))
                .filter(other -> !other.isRevoked() && !other.isExpired())
                .findFirst()
                .orElse(null);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.TOKEN_ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_LIFETIME))
                .subject(user.getId())
                .claim("username", user.getUsername())
                .claim("roles", user.getRole() != null ? user.getRole().name() : "")
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public boolean isValid(String rawToken) {
        return refreshTokenRepository.findByToken(hash(rawToken))
                .map(token -> !token.isRevoked() && !token.isExpired())
                .orElse(false);
    }

    @Transactional
    public void revokeSession(String rawToken, String reason) {
        refreshTokenRepository.findByToken(hash(rawToken)).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke(reason);
                refreshTokenRepository.save(token);
            }
        });
    }

    public List<RefreshToken> getActiveSessionsForUser(String userId) {
        return refreshTokenRepository.findAllByUserId(userId).stream()
                .filter(token -> !token.isRevoked() && !token.isExpired())
                .toList();
    }

    /** One signed in browser or device. Rotation keeps the session id, so this groups a session's tokens. */
    public record SessionInfo(String id, String userAgent, String ipAddress, Instant signedInAt, Instant lastActive,
                              Instant expiresAt, boolean current) {
    }

    public List<SessionInfo> sessions(String userId, String rawCookie) {
        String currentSession = sessionIdOf(rawCookie);
        Map<String, List<RefreshToken>> bySession = refreshTokenRepository.findAllByUserId(userId).stream()
                .filter(token -> token.getSessionId() != null)
                .collect(Collectors.groupingBy(RefreshToken::getSessionId));
        return bySession.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .filter(token -> !token.isRevoked() && !token.isExpired())
                        .findFirst()
                        .map(active -> new SessionInfo(entry.getKey(), active.getUserAgent(), active.getIpAddress(),
                                entry.getValue().stream().map(RefreshToken::getCreationDate)
                                        .filter(Objects::nonNull).min(Comparator.naturalOrder())
                                        .orElse(active.getCreationDate()),
                                active.getLastUsed(), active.getExpirationDate(),
                                entry.getKey().equals(currentSession)))
                        .stream())
                .sorted(Comparator.comparing(SessionInfo::current).reversed()
                        .thenComparing(SessionInfo::lastActive, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** The session a refresh cookie belongs to, or null. */
    public String sessionIdOf(String rawCookie) {
        if (rawCookie == null || rawCookie.isBlank()) {
            return null;
        }
        return refreshTokenRepository.findByToken(hash(rawCookie)).map(RefreshToken::getSessionId).orElse(null);
    }

    /** Revokes one session of the user. False when the user has no active session with that id. */
    @Transactional
    public boolean revokeUserSession(String userId, String sessionId, String reason) {
        List<RefreshToken> active = refreshTokenRepository.findAllByUserId(userId).stream()
                .filter(token -> sessionId.equals(token.getSessionId()) && !token.isRevoked())
                .toList();
        active.forEach(token -> token.revoke(reason));
        refreshTokenRepository.saveAll(active);
        return !active.isEmpty();
    }

    /** Revokes every session of the user except one; returns how many sessions ended. */
    @Transactional
    public int revokeOtherSessions(String userId, String keepSessionId, String reason) {
        List<RefreshToken> others = refreshTokenRepository.findAllByUserId(userId).stream()
                .filter(token -> !keepSessionId.equals(token.getSessionId()) && !token.isRevoked())
                .toList();
        others.forEach(token -> token.revoke(reason));
        refreshTokenRepository.saveAll(others);
        return (int) others.stream().map(RefreshToken::getSessionId).distinct().count();
    }

    @Transactional
    public void revokeAllUserSessions(String userId, String reason) {
        List<RefreshToken> userTokens = refreshTokenRepository.findAllByUserId(userId);
        userTokens.stream().filter(token -> !token.isRevoked()).forEach(token -> token.revoke(reason));
        refreshTokenRepository.saveAll(userTokens);
    }

    private RefreshToken findByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new TokenException("Refresh token required");
        }
        return refreshTokenRepository.findByToken(hash(rawToken))
                .orElseThrow(() -> new TokenException("Invalid refresh token"));
    }

    private RefreshToken createRefreshToken(User user, HttpServletRequest request, String sessionId, Instant expiresAt) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(hash(rawToken))
                .user(user)
                .sessionId(sessionId)
                .deviceInfo(request.getHeader("User-Agent"))
                .creationDate(now)
                .expirationDate(expiresAt)
                .lastUsed(now)
                // Tomcat resolves the client address from X-Forwarded-For only for trusted proxies
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .revoked(false)
                .build();

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        saved.setRawToken(rawToken);
        return saved;
    }

    private void cleanupExpiredTokensForUser(String userId) {
        List<RefreshToken> expired = refreshTokenRepository.findAllByUserId(userId).stream()
                .filter(token -> token.isExpired() && !token.isRevoked())
                .toList();
        expired.forEach(token -> token.revoke("Expired during cleanup"));
        if (!expired.isEmpty()) {
            refreshTokenRepository.saveAll(expired);
        }
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
