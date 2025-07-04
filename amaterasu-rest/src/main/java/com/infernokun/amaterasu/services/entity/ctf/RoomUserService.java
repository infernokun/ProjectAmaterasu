package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.repositories.ctf.RoomUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RoomUserService {
    private final RoomUserRepository roomUserRepository;

    public RoomUser save(RoomUser roomUser) {
        return roomUserRepository.save(roomUser);
    }

    public List<RoomUser> findAllRoomUsers() {
        return roomUserRepository.findAll();
    }

    public RoomUser findByUserId(String userId) {
        return this.roomUserRepository.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException(
                "No room user found by user id " + userId + "!"));
    }

    public List<RoomUser> findByRoomIdOrderByPointsDesc(String roomId) {
        return roomUserRepository.findByRoomIdOrderByPointsDesc(roomId);
    }

    public Optional<RoomUser> findByUserAndRoom(User user, Room room) {
        return roomUserRepository.findByUserAndRoom(user, room);
    }

    public List<RoomUser> findByUserIdAndRoomIds(String userId, List<String> roomIds) {
        return roomUserRepository.findByUserIdAndRoomIds(userId, roomIds);
    }
}
