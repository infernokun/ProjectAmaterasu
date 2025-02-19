package com.infernokun.amaterasu.models.dto;

import com.infernokun.amaterasu.models.entities.Team;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserDTO {
    private String username;
    private String role;
    private Team team;
}
