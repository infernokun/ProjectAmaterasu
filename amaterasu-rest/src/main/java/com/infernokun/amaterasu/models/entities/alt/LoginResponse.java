package com.infernokun.amaterasu.models.entities.alt;

import com.infernokun.amaterasu.models.entities.User;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private String accessToken;      // JWT, 15 minutes
    private String refreshToken;     // Opaque, rotated on every refresh
    private UserResponse user;

    public LoginResponse(String accessToken, User user, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.user = UserResponse.builder()
                .id(user.getId())
                .role(user.getRole())
                .team(user.getTeam())
                .username(user.getUsername())
                .build();
    }
}

