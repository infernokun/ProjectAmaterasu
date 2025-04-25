package com.infernokun.amaterasu.models.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.infernokun.amaterasu.models.auth.SimpleGrantedAuthority;
import com.infernokun.amaterasu.models.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User extends StoredObject implements UserDetails {
    private String username;
    @JsonIgnore
    private String password;
    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.MEMBER;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @JsonIgnore
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.role == null) {
            return Collections.emptyList();
        }
        return List.of(new SimpleGrantedAuthority(this.role.getValue()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + getId() + '\'' +
                ", username='" + username + '\'' +
                ", role=" + role +
                ", team=" + (team != null ? team.getId() : "null") +
                ", createdBy='" + getCreatedBy() + '\'' +
                ", updatedBy='" + getUpdatedBy() + '\'' +
                ", createdAt=" + getCreatedAt() +
                ", updatedAt=" + getUpdatedAt() +
                ", versioning=" + getVersioning() +
                ", accountNonExpired=" + isAccountNonExpired() +
                ", accountNonLocked=" + isAccountNonLocked() +
                ", credentialsNonExpired=" + isCredentialsNonExpired() +
                ", enabled=" + isEnabled() +
                '}';
    }
}
