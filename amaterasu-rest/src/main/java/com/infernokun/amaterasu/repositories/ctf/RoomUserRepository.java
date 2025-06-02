package com.infernokun.amaterasu.repositories.ctf;

import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomUserRepository extends JpaRepository<RoomUser, Long> {
    Optional<RoomUser> findByUserAndRoom(User user, Room room);

    @Query("SELECT ru FROM RoomUser ru WHERE ru.user.id = :userId AND ru.room.id IN :roomIds")
    List<RoomUser> findByUserIdAndRoomIds(@Param("userId") String userId, @Param("roomIds") List<String> roomIds);
}