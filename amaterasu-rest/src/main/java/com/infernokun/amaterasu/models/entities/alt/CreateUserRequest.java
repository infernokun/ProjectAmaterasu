package com.infernokun.amaterasu.models.entities.alt;

import com.infernokun.amaterasu.models.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    private String username;
    private String password;
    private Role role;
    /** Optional. */
    private String teamId;
}
