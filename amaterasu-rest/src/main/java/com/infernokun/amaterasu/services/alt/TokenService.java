package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.services.entity.RefreshTokenService;
import com.infernokun.amaterasu.services.entity.UserService;
import com.infernokun.amaterasu.services.BaseService;
import jakarta.annotation.PostConstruct;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TokenService extends BaseService {
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    public TokenService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, RefreshTokenService refreshTokenService, UserService userService) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
    }
    public String generateJwt(UserDetails userDetails) {
        Instant now = Instant.now();
        Instant expiration = now.plus(10, ChronoUnit.MINUTES); // Extended expiration time

        String scope = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self")
                .issuedAt(now)
                .expiresAt(expiration)
                .subject(userDetails.getUsername())
                .claim("roles", scope)
                .build();

        String token = this.jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        this.refreshTokenService.createRefreshToken(userDetails.getUsername(), token, expiration);
        return token;
    }

    @PostConstruct
    public void initializeAdminToken() {
        List<User> users = this.userService.findAllUsers();

        User admin = users.stream()
                .filter(user -> "admin".equals(user.getUsername()))
                .findFirst()
                .orElseGet(() -> new User("admin", "defaultPassword"));

        String scope = admin.getAuthorities() != null
                ? admin.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "))
                : "";

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
                .subject("admin")
                .claim("roles", scope)
                .build();

        LOGGER.info("TOKEN: {}", this.jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue());
    }
}
