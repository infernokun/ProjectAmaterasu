package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityAnswer;
import com.infernokun.amaterasu.models.entities.ctf.dto.JoinRoomResponse;
import com.infernokun.amaterasu.models.entities.ctf.dto.RoomJoinableRequest;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.ctf.dto.RoomUserProfileResponse;
import com.infernokun.amaterasu.models.entities.ctf.dto.RoomUserResponse;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import com.infernokun.amaterasu.services.entity.UserService;
import com.infernokun.amaterasu.services.entity.ctf.CTFAnswerService;
import com.infernokun.amaterasu.services.entity.ctf.RoomService;
import com.infernokun.amaterasu.services.entity.ctf.RoomUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.infernokun.amaterasu.utils.AmaterasuConstants.buildSuccessResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/room")
public class RoomController {
    private final RoomService roomService;
    private final UserService userService;
    private final RoomUserService roomUserService;
    private final ModelMapper modelMapper;
    private final CTFAnswerService ctfAnswerService;

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

    @GetMapping("/scoreboard/{roomId}")
    public ResponseEntity<ApiResponse<RoomUserProfileResponse>> getScoreboardUserInfo(@PathVariable String roomId) {
        User user = userService.findUserById(SecurityContextHolder.getContext().getAuthentication().getName());

        RoomUser roomUser = roomUserService.findByUserId(user.getId());

        List<CTFEntityAnswer> roomUserAnswers = ctfAnswerService.findByRoomUserId(roomUser);

        AtomicInteger fails = new AtomicInteger();
        AtomicInteger correct = new AtomicInteger();
        Map<String, Integer> correctByCategory = new HashMap<>();

        roomUserAnswers.forEach(roomUserAnswer -> {
            if (roomUserAnswer.getCorrect()) {
                fails.set(fails.get() + roomUserAnswer.getAttempts() - 1);
                correct.set(correct.get() + 1);
                correctByCategory.put(roomUserAnswer.getCtfEntity().getCategory(),
                        correctByCategory.get(roomUserAnswer.getCtfEntity().getCategory()) != null ?
                                correctByCategory.get(roomUserAnswer.getCtfEntity().getCategory()) + 1 : 1);
            } else {
                fails.set(fails.get() + roomUserAnswer.getAttempts());
            }
        });

        RoomUserProfileResponse roomUserProfileResponse = new RoomUserProfileResponse(roomUser.getUser().getUsername(),
                roomUser.getPoints(), roomUser.getPointsHistory(), fails.get(), correct.get(), correctByCategory);

        RoomUserResponse roomUserResponse = new RoomUserResponse(roomUser.getUser().getUsername(),
                roomUser.getPoints(),
                roomUser.getPointsHistory());

        return buildSuccessResponse(
                String.format("Retrieved %s for room %s scoreboard", roomUserProfileResponse.getUsername(), roomId),
                roomUserProfileResponse,
                HttpStatus.OK
        );
    }

