package com.infernokun.amaterasu.models.dto;

import com.infernokun.amaterasu.models.entities.Team;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private String username;
    private String role;
    private Team team;
}
