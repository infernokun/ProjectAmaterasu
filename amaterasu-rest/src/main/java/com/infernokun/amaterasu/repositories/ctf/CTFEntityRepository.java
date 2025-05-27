package com.infernokun.amaterasu.repositories.ctf;

import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CTFEntityRepository extends JpaRepository<CTFEntity, String> {

    @Query("SELECT DISTINCT c FROM CTFEntity c LEFT JOIN FETCH c.flags")
    List<CTFEntity> findAllWithFlags();

    @Query("SELECT DISTINCT c FROM CTFEntity c LEFT JOIN FETCH c.flags WHERE c.id = :id")
    Optional<CTFEntity> findByIdWithFlags(@Param("id") String id);

    List<CTFEntity> findByRoomId(String roomId);
}