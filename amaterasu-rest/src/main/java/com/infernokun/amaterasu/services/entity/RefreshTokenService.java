package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.models.entities.RefreshToken;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.repositories.RefreshTokenRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.services.BaseService;
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
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isPresent()) {
            // Check if a refresh token already exists for the user
            Optional<RefreshToken> existingToken = findByUserId(user.get().getId());
            if (existingToken.isPresent()) {
                // Update the existing refresh token
                existingToken.get().setToken(token);
                existingToken.get().setCreationDate(Instant.now());
                existingToken.get().setExpirationDate(expiration);
                return this.refreshTokenRepository.save(existingToken.get());
            } else {
                // Create a new refresh token
                RefreshToken refreshToken = RefreshToken.builder()
                        .user(user.get())
                        .token(token)
                        .creationDate(Instant.now())
                        .expirationDate(expiration)
                        .build();
                return this.refreshTokenRepository.save(refreshToken);
            }
        }
        return null;
    }

    public Optional<RefreshToken> findByUserId(String id) {
        return this.refreshTokenRepository.findByUserId(id);
    }

    public Optional<RefreshToken> findByToken(String token) {
        return this.refreshTokenRepository.findByToken(token);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpirationDate().compareTo(Instant.now()) < 0) {
            this.refreshTokenRepository.delete(token);
            throw new RuntimeException(token.getToken() + " Refresh token is expired. Please make a new login..!");
        }
        return token;
    }

    @Transactional
    public Optional<RefreshToken> deleteToken(String username) {
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isPresent()) {
            LOGGER.info("LOGOUT COMPLETE");
            return refreshTokenRepository.deleteByUserId(user.get().getId());
        } else {
            return Optional.empty();
        }
    }
}

