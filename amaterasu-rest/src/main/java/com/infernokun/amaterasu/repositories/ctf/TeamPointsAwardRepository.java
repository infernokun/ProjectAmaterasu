package com.infernokun.amaterasu.repositories.ctf;

import com.infernokun.amaterasu.models.entities.ctf.TeamPointsAward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamPointsAwardRepository extends JpaRepository<TeamPointsAward, String> {
    List<TeamPointsAward> findByRoomIdOrderByAwardedAtAsc(String roomId);

    List<TeamPointsAward> findByRoomId(String roomId);
}
