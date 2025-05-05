package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.models.entities.LabFileChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LabFileChangeLogRepository extends JpaRepository<LabFileChangeLog, String> {
    Optional<LabFileChangeLog> findByLab(Lab lab);
    void deleteByLabId(String labId);
}
