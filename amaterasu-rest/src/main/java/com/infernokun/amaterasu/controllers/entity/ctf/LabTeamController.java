package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.LabSeat;
import com.infernokun.amaterasu.services.entity.AuditService;
import com.infernokun.amaterasu.services.entity.ctf.LabTeamService;
import com.infernokun.amaterasu.services.entity.ctf.RoomService;
import com.infernokun.amaterasu.websocket.RoomActivityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

import static com.infernokun.amaterasu.utils.AmaterasuConstants.buildSuccessResponse;

/** Joining a lab solo or on a team, and the teams inside a lab. Any signed in user, for themselves. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/room/{roomId}")
public class LabTeamController {
    private final LabTeamService labTeamService;
    private final RoomService roomService;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    @GetMapping("/teams")
    public ResponseEntity<ApiResponse<List<LabTeamService.LabTeamView>>> teams(@PathVariable String roomId) {
        return buildSuccessResponse("Teams", labTeamService.teams(roomService.findByRoomId(roomId)), HttpStatus.OK);
    }

    /** Joins the lab solo ({}), on a team ({"teamId"}) or on a new team ({"newTeamName"}), as the lab allows. */
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<LabSeat>> join(@PathVariable String roomId,
                                                     @RequestBody(required = false) LabTeamService.JoinRequest request) {
        Room room = roomService.findByRoomId(roomId);
        User user = currentUser.user();
        Team before = labTeamService.teamOf(room, user).orElse(null);
        RoomUser seat = labTeamService.join(room, user, request == null ? new LabTeamService.JoinRequest(null, null) : request);
        recordTeam(room, seat, before);
        return buildSuccessResponse("Joined " + room.getName(), seat(seat), HttpStatus.OK);
    }

    /** Moves a joined player to another team, a new one, or solo ({}). */
    @PostMapping("/team")
    public ResponseEntity<ApiResponse<LabSeat>> changeTeam(@PathVariable String roomId,
                                                           @RequestBody(required = false) LabTeamService.JoinRequest request) {
        Room room = roomService.findByRoomId(roomId);
        User user = currentUser.user();
        Team before = labTeamService.teamOf(room, user).orElse(null);
        RoomUser seat = labTeamService.changeTeam(room, user,
                request == null ? new LabTeamService.JoinRequest(null, null) : request);
        recordTeam(room, seat, before);
        return buildSuccessResponse(seat.getTeam() != null ? "On " + seat.getTeam().getName() : "Playing solo",
                seat(seat), HttpStatus.OK);
    }

    private void recordTeam(Room room, RoomUser seat, Team before) {
        Team after = seat.getTeam();
        String beforeId = before != null ? before.getId() : null;
        String afterId = after != null ? after.getId() : null;
        if (!Objects.equals(beforeId, afterId)) {
            auditService.record("LAB_TEAM_CHANGED", "ROOM", room.getId(), room.getName(), afterId,
                    seat.getUser().getUsername() + (after != null ? " joined " + after.getName() : " plays solo"));
            events.publishEvent(new RoomActivityEvent(room.getId()));
        }
    }

    static LabSeat seat(RoomUser seat) {
        Team team = seat.getTeam();
        return new LabSeat(seat.getRoom().getId(), seat.getUser().getId(), seat.getRoomUserStatus(),
                seat.getPoints() == null ? 0 : seat.getPoints(), team != null ? team.getId() : null,
                team != null ? team.getName() : null);
    }
}
