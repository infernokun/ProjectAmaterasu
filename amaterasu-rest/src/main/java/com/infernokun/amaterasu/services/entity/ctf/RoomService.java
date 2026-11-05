package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.repositories.ctf.RoomRepository;
import com.infernokun.amaterasu.repositories.ctf.RoomUserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomRepository roomRepository;
    private final RoomUserRepository roomUserRepository;
    private final CTFEntityService ctfEntityService;

    public List<Room> findAllRooms() {
        return roomRepository.findAll();
    }

    public Room findByRoomName(String roomName) {
        return roomRepository.findByName(roomName)
                .orElseThrow(() -> new ResourceNotFoundException("Room with name '" + roomName + "' not found."));
    }

    public Room findByRoomId(String id) {
        return roomRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Room with id '" + id + "' not found."));
    }

    public List<Room> findAllByIds(List<String> ids) {
        return roomRepository.findAllByIdIn(ids);
    }

    public Room saveRoom(Room room) { return roomRepository.save(room); }

    public List<Room> saveRooms(List<Room> rooms) {
        return roomRepository.saveAll(rooms);
    }

    /** Deletes a room with its challenges, the answers given and who joined it. */
    @Transactional
    public Room deleteRoom(String id) {
        Room room = findByRoomId(id);
        ctfEntityService.deleteByRoomId(id);
        roomUserRepository.deleteAll(roomUserRepository.findByRoomId(id));
        roomRepository.delete(room);
        return room;
    }

    /** A trimmed, unused room name. */
    public String requireUniqueName(String name, String exceptId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Room name required");
        }
        boolean taken = roomRepository.findAll().stream()
                .anyMatch(other -> trimmed.equalsIgnoreCase(other.getName()) && !other.getId().equals(exceptId));
        if (taken) {
            throw new IllegalArgumentException("A room named " + trimmed + " already exists");
        }
        return trimmed;
    }
}
