package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.enums.LabStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface LabTrackerRepository extends JpaRepository<LabTracker, String> {

    Optional<LabTracker> findLabTrackerByLabStartedAndLabOwner(Lab labStarted, Team labOwner);

    Optional<LabTracker> findLabTrackerByLabStartedAndLabOwnerAndLabStatusNot(Lab labStarted, Team labOwner, LabStatus labStatus);

    List<LabTracker> findLabTrackersByLabOwner(Team labOwner);

    List<LabTracker> findByLabStatus(LabStatus labStatus);
}
