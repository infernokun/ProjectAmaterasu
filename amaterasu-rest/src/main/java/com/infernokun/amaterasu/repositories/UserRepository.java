package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);
    Optional<List<User>> findByRole(Role role);
}
