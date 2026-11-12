package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.dto.manage.LabTeamsView;
import com.infernokun.amaterasu.models.enums.TeamMode;
import com.infernokun.amaterasu.services.entity.AuditService;
import com.infernokun.amaterasu.services.entity.ctf.LabTeamService;
import com.infernokun.amaterasu.services.entity.ctf.RoomService;
import com.infernokun.amaterasu.websocket.RoomActivityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static com.infernokun.amaterasu.utils.AmaterasuConstants.buildSuccessResponse;

/**
 * A lab's team setup: which teams count (site, none or the lab's own) and the lab's own teams. Admins
 * and the lab's facilitators only; SecurityConfig keeps everyone else out and {@link CurrentUser#requireCanRun}
 * keeps facilitators to their labs.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/room/{roomId}/teams")
public class LabTeamController {
    private final RoomService roomService;
    private final LabTeamService labTeamService;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public record TeamModeRequest(TeamMode teamMode) {
    }

    public record LabTeamRequest(String name, String description) {
    }

    public record MembersRequest(List<String> userIds) {
    }

    @GetMapping
    public ResponseEntity<ApiResponse<LabTeamsView>> view(@PathVariable String roomId) {
        return buildSuccessResponse("Lab teams", labTeamService.view(runnable(roomId)), HttpStatus.OK);
    }

    /** Everyone who can be put on a lab team, with the teams they are on now. */
    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<List<LabTeamsView.Candidate>>> candidates(@PathVariable String roomId) {
        return buildSuccessResponse("People", labTeamService.candidates(runnable(roomId)), HttpStatus.OK);
    }

    @PutMapping("/mode")
    public ResponseEntity<ApiResponse<LabTeamsView>> mode(@PathVariable String roomId, @RequestBody TeamModeRequest request) {
        Room room = runnable(roomId);
        if (request == null || request.teamMode() == null) {
            throw new IllegalArgumentException("Pick SITE, NONE or LAB");
        }
        TeamMode before = room.effectiveTeamMode();
        roomService.changeTeamMode(room, request.teamMode());
        room.setUpdatedAt(LocalDateTime.now());
        room = roomService.saveRoom(room);
        if (before != room.effectiveTeamMode()) {
            auditService.record("LAB_TEAM_MODE_CHANGED", "ROOM", room.getId(), room.getName(), null,
                    before + " to " + room.effectiveTeamMode());
            events.publishEvent(new RoomActivityEvent(room.getId()));
        }
        return buildSuccessResponse("Team mode saved", labTeamService.view(room), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LabTeamsView>> create(@PathVariable String roomId, @RequestBody LabTeamRequest request) {
        Room room = runnable(roomId);
        Team team = labTeamService.create(room, request == null ? null : request.name(), request == null ? null : request.description());
        auditService.record("LAB_TEAM_CREATED", "ROOM", room.getId(), room.getName(), team.getId(), team.getName());
        return changed(room, "Team created", HttpStatus.CREATED);
    }

    @PutMapping("/{teamId}")
    public ResponseEntity<ApiResponse<LabTeamsView>> edit(@PathVariable String roomId, @PathVariable String teamId,
                                                          @RequestBody LabTeamRequest request) {
        Room room = runnable(roomId);
        String before = labTeamService.requireLabTeam(room, teamId).getName();
        Team team = labTeamService.edit(room, teamId, request == null ? null : request.name(),
                request == null ? null : request.description());
        auditService.record("LAB_TEAM_UPDATED", "ROOM", room.getId(), room.getName(), team.getId(),
                before.equals(team.getName()) ? team.getName() : before + " renamed " + team.getName());
        return changed(room, "Team saved", HttpStatus.OK);
    }

    @DeleteMapping("/{teamId}")
    public ResponseEntity<ApiResponse<LabTeamsView>> delete(@PathVariable String roomId, @PathVariable String teamId) {
        Room room = runnable(roomId);
        Team team = labTeamService.delete(room, teamId);
        auditService.record("LAB_TEAM_DELETED", "ROOM", room.getId(), room.getName(), team.getId(), team.getName());
        return changed(room, "Team deleted", HttpStatus.OK);
    }

    /** Puts people on the team; anyone on another team in this lab moves over. */
    @PostMapping("/{teamId}/members")
    public ResponseEntity<ApiResponse<LabTeamsView>> addMembers(@PathVariable String roomId, @PathVariable String teamId,
                                                                @RequestBody MembersRequest request) {
        Room room = runnable(roomId);
        Team team = labTeamService.requireLabTeam(room, teamId);
        List<User> added = labTeamService.addMembers(room, teamId, request == null ? null : request.userIds());
        if (!added.isEmpty()) {
            auditService.record("LAB_TEAM_MEMBERS_CHANGED", "ROOM", room.getId(), room.getName(), team.getId(),
                    "Added " + String.join(", ", added.stream().map(User::getUsername).toList()) + " to " + team.getName());
        }
        return changed(room, "Members added", HttpStatus.OK);
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<ApiResponse<LabTeamsView>> removeMember(@PathVariable String roomId, @PathVariable String teamId,
                                                                  @PathVariable String userId) {
        Room room = runnable(roomId);
        Team team = labTeamService.requireLabTeam(room, teamId);
        User removed = labTeamService.removeMember(room, teamId, userId);
        auditService.record("LAB_TEAM_MEMBERS_CHANGED", "ROOM", room.getId(), room.getName(), team.getId(),
                "Took " + removed.getUsername() + " off " + team.getName());
        return changed(room, "Member removed", HttpStatus.OK);
    }

    /** Seeds the lab's teams from the site teams, by name; people already on a lab team stay put. */
    @PostMapping("/copy-site")
    public ResponseEntity<ApiResponse<LabTeamsView>> copySite(@PathVariable String roomId) {
        Room room = runnable(roomId);
        LabTeamService.CopyResult result = labTeamService.copyFromSite(room);
        auditService.record("LAB_TEAMS_COPIED", "ROOM", room.getId(), room.getName(), null,
                result.teamsCreated() + " teams made, " + result.membersAdded() + " people added, "
                        + result.alreadyOnATeam() + " already on a team");
        return changed(room, result.teamsCreated() + " teams made, " + result.membersAdded() + " people added"
                + (result.alreadyOnATeam() > 0 ? ", " + result.alreadyOnATeam() + " kept their team" : ""), HttpStatus.OK);
    }

    private ResponseEntity<ApiResponse<LabTeamsView>> changed(Room room, String message, HttpStatus status) {
        events.publishEvent(new RoomActivityEvent(room.getId()));
        return buildSuccessResponse(message, labTeamService.view(room), status);
    }

    private Room runnable(String roomId) {
        Room room = roomService.findByRoomId(roomId);
        currentUser.requireCanRun(room);
        return room;
    }
}
