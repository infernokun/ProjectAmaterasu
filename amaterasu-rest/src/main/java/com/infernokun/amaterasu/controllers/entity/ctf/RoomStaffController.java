package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.services.entity.AuditService;
import com.infernokun.amaterasu.services.entity.ctf.CTFEntityService;
import com.infernokun.amaterasu.services.entity.ctf.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static com.infernokun.amaterasu.utils.AmaterasuConstants.buildSuccessResponse;

/**
 * Who runs a room and the order of its challenges. SecurityConfig lets authors edit both and lets
 * admins and facilitators list the rooms they run.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/room")
public class RoomStaffController {
    private final RoomService roomService;
    private final CTFEntityService ctfEntityService;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public record Person(String id, String username) {
    }

    public record FacilitatorsRequest(List<String> userIds) {
    }

    public record OrderRequest(List<String> challengeIds) {
    }

    /** The rooms the caller may open in the live view: every room for admins, their own for facilitators. */
    @GetMapping("/facilitated")
    public ResponseEntity<ApiResponse<List<Room>>> facilitated() {
        List<Room> rooms = roomService.findAllRooms().stream()
                .filter(currentUser::canRun)
                .sorted(Comparator.comparing(room -> room.getName() == null ? "" : room.getName()))
                .toList();
        return buildSuccessResponse("Rooms you can run", rooms, HttpStatus.OK);
    }

    /** Accounts that can be made a room's facilitator. Admins already run every room. */
    @GetMapping("/facilitator-candidates")
    public ResponseEntity<ApiResponse<List<Person>>> candidates() {
        List<Person> people = userRepository.findByRole(Role.FACILITATOR).orElse(List.of()).stream()
                .filter(user -> !Boolean.TRUE.equals(user.getDisabled()))
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(RoomStaffController::person)
                .toList();
        return buildSuccessResponse("Facilitators", people, HttpStatus.OK);
    }

    @GetMapping("/{roomId}/facilitators")
    public ResponseEntity<ApiResponse<List<Person>>> facilitators(@PathVariable String roomId) {
        return buildSuccessResponse("Room facilitators", people(roomService.findByRoomId(roomId)), HttpStatus.OK);
    }

    /** Replaces the room's facilitators. Each one has to be an account with the Facilitator role. */
    @PutMapping("/{roomId}/facilitators")
    public ResponseEntity<ApiResponse<List<Person>>> setFacilitators(@PathVariable String roomId,
                                                                     @RequestBody FacilitatorsRequest request) {
        Room room = roomService.findByRoomId(roomId);
        List<String> ids = request == null || request.userIds() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(request.userIds()));
        for (String id : ids) {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("One of the picked accounts no longer exists"));
            if (user.getRole() != Role.FACILITATOR) {
                throw new IllegalArgumentException(user.getUsername() + " is not a facilitator. Change their role in Users first.");
            }
        }
        room.setFacilitators(ids);
        room.setUpdatedAt(LocalDateTime.now());
        Room saved = roomService.saveRoom(room);
        List<Person> people = people(saved);
        auditService.record("ROOM_FACILITATORS_CHANGED", "ROOM", saved.getId(), saved.getName(), null,
                people.isEmpty() ? "No facilitators" : "Facilitated by " + String.join(", ", people.stream().map(Person::username).toList()));
        return buildSuccessResponse("Facilitators saved", people, HttpStatus.OK);
    }

    /** Sets the order players see the room's challenges in. */
    @PutMapping("/{roomId}/order")
    public ResponseEntity<ApiResponse<List<String>>> reorder(@PathVariable String roomId, @RequestBody OrderRequest request) {
        Room room = roomService.findByRoomId(roomId);
        List<CTFEntity> ordered = ctfEntityService.reorder(roomId, request == null ? null : request.challengeIds());
        auditService.record("CHALLENGES_REORDERED", "ROOM", room.getId(), room.getName(), null,
                ordered.size() + " challenges");
        return buildSuccessResponse("Order saved", ordered.stream().map(CTFEntity::getId).toList(), HttpStatus.OK);
    }

    private List<Person> people(Room room) {
        List<Person> people = new ArrayList<>();
        for (String id : Optional.ofNullable(room.getFacilitators()).orElse(List.of())) {
            userRepository.findById(id).map(RoomStaffController::person).ifPresent(people::add);
        }
        return people;
    }

    private static Person person(User user) {
        return new Person(user.getId(), user.getUsername());
    }
}
