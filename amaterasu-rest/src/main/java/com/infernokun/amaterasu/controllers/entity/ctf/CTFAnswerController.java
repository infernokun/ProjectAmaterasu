package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityAnswer;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.ctf.dto.*;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.services.entity.ctf.*;
import com.infernokun.amaterasu.services.entity.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final CTFEntityService ctfEntityService;
    private final ModelMapper modelMapper;

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<CTFEntityAnswerResponse>> checkChallengeStatus(
            @RequestParam @NotBlank String roomId,
            @RequestParam @NotBlank String ctfEntityId) {

        final String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findUserById(userId);
        Room room = roomService.findByRoomId(roomId);
        CTFEntity ctfEntity = ctfEntityService.findCTFEntityById(ctfEntityId);

        Optional<RoomUser> roomUserOpt = roomUserService.findByUserAndRoom(user, room);
        if (roomUserOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        CTFEntityAnswer challengeStatus = ctfAnswerService
                .findByRoomUserIdAndCtfEntityId(roomUserOpt.get(), ctfEntity);

        CTFEntityAnswerResponse ctfEntityAnswerResponse = modelMapper.map(challengeStatus, CTFEntityAnswerResponse.class);

        List<CTFEntityHintUsageResponse> hintUsageResponseList = challengeStatus.getHintUsages()
                .stream()
                .map(hintUsage -> modelMapper.map(hintUsage, CTFEntityHintUsageResponse.class))
                .collect(Collectors.toList());

        ctfEntityAnswerResponse.setHintUsages(hintUsageResponseList);

        ctfEntityAnswerResponse.setJoinRoomResponse(JoinRoomResponse.builder()
                .roomId(roomId)
                .points(roomUserOpt.get().getPoints())
                .userId(roomUserOpt.get().getUser().getId())
                .roomUserStatus(roomUserOpt.get().getRoomUserStatus())
                .build());

        return buildSuccessResponse("Challenge status retrieved", ctfEntityAnswerResponse, HttpStatus.OK);
    }

    @GetMapping("/check/{roomId}")
    public ResponseEntity<ApiResponse<List<CTFEntityAnswerResponse>>> checkRoomUserChallengeData(
            @PathVariable @NotBlank String roomId) {
        final String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findUserById(userId);
        Room room = roomService.findByRoomId(roomId);
        List<CTFEntity> ctfEntities = ctfEntityService.findCTFEntitiesByRoomId(roomId);

        Optional<RoomUser> roomUserOpt = roomUserService.findByUserAndRoom(user, room);
        if (roomUserOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Get all challenge answers for this room user
        List<CTFEntityAnswer> challengeAnswers = ctfAnswerService.findByRoomUserId(roomUserOpt.get());

        // Convert each CTFEntityAnswer to CTFEntityAnswerResponse
        List<CTFEntityAnswerResponse> ctfEntityAnswerResponses = challengeAnswers.stream()
                .map(challengeAnswer -> {
                    CTFEntityAnswerResponse response = modelMapper.map(challengeAnswer, CTFEntityAnswerResponse.class);
                    response.setCtfEntityId(challengeAnswer.getCtfEntity().getId());

                    // Map hint usages for each challenge answer
                    List<CTFEntityHintUsageResponse> hintUsageResponseList = challengeAnswer.getHintUsages()
                            .stream()
                            .map(hintUsage -> modelMapper.map(hintUsage, CTFEntityHintUsageResponse.class))
                            .collect(Collectors.toList());
                    response.setHintUsages(hintUsageResponseList);

                    // Set room user info (this will be the same for all responses)
                    response.setJoinRoomResponse(JoinRoomResponse.builder()
                            .roomId(roomId)
                            .points(roomUserOpt.get().getPoints())
                            .userId(roomUserOpt.get().getUser().getId())
                            .roomUserStatus(roomUserOpt.get().getRoomUserStatus())
                            .build());

                    return response;
                }).collect(Collectors.toList());

        return buildSuccessResponse("All challenge statuses retrieved", ctfEntityAnswerResponses, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CTFEntityAnswerResponse>> answerQuestion(
            @RequestBody @Valid CTFEntityAnswerRequest ctfEntityAnswerRequest) {

        final String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        boolean isAnswerCorrect = flagService.validateFlag(ctfEntityAnswerRequest);
        CTFEntityAnswerResponse response = flagService
                .addAnsweredCTFEntity(userId, ctfEntityAnswerRequest, isAnswerCorrect);

        String message = isAnswerCorrect ? "Correct answer!" : "Incorrect answer";
        return buildSuccessResponse(message, response, HttpStatus.OK);
    }
}
