package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, String> {
    /** Site teams only; lab teams belong to one lab. */
    List<Team> findByLabIdIsNullOrderByNameAsc();

    List<Team> findByLabIdOrderByNameAsc(String labId);
}
