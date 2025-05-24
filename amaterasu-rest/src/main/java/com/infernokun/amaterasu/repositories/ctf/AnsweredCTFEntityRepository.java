package com.infernokun.amaterasu.repositories.ctf;

import com.infernokun.amaterasu.models.entities.ctf.AnsweredCTFEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnsweredCTFEntityRepository extends JpaRepository<AnsweredCTFEntity, String> {
    Optional<AnsweredCTFEntity> findByUserIdAndCtfEntityId(String userId, String ctfEntityId);
}
