package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.PointsHistoryEntry;
import com.infernokun.amaterasu.models.entities.ctf.dto.AwardPointsRequest;
import com.infernokun.amaterasu.websocket.RoomActivityEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityAnswer;
import com.infernokun.amaterasu.models.entities.ctf.dto.JoinRoomResponse;
import com.infernokun.amaterasu.models.entities.ctf.dto.RoomJoinableRequest;
import com.infernokun.amaterasu.models.entities.ctf.dto.RoomRequest;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.TeamMode;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.ctf.dto.RoomUserProfileResponse;
import com.infernokun.amaterasu.models.entities.ctf.dto.RoomUserResponse;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import com.infernokun.amaterasu.services.entity.AuditService;
import com.infernokun.amaterasu.services.entity.UserService;
import com.infernokun.amaterasu.utils.AmaterasuConstants;
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
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;
    private final AuditService auditService;

    static final int MAX_AWARD = 10_000;
    static final int MAX_REASON = 200;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Room>>> getAllRooms(@RequestParam(required = false)
                                                               Map<String, String> params) {
        List<Room> rooms = this.roomService.findAllRooms().stream().filter(currentUser::canSee).toList();
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

        Room room = roomService.findByRoomId(roomId);
        RoomUser roomUser = roomUserService.findByUserAndRoom(user, room)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User is not a member of room " + roomId));

        List<CTFEntityAnswer> roomUserAnswers = ctfAnswerService.findByRoomUserId(roomUser);

        AtomicInteger fails = new AtomicInteger();
        AtomicInteger correct = new AtomicInteger();
        Map<String, Integer> correctByCategory = new HashMap<>();

        List<RoomUser> allRoomUsers = roomUserService.findByRoomIdOrderByPointsDesc(roomId);

        roomUserAnswers.forEach(roomUserAnswer -> {
            if (roomUserAnswer.getCorrect()) {
                // Failed attempts, excluding the correct one
                fails.set(fails.get() + Math.max(0, roomUserAnswer.getAttempts() - 1));
                correct.set(correct.get() + 1);

                // Track correct answers by category
                String category = roomUserAnswer.getCtfEntity().getCategory();
                correctByCategory.put(category,
                        correctByCategory.getOrDefault(category, 0) + 1);
            } else {
                // Add all attempts as fails for incorrect answers
                fails.set(fails.get() + roomUserAnswer.getAttempts());
            }
        });

        RoomUserProfileResponse roomUserProfileResponse = new RoomUserProfileResponse(
                roomUser.getUser().getUsername(),
                roomUser.getPoints(),
                roomUser.getPointsHistory(),
                fails.get(),
                correct.get(),
                correctByCategory,
                calculateUserRank(allRoomUsers, roomUser)
        );

        return buildSuccessResponse(
                String.format("Retrieved %s for room %s scoreboard", roomUserProfileResponse.getUsername(), roomId),
                roomUserProfileResponse, // Return the correct object
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

    /** Creates a lab. It needs a name and a scenario; the environment is optional. */
    @PostMapping
    public ResponseEntity<ApiResponse<Room>> createRoom(@RequestBody RoomRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Send the lab's name and scenario");
        }
        Room room = new Room();
        room.setName(roomService.requireUniqueName(request.name(), null));
        room.setSurroundTag(request.surroundTag() == null || request.surroundTag().isBlank()
                ? AmaterasuConstants.DEFAULT_SURROUND_TAG : request.surroundTag().trim());
        room.setScenario(roomService.requireScenario(request.scenario()));
        roomService.applyEnvironment(room, request.environmentMode() == null ? EnvironmentMode.NONE : request.environmentMode(),
                request.environmentId());
        roomService.applyTeams(room, request.teamMode(), request.maxTeamSize(), request.teamCredit());
        room.setPublished(!Boolean.FALSE.equals(request.published()));
        room.setCreator(currentUser.user());
        Room savedRoom = this.roomService.saveRoom(room);
        auditService.record("ROOM_CREATED", "ROOM", savedRoom.getId(), savedRoom.getName(), null, null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Room>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Lab created successfully.")
                        .data(savedRoom)
                        .build());
    }

    @PostMapping("/join/{roomId}/{userId}")
    public ResponseEntity<ApiResponse<JoinRoomResponse>> joinRoom(@PathVariable String userId,
                                                                  @PathVariable String roomId) {
        currentUser.requireSelfOrAdmin(userId);
        User user = userService.findUserById(userId);
        Room room = currentUser.requireCanSee(roomService.findByRoomId(roomId));
        if (room.effectiveTeamMode() == TeamMode.TEAMS_REQUIRED
                && roomUserService.findByUserAndRoom(user, room).map(RoomUser::getTeam).isEmpty()) {
            throw new IllegalArgumentException(room.getName() + " is played in teams. Join or create a team on its page.");
        }

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
        currentUser.requireSelfOrAdmin(userId);
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

        currentUser.requireSelfOrAdmin(roomJoinableRequest.getUserId());
        List<RoomUser> eachRoomForUser = roomUserService.findByUserIdAndRoomIds(roomJoinableRequest.getUserId(),
                roomJoinableRequest.getRoomIds());

        Map<String, JoinRoomResponse> roomStatus = new HashMap<>();

        eachRoomForUser.forEach(roomUser -> roomStatus.put(roomUser.getRoom().getId(), JoinRoomResponse.builder()
                        .points(roomUser.getPoints())
                        .roomId(roomUser.getRoom().getId())
                        .userId(roomUser.getUser().getId())
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

    /** Admins and the room's facilitators add or take away points, with a reason the player sees. */
    @PostMapping("/add-points/{roomId}/{userId}")
    public ResponseEntity<ApiResponse<JoinRoomResponse>> addPoints(@PathVariable String userId,
                                                                   @PathVariable String roomId,
                                                                   @RequestBody AwardPointsRequest request) {
        Room room = roomService.findByRoomId(roomId);
        currentUser.requireCanRun(room);
        int points = request == null || request.points() == null ? 0 : request.points();
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (points == 0 || Math.abs(points) > MAX_AWARD) {
            throw new IllegalArgumentException("Points must be a whole number from -" + MAX_AWARD + " to " + MAX_AWARD
                    + ", and not 0");
        }
        if (reason.isEmpty() || reason.length() > MAX_REASON) {
            throw new IllegalArgumentException("Give a reason of up to " + MAX_REASON + " characters");
        }

        User user = userService.findUserById(userId);

        Optional<RoomUser> roomUserOpt = roomUserService.findByUserAndRoom(user, room);

        if (roomUserOpt.isEmpty()) {
            return buildSuccessResponse("User is not in this room", null, HttpStatus.BAD_REQUEST);
        }

        RoomUser roomUser = roomUserOpt.get();

        int total = roomUser.getPoints() + points;
        roomUser.getPointsHistory().add(new PointsHistoryEntry(LocalDateTime.now(), total, points, reason,
                currentUser.user().getUsername()));
        roomUser.setPoints(total);
        roomUser.setUpdatedAt(LocalDateTime.now());
        roomUser = roomUserService.save(roomUser);
        events.publishEvent(new RoomActivityEvent(roomId));
        auditService.record("POINTS_AWARDED", "USER", user.getId(), user.getUsername(), user.getTeam() != null ? user.getTeam().getId() : null, points + " pts in " + room.getName() + ": " + reason);

        return buildSuccessResponse(String.format("User %s has %d points in room %s",
                roomUser.getUser().getUsername(), roomUser.getPoints(), roomUser.getRoom().getName()),
                JoinRoomResponse.builder()
                        .points(roomUser.getPoints())
                        .roomId(roomUser.getRoom().getId())
                        .userId(roomUser.getUser().getId())
                        .roomUserStatus(roomUser.getRoomUserStatus())
                        .build(), HttpStatus.OK);
    }

    /** Edits a lab. Fields left out stay as they are. */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Room>> updateRoom(@PathVariable String id, @RequestBody RoomRequest request) {
        Room existingRoom = this.roomService.findByRoomId(id);
        if (request.name() != null) {
            existingRoom.setName(roomService.requireUniqueName(request.name(), id));
        }
        if (request.surroundTag() != null && !request.surroundTag().isBlank()) {
            existingRoom.setSurroundTag(request.surroundTag().trim());
        }
        if (request.scenario() != null) {
            existingRoom.setScenario(roomService.requireScenario(request.scenario()));
        }
        if (request.environmentMode() != null || request.environmentId() != null) {
            roomService.changeEnvironment(existingRoom,
                    request.environmentMode() != null ? request.environmentMode() : existingRoom.effectiveMode(),
                    request.environmentId() != null ? request.environmentId() : existingRoom.getEnvironmentId());
        }
        roomService.applyTeams(existingRoom, request.teamMode(), request.maxTeamSize(), request.teamCredit());
        if (request.published() != null) {
            roomService.changePublished(existingRoom, request.published());
        }
        existingRoom.setUpdatedAt(LocalDateTime.now());
        Room updatedRoom = this.roomService.saveRoom(existingRoom);
        auditService.record("ROOM_UPDATED", "ROOM", id, updatedRoom.getName(), null, null);
        return ResponseEntity.ok(
                ApiResponse.<Room>builder()
                        .code(HttpStatus.OK.value())
                        .message("Lab updated successfully.")
                        .data(updatedRoom)
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(@PathVariable String id) {
        Room deleted = this.roomService.deleteRoom(id);
        auditService.record("ROOM_DELETED", "ROOM", id, deleted.getName(), null, null);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(HttpStatus.OK.value())
                        .message("Room deleted successfully.")
                        .data(null)
                        .build()
        );
    }

    private String calculateUserRank(List<RoomUser> allRoomUsers, RoomUser currentUser) {
        // Find the user's position in the sorted list (1-based ranking)
        int rank = 1;
        int currentUserPoints = currentUser.getPoints();

        for (RoomUser user : allRoomUsers) {
            if (user.getId().equals(currentUser.getId())) {
                break; // Found our user, rank is current position
            }

            // Only increment rank if the user has more points
            if (user.getPoints() > currentUserPoints) {
                rank++;
            }
        }

        return formatRank(rank);
    }

    private String formatRank(int rank) {
        if (rank >= 11 && rank <= 13) {
            return rank + "th";
        }

        switch (rank % 10) {
            case 1:
                return rank + "st";
            case 2:
                return rank + "nd";
            case 3:
                return rank + "rd";
            default:
                return rank + "th";
        }
    }
}
