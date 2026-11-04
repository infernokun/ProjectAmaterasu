package com.infernokun.amaterasu.repositories.lab;

import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface LabTrackerRepository extends JpaRepository<LabTracker, String> {

    Optional<LabTracker> findLabTrackerByLabStartedAndLabOwner(Lab labStarted, Team labOwner);

    Optional<LabTracker> findLabTrackerByLabStartedAndLabOwnerAndLabStatusNot(Lab labStarted, Team labOwner, LabStatus labStatus);

    List<LabTracker> findLabTrackersByLabOwner(Team labOwner);

    List<LabTracker> findByLabStatus(LabStatus labStatus);

    List<LabTracker> findByLabStarted(Lab labStarted);

    // Bulk update so the cron never bumps the version a running lab action is about to save
    @Modifying
    @Query("update LabTracker t set t.vms = :vms, t.labStatus = :status where t.id = :id and t.labStatus = :expected")
    int updateVmsIfStatus(@Param("id") String id, @Param("vms") List<ProxmoxVM> vms,
                          @Param("status") LabStatus status, @Param("expected") LabStatus expected);
}
