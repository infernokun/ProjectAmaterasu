package com.infernokun.amaterasu.repositories.ctf;

import com.infernokun.amaterasu.models.entities.ctf.CTFAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CTFAnswerRepository extends JpaRepository<CTFAnswer, String> {
    Optional<CTFAnswer> findByUserIdAndCtfEntityId(String userId, String ctfEntityId);
}
