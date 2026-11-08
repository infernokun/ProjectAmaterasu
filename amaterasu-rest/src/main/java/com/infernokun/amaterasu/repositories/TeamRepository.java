package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, String> {
    List<Team> findAllByOrderByNameAsc();
}
