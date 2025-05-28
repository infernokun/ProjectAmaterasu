package com.infernokun.amaterasu.repositories.lab;

import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.entities.lab.LabFileChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LabFileChangeLogRepository extends JpaRepository<LabFileChangeLog, String> {
    Optional<LabFileChangeLog> findByLab(Lab lab);
    void deleteByLabId(String labId);
}
