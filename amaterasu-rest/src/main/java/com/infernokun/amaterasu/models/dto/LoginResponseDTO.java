package com.infernokun.amaterasu.models.dto;

import com.infernokun.amaterasu.models.entities.User;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponseDTO {
    private String accessToken;      // Short-lived JWT (30 minutes)
    private String refreshToken;     // Long-lived UUID (90 days)
    private UserDTO user;

    // Constructor for backwards compatibility
    public LoginResponseDTO(String accessToken, User user, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.user = UserDTO.builder()
                .id(user.getId())
                .role(user.getRole())
                .team(user.getTeam())
                .username(user.getUsername())
                .build();
    }
}

