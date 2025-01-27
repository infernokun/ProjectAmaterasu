package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, String> {
}
