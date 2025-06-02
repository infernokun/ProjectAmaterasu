package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.dto.ctf.JoinRoomResponse;
import com.infernokun.amaterasu.models.dto.ctf.RoomJoinableRequest;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import com.infernokun.amaterasu.services.entity.UserService;
import com.infernokun.amaterasu.services.entity.ctf.RoomService;
import com.infernokun.amaterasu.services.entity.ctf.RoomUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.infernokun.amaterasu.utils.AmaterasuConstants.buildSuccessResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/room")
public class RoomController {
    private final RoomService roomService;
    private final UserService userService;
    private final RoomUserService roomUserService;

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
        Room room;
        String message;

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

    @PostMapping("/join/{roomId}/{userId}")
    public ResponseEntity<ApiResponse<JoinRoomResponse>> joinRoom(@PathVariable String userId, @PathVariable String roomId) {
        User user = userService.findUserById(userId);
        Room room = roomService.findByRoomId(roomId);

        // Check if already joined (optional, depends on logic)
        if (roomUserService.findByUserAndRoom(user, room).isPresent()) {
            return buildSuccessResponse("User already joined this room", null, HttpStatus.CONFLICT);
        }

        RoomUser roomUser = new RoomUser();
        roomUser.setUser(user);
        roomUser.setRoom(room);
        roomUser.setPoints(0);
        roomUser.setRoomUserStatus(RoomUserStatus.JOINED);

        roomUserService.save(roomUser);

        return buildSuccessResponse("User " + user.getUsername() + " joined room " + room.getName(),
                JoinRoomResponse.builder()
                        .roomId(roomUser.getRoom().getId())
                        .userId(roomUser.getUser().getId())
                        .roomUserStatus(roomUser.getRoomUserStatus())
                        .build(),
                HttpStatus.OK);
    }

    @PostMapping("/check-joinable")
    public ResponseEntity<ApiResponse<Map<String, JoinRoomResponse>>> checkJoinable(
            @RequestBody RoomJoinableRequest roomJoinableRequest) {

        List<RoomUser> eachRoomForUser = roomUserService.findByUserIdAndRoomIds(roomJoinableRequest.getUserId(),
                roomJoinableRequest.getRoomIds());

        Map<String, JoinRoomResponse> roomStatus = new HashMap<>();

        eachRoomForUser.forEach(roomUser -> {
            roomStatus.put(roomUser.getRoom().getId(), JoinRoomResponse.builder()
                            .points(roomUser.getPoints())
                            .roomId(roomUser.getRoom().getId())
                            .userId(roomUser.getId())
                            .roomUserStatus(roomUser.getRoomUserStatus())
                    .build());
        });

        return buildSuccessResponse("Checked join status for rooms.", roomStatus, HttpStatus.OK);
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