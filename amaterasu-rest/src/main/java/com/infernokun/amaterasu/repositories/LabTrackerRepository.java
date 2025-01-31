package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabTracker;
import com.infernokun.amaterasu.models.entities.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LabTrackerRepository extends JpaRepository<LabTracker, String> {

    Optional<LabTracker> findLabTrackerByLabStartedAndLabOwner(Lab labStarted, Team labOwner);
}
