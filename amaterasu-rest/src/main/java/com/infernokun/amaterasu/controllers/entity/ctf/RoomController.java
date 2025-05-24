package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.services.ctf.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/room")
public class RoomController {
    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Room>>> getAllRooms(@RequestParam(required = false)
                                                               Map<String, String> params) {
        List<Room> rooms = this.roomService.findAllRooms();
        return ResponseEntity.ok(
                ApiResponse.<List<Room>>builder()
                        .code(HttpStatus.OK.value())
                        .message("Rooms retrieved successfully.")
                        .data(rooms)
                        .build()
        );
    }

    @GetMapping("/by")
    public ResponseEntity<ApiResponse<Room>> getRoomBy(@RequestParam Map<String, String> params) {
        Room room = null;
        String message = "Room retrieved successfully.";

        if (params.containsKey("name")) {
            String roomName = params.get("name");
            room = this.roomService.findByRoomName(roomName);
            message = String.format("Room with name '%s' retrieved successfully.", roomName);

        } else if (params.containsKey("id")) {
            String roomId = params.get("id");
            room = this.roomService.findByRoomId(roomId);
            message = String.format("Room with ID '%s' retrieved successfully.", roomId);
        } else {
            throw new IllegalArgumentException("At least one search parameter is required: name, id, creator, or tag");
        }

        return ResponseEntity.ok(
                ApiResponse.<Room>builder()
                        .code(HttpStatus.OK.value())
                        .message(message)
                        .data(room)
                        .build()
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Room>> createRoom(@RequestBody Room room) {
        Room savedRoom = this.roomService.saveRoom(room);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Room>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Room created successfully.")
                        .data(savedRoom)
                        .build());
    }

    @PostMapping("/many")
    public ResponseEntity<ApiResponse<List<Room>>> createRooms(@RequestBody List<Room> rooms) {
        List<Room> savedRooms = this.roomService.saveRooms(rooms);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<List<Room>>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Rooms created successfully.")
                        .data(savedRooms)
                        .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Room>> getRoomById(@PathVariable String id) {
        Room room = this.roomService.findByRoomId(id);
        return ResponseEntity.ok(
                ApiResponse.<Room>builder()
                        .code(HttpStatus.OK.value())
                        .message("Room retrieved successfully.")
                        .data(room)
                        .build()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Room>> updateRoom(@PathVariable String id, @RequestBody Room room) {
        room.setId(id);
        Room updatedRoom = this.roomService.saveRoom(room);
        return ResponseEntity.ok(
                ApiResponse.<Room>builder()
                        .code(HttpStatus.OK.value())
                        .message("Room updated successfully.")
                        .data(updatedRoom)
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(@PathVariable String id) {
        this.roomService.deleteRoom(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("Room deleted successfully.")
                        .data(null)
                        .build()
        );
    }
}