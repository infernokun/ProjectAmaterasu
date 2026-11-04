package com.infernokun.amaterasu.config;

import com.infernokun.amaterasu.repositories.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

/**
 * Rejects access tokens of users that no longer exist, are disabled, or changed their password after
 * the token was issued. Without this a stolen token would keep working until it expires.
 */
public class ActiveUserTokenValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "Token is no longer valid", null);

    private final UserRepository userRepository;

    public ActiveUserTokenValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Instant issuedAt = jwt.getIssuedAt();
        boolean valid = jwt.getSubject() != null && userRepository.findById(jwt.getSubject())
                .filter(user -> user.isEnabled())
                .filter(user -> user.getSessionsValidAfter() == null
                        || (issuedAt != null && !issuedAt.isBefore(user.getSessionsValidAfter())))
                .isPresent();
        return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(REVOKED);
    }
}
