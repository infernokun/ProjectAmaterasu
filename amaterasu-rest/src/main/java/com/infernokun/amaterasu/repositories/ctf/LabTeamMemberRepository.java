package com.infernokun.amaterasu.repositories.ctf;

import com.infernokun.amaterasu.models.entities.ctf.LabTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabTeamMemberRepository extends JpaRepository<LabTeamMember, String> {
    List<LabTeamMember> findByRoomId(String roomId);

    Optional<LabTeamMember> findByRoomIdAndUserId(String roomId, String userId);

    List<LabTeamMember> findByTeamId(String teamId);

    List<LabTeamMember> findByUserId(String userId);
}
