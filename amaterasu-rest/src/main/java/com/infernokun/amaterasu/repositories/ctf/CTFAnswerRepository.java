package com.infernokun.amaterasu.repositories.ctf;

import com.infernokun.amaterasu.models.entities.ctf.CTFEntityAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CTFAnswerRepository extends JpaRepository<CTFEntityAnswer, String> {
    Optional<CTFEntityAnswer> findByRoomUserIdAndCtfEntityId(String roomUserId, String ctfEntityId);

    List<CTFEntityAnswer> findByRoomUserId(String roomUserId);
}
