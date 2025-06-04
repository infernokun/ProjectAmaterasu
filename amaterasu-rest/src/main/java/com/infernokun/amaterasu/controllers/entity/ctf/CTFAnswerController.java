package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.ctf.dto.CTFAnswerRequest;
import com.infernokun.amaterasu.models.entities.ctf.dto.AnsweredCTFEntityResponse;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFAnswer;
import com.infernokun.amaterasu.services.entity.ctf.CTFAnswerService;
import com.infernokun.amaterasu.services.entity.ctf.FlagService;
import com.infernokun.amaterasu.services.entity.UserService;
import com.infernokun.amaterasu.services.entity.ctf.RoomService;
import com.infernokun.amaterasu.services.entity.ctf.RoomUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import static com.infernokun.amaterasu.utils.AmaterasuConstants.buildSuccessResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/answer")
public class CTFAnswerController {
    private final FlagService flagService;
    private final CTFAnswerService ctfAnswerService;
    private final UserService userService;
    private final RoomUserService roomUserService;
    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<ApiResponse<AnsweredCTFEntityResponse>> answerQuestion(@RequestBody CTFAnswerRequest ctfAnswerRequest) {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (ctfAnswerRequest == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        User user = userService.findUserById(ctfAnswerRequest.getUserId());

        if (!user.getId().equals(authentication.getName())) {
            // If usernames don't match, return unauthorized
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean isAnswerCorrect = this.flagService.validateFlag(ctfAnswerRequest);

        return buildSuccessResponse("Got some answer", flagService
                .addAnsweredCTFEntity(authentication.getName(), ctfAnswerRequest, isAnswerCorrect), HttpStatus.OK);
    }

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<CTFAnswer>> checkChallengeStatus(
            @RequestParam String roomId,
            @RequestParam String ctfEntityId) {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        User user = this.userService.findUserById(authentication.getName());
        Room room = roomService.findByRoomId(roomId);

        Optional<RoomUser> roomUserOpt = roomUserService.findByUserAndRoom(user, room);
        return buildSuccessResponse("Got some answer", ctfAnswerService
                .findByRoomUserIdAndCtfEntityId(roomUserOpt.get().getId(), ctfEntityId), HttpStatus.OK);
    }
}
