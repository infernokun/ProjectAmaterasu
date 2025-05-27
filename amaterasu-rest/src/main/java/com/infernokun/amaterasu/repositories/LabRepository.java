package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.enums.LabType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabRepository extends JpaRepository<Lab, String> {
    List<Lab> findByLabType(LabType labType);
}
