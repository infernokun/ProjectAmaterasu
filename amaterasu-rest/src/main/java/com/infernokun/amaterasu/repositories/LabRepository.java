package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.Lab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LabRepository extends JpaRepository<Lab, String> {


}
