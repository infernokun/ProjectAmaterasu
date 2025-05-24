package com.infernokun.amaterasu.repositories.ctf;

import com.infernokun.amaterasu.models.entities.ctf.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {
    Optional<Room> findByName(String name);
}
