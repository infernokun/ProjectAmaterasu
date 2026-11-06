package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);
    Optional<List<User>> findByRole(Role role);
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:username)")
    Optional<User> findByUsernameIgnoreCase(@Param("username") String username);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.username) = LOWER(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    /** [teamId, count] for every team with enabled members. */
    @Query("SELECT u.team.id, COUNT(u) FROM User u WHERE u.team IS NOT NULL AND (u.disabled IS NULL OR u.disabled = false)"
            + " GROUP BY u.team.id")
    List<Object[]> countEnabledByTeam();
}