    @GetMapping("/scoreboard/{roomId}/users")
    public ResponseEntity<ApiResponse<List<RoomUserResponse>>> getScoreboardUsersInfo(@PathVariable String roomId) {
        // Get room users for the specific room, ordered by points descending
        List<RoomUser> roomUsers = roomUserService.findByRoomIdOrderByPointsDesc(roomId);

        if (roomUsers.isEmpty()) {
            return buildSuccessResponse("No users found in this room", Collections.emptyList(), HttpStatus.OK);
        }

        // Convert to response DTOs using streams for better performance and readability
        List<RoomUserResponse> roomUserResponses = roomUsers.stream()
                .map(roomUser -> new RoomUserResponse(
                        roomUser.getUser().getUsername(),
                        roomUser.getPoints(),
                        roomUser.getPointsHistory()
                ))
                .collect(Collectors.toList());

        return buildSuccessResponse(
                String.format("Retrieved %d users for room %s scoreboard", roomUserResponses.size(), roomId),
                roomUserResponses,
                HttpStatus.OK
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
    public ResponseEntity<ApiResponse<JoinRoomResponse>> joinRoom(@PathVariable String userId,
                                                                  @PathVariable String roomId) {
        User user = userService.findUserById(userId);
        Room room = roomService.findByRoomId(roomId);

        if (roomUserService.findByUserAndRoom(user, room).isPresent()) {
            RoomUser roomUser = roomUserService.findByUserAndRoom(user, room).get();
            if (roomUser.getRoomUserStatus() == RoomUserStatus.JOINED) {
                return buildSuccessResponse("User already joined this room", null, HttpStatus.CONFLICT);

            }
            roomUser.setUpdatedAt(LocalDateTime.now());
            roomUser.setRoomUserStatus(RoomUserStatus.JOINED);
            roomUserService.save(roomUser);

            return buildSuccessResponse("User rejoined this room!",
                    modelMapper.map(roomUser, JoinRoomResponse.class), HttpStatus.OK);
        }

        RoomUser roomUser = new RoomUser();
        roomUser.setUser(user);
        roomUser.setRoom(room);
        roomUser.setPoints(0);
        roomUser.setRoomUserStatus(RoomUserStatus.JOINED);
        roomUser.setUpdatedAt(LocalDateTime.now());

        roomUserService.save(roomUser);

        return buildSuccessResponse("User " + user.getUsername() + " joined room " + room.getName(),
                JoinRoomResponse.builder()
                        .roomId(roomUser.getRoom().getId())
                        .userId(roomUser.getUser().getId())
                        .roomUserStatus(roomUser.getRoomUserStatus())
                        .build(),
                HttpStatus.OK);
    }

    @PostMapping("/leave/{roomId}/{userId}")
    public ResponseEntity<ApiResponse<JoinRoomResponse>> leaveRoom(@PathVariable String userId,
                                                                  @PathVariable String roomId) {
        User user = userService.findUserById(userId);
        Room room = roomService.findByRoomId(roomId);

        Optional<RoomUser> roomUserOpt = roomUserService.findByUserAndRoom(user, room);

        if (roomUserOpt.isEmpty()) {
            return buildSuccessResponse("User is not in this room", null, HttpStatus.BAD_REQUEST);
        }

        RoomUser roomUser = roomUserOpt.get();

        if (roomUser.getRoomUserStatus() == RoomUserStatus.LEFT) {
            return buildSuccessResponse("User already left this room", null, HttpStatus.CONFLICT);
        }

        roomUser.setRoomUserStatus(RoomUserStatus.LEFT);
        roomUser.setUpdatedAt(LocalDateTime.now());
        roomUser = roomUserService.save(roomUser);

        return buildSuccessResponse("User left this room!",
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

        eachRoomForUser.forEach(roomUser -> roomStatus.put(roomUser.getRoom().getId(), JoinRoomResponse.builder()
                        .points(roomUser.getPoints())
                        .roomId(roomUser.getRoom().getId())
                        .userId(roomUser.getId())
                        .roomUserStatus(roomUser.getRoomUserStatus())
                .build()));

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

    @PostMapping("/add-points/{roomId}/{userId}")
    public ResponseEntity<ApiResponse<JoinRoomResponse>> addPoints(@PathVariable String userId,
                                                                   @PathVariable String roomId) {

        User user = userService.findUserById(userId);
        Room room = roomService.findByRoomId(roomId);

        Optional<RoomUser> roomUserOpt = roomUserService.findByUserAndRoom(user, room);

        if (roomUserOpt.isEmpty()) {
            return buildSuccessResponse("User is not in this room", null, HttpStatus.BAD_REQUEST);
        }

        RoomUser roomUser = roomUserOpt.get();

        roomUser.setUpdatedAt(LocalDateTime.now());
        roomUser.updatePoints(roomUser.getPoints() + 100, "dev add button");
        roomUser = roomUserService.save(roomUser);

        return buildSuccessResponse(String.format("User %s has %d points in room %s",
                roomUser.getUser().getUsername(), roomUser.getPoints() + 100, roomUser.getRoom().getName()),
                JoinRoomResponse.builder()
                        .points(roomUser.getPoints())
                        .roomId(roomUser.getRoom().getId())
                        .userId(roomUser.getUser().getId())
                        .roomUserStatus(roomUser.getRoomUserStatus())
                        .build(), HttpStatus.OK);
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