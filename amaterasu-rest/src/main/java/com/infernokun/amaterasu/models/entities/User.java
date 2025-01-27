package com.infernokun.amaterasu.models.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User extends StoredObject {
    private String username;
    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;
}
