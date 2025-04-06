package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.exceptions.TokenException;
import com.infernokun.amaterasu.models.entities.RefreshToken;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.repositories.RefreshTokenRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class RefreshTokenService extends BaseService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    // Single cache for tokens
    private static final String TOKEN_CACHE = "tokenCache";

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    public void createRefreshToken(String username, String token, Instant expiration) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Find existing token
        Optional<RefreshToken> existingTokenOpt = this.refreshTokenRepository.findByUserId(user.getId());

        if (existingTokenOpt.isPresent()) {
            RefreshToken existingToken = existingTokenOpt.get();
            // Clear old token from cache
            evictTokenCache(existingToken.getToken());

            existingToken.setToken(token);
            existingToken.setCreationDate(Instant.now());
            existingToken.setExpirationDate(expiration);
            refreshTokenRepository.save(existingToken);
        } else {
            // If no existing token, create a new one
            RefreshToken newToken = RefreshToken.builder()
                    .user(user)
                    .token(token)
                    .creationDate(Instant.now())
                    .expirationDate(expiration)
                    .build();

            refreshTokenRepository.save(newToken);
        }
    }

    public Optional<RefreshToken> findByUserId(String id) {
        // Bypass cache for User lookups to avoid serialization issues
        return this.refreshTokenRepository.findByUserId(id);
    }

    public RefreshToken findByToken(String token) {
        // Bypass cache to avoid serialization issues
        return this.refreshTokenRepository.findByToken(token).orElseThrow(
                () -> new TokenException("Failed to find token."));
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpirationDate().compareTo(Instant.now()) < 0) {
            // Token expired - remove from DB and cache
            this.refreshTokenRepository.delete(token);
            evictTokenCache(token.getToken());

            throw new RuntimeException(token.getToken() + " Refresh token is expired. Please make a new login..!");
        }
        return token;
    }

    @Transactional
    public Optional<RefreshToken> deleteToken(String id) {
        Optional<User> user = userRepository.findById(id);
        if (user.isPresent()) {
            // Get the token before deleting to clear cache by token
            Optional<RefreshToken> tokenOpt = this.refreshTokenRepository.findByUserId(id);
            tokenOpt.ifPresent(refreshToken -> evictTokenCache(refreshToken.getToken()));

            LOGGER.info("LOGOUT COMPLETE");
            return refreshTokenRepository.deleteByUserId(id);
        } else {
            return Optional.empty();
        }
    }

    @CacheEvict(value = TOKEN_CACHE, key = "#token")
    public void evictTokenCache(String token) {
        // Method is empty because the annotation does the work
    }
}