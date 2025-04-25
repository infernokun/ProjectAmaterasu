package com.infernokun.amaterasu.models.dto;

import com.infernokun.amaterasu.models.entities.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {
    private String jwt;
    private UserDTO user;

    public LoginResponseDTO(String jwt, User user) {
        this.jwt = jwt;
        this.user = UserDTO.builder()
                .id(user.getId())
                .role(user.getRole())
                .team(user.getTeam())
                .username(user.getUsername())
                .build();
    }
}