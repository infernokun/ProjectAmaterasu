package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomLive;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomOverview;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomStandings;
import com.infernokun.amaterasu.services.entity.ctf.RoomPlayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.infernokun.amaterasu.utils.AmaterasuConstants.buildSuccessResponse;

/** Room views for players, the scoreboard and facilitators. SecurityConfig limits /live to staff. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/room")
public class RoomPlayController {
    private final RoomPlayService roomPlayService;
    private final CurrentUser currentUser;

    @GetMapping("/{roomId}/overview")
    public ResponseEntity<ApiResponse<RoomOverview>> overview(@PathVariable String roomId) {
        return buildSuccessResponse("Room overview", roomPlayService.overview(roomId, currentUser.id()), HttpStatus.OK);
    }

    @GetMapping("/{roomId}/standings")
    public ResponseEntity<ApiResponse<RoomStandings>> standings(@PathVariable String roomId) {
        return buildSuccessResponse("Room standings", roomPlayService.standings(roomId), HttpStatus.OK);
    }

    @GetMapping("/{roomId}/live")
    public ResponseEntity<ApiResponse<RoomLive>> live(@PathVariable String roomId) {
        return buildSuccessResponse("Live room summary", roomPlayService.live(roomId), HttpStatus.OK);
    }
}
