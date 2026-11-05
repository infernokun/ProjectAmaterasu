package com.infernokun.amaterasu.models.entities.alt;

import com.infernokun.amaterasu.models.entities.User;
import lombok.*;

/** The refresh token is not in the body; it is set as an HttpOnly cookie. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private String accessToken;      // JWT, 15 minutes
    private UserResponse user;

    public LoginResponse(String accessToken, User user) {
        this.accessToken = accessToken;
        this.user = UserResponse.builder()
                .id(user.getId())
                .role(user.getRole())
                .team(user.getTeam())
                .username(user.getUsername())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
