package com.infernokun.amaterasu_rest.repositories;

import com.infernokun.amaterasu_rest.models.entities.Lab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LabRepository extends JpaRepository<Lab, String> {
}
