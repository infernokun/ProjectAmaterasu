package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.exceptions.TokenException;
import com.infernokun.amaterasu.models.entities.RefreshToken;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.repositories.RefreshTokenRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class RefreshTokenService extends BaseService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    public RefreshToken createRefreshToken(String username, String token, Instant expiration) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Find existing token
        Optional<RefreshToken> existingTokenOpt = findByUserId(user.getId());

        if (existingTokenOpt.isPresent()) {
            RefreshToken existingToken = existingTokenOpt.get();
            existingToken.setToken(token);
            existingToken.setCreationDate(Instant.now());
            existingToken.setExpirationDate(expiration);
            return refreshTokenRepository.save(existingToken);
        }

        // If no existing token, create a new one
        RefreshToken newToken = RefreshToken.builder()
                .user(user)
                .token(token)
                .creationDate(Instant.now())
                .expirationDate(expiration)
                .build();

        return refreshTokenRepository.save(newToken);
    }

    public Optional<RefreshToken> findByUserId(String id) {
        return this.refreshTokenRepository.findByUserId(id);
    }

    public RefreshToken findByToken(String token) {
        return this.refreshTokenRepository.findByToken(token).orElseThrow(
                () -> new TokenException("Failed to find token."));
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpirationDate().compareTo(Instant.now()) < 0) {
            this.refreshTokenRepository.delete(token);
            throw new RuntimeException(token.getToken() + " Refresh token is expired. Please make a new login..!");
        }
        return token;
    }

    @Transactional
    public Optional<RefreshToken> deleteToken(String id) {
        Optional<User> user = userRepository.findById(id);
        if (user.isPresent()) {
            LOGGER.info("LOGOUT COMPLETE");
            return refreshTokenRepository.deleteByUserId(id);
        } else {
            return Optional.empty();
        }
    }
}

