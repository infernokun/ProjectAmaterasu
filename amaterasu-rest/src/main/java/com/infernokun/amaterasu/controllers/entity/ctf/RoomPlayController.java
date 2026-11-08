package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.LabActionResult;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.EnvironmentView;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.LabCard;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomLive;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomOverview;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomStandings;
import com.infernokun.amaterasu.services.entity.ctf.RoomPlayService;
import com.infernokun.amaterasu.services.entity.ctf.RoomService;
import com.infernokun.amaterasu.services.entity.lab.LabEnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.infernokun.amaterasu.utils.AmaterasuConstants.buildSuccessResponse;

/**
 * Lab views for players, the scoreboard and facilitators, and running a lab's environment. SecurityConfig
 * limits /live to staff; a facilitator only sees labs they facilitate.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/room")
public class RoomPlayController {
    private final RoomPlayService roomPlayService;
    private final CurrentUser currentUser;
    private final RoomService roomService;
    private final LabEnvironmentService labEnvironmentService;

    /** How an environment action went, with the environment as it is now. */
    public record EnvironmentActionResult(boolean successful, String message, EnvironmentView environment) {
    }

    public record StartRequest(String remoteServerId) {
    }

    @GetMapping("/cards")
    public ResponseEntity<ApiResponse<List<LabCard>>> cards() {
        return buildSuccessResponse("Labs", roomPlayService.cards(currentUser.id()), HttpStatus.OK);
    }

    @GetMapping("/{roomId}/overview")
    public ResponseEntity<ApiResponse<RoomOverview>> overview(@PathVariable String roomId) {
        return buildSuccessResponse("Room overview", roomPlayService.overview(roomId, currentUser.id()), HttpStatus.OK);
    }

    @GetMapping("/{roomId}/environment")
    public ResponseEntity<ApiResponse<EnvironmentView>> environment(@PathVariable String roomId) {
        return buildSuccessResponse("Environment", roomPlayService.environment(roomId, currentUser.id()), HttpStatus.OK);
    }

    /** Starts or resumes the shared copy (its facilitators and admins) or the caller's team's copy. */
    @PostMapping("/{roomId}/environment/start")
    public ResponseEntity<ApiResponse<EnvironmentActionResult>> start(@PathVariable String roomId,
                                                                      @RequestBody(required = false) StartRequest request) {
        Room room = roomService.findByRoomId(roomId);
        return acted(roomId, labEnvironmentService.start(room, request == null ? null : request.remoteServerId()),
                "Environment started", "The environment did not start");
    }

    @PostMapping("/{roomId}/environment/pause")
    public ResponseEntity<ApiResponse<EnvironmentActionResult>> pause(@PathVariable String roomId) {
        return acted(roomId, labEnvironmentService.pause(roomService.findByRoomId(roomId)),
                "Environment paused", "The environment did not pause");
    }

    @PostMapping("/{roomId}/environment/stop")
    public ResponseEntity<ApiResponse<EnvironmentActionResult>> stop(@PathVariable String roomId) {
        return acted(roomId, labEnvironmentService.stop(roomService.findByRoomId(roomId)),
                "Environment stopped", "The environment did not stop");
    }

    private ResponseEntity<ApiResponse<EnvironmentActionResult>> acted(String roomId, LabActionResult result,
                                                                        String done, String failed) {
        String output = result.getOutput() == null || result.getOutput().isBlank() ? null : result.getOutput().trim();
        String message = result.isSuccessful() ? done : output != null ? output : failed;
        HttpStatus status = result.isSuccessful() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return buildSuccessResponse(message, new EnvironmentActionResult(result.isSuccessful(), message,
                roomPlayService.environment(roomId, currentUser.id())), status);
    }

    @GetMapping("/{roomId}/standings")
    public ResponseEntity<ApiResponse<RoomStandings>> standings(@PathVariable String roomId) {
        return buildSuccessResponse("Room standings", roomPlayService.standings(roomId), HttpStatus.OK);
    }

    @GetMapping("/{roomId}/live")
    public ResponseEntity<ApiResponse<RoomLive>> live(@PathVariable String roomId) {
        currentUser.requireCanRun(roomService.findByRoomId(roomId));
        return buildSuccessResponse("Live room summary", roomPlayService.live(roomId), HttpStatus.OK);
    }
}
