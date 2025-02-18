package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.ApplicationInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationInfoRepository extends JpaRepository<ApplicationInfo, String> {
}
