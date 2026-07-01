package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.models.entities.ctf.dto.*;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.*;
import com.infernokun.amaterasu.repositories.ctf.FlagRepository;
import com.infernokun.amaterasu.services.entity.UserService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlagService {
    private final CTFEntityService ctfEntityService;
    private final UserService userService;
    private final CTFAnswerService ctfAnswerService;
    private final FlagRepository flagRepository;
    private final ModelMapper modelMapper;
    private final RoomUserService roomUserService;
    private final RoomService roomService;

    public Flag saveFlag(Flag flag) {
        return this.flagRepository.save(flag);
    }

    public boolean validateFlag(CTFEntityAnswerRequest ctfEntityAnswerRequest) {
        List<Flag> challengeFlags = getFlagsByCtfEntityId(ctfEntityAnswerRequest.getQuestionId());

        // Trim to tolerate copy-paste whitespace, and honour each flag's caseSensitive
        // setting (previously always case-insensitive, ignoring the field entirely).
        String submitted = ctfEntityAnswerRequest.getFlag() == null
                ? "" : ctfEntityAnswerRequest.getFlag().trim();

        return challengeFlags.stream().anyMatch(flag -> {
            String expected = flag.getFlag() == null ? "" : flag.getFlag().trim();
            return Boolean.TRUE.equals(flag.getCaseSensitive())
                    ? expected.equals(submitted)
                    : expected.equalsIgnoreCase(submitted);
        });
    }

    @Transactional
    public CTFEntityAnswerResponse addAnsweredCTFEntity(String userId, CTFEntityAnswerRequest ctfEntityAnswerRequest,
                                                        boolean correct) {
        // Fetch required entities
        User user = userService.findUserById(userId);
        Room room = roomService.findByRoomId(ctfEntityAnswerRequest.getRoomId());
        CTFEntity ctfEntity = ctfEntityService.findCTFEntityByIdWithFlags(ctfEntityAnswerRequest.getQuestionId());

        // Verify user is in the room
        RoomUser roomUser = roomUserService.findByUserAndRoom(user, room)
                .orElseThrow(() -> new IllegalStateException("User is not a member of this room"));

        // Get or create answer record - FIX: Use roomUser.getId() instead of user.getId()
        RoomUser finalRoomUser = roomUser;
        CTFEntityAnswer ctfEntityAnswer = ctfAnswerService
                .findByRoomUserIdAndCtfEntityIdOptional(roomUser.getId(), ctfEntity.getId())
                .orElseGet(() -> CTFEntityAnswer
                        .builder()
                        .roomUser(finalRoomUser)
                        .ctfEntity(ctfEntity)
                        .correct(false)
                        .answers(new ArrayList<>())
                        .attemptTimes(new ArrayList<>())
                        .attempts(0)
                        .build());

        // Update answer record
        ctfEntityAnswer.getAnswers().add(ctfEntityAnswerRequest);
        ctfEntityAnswer.getAttemptTimes().add(LocalDateTime.now());
        ctfEntityAnswer.setAttempts(ctfEntityAnswer.getAttempts() + 1);

        // Set solved timestamp if correct and not already solved
        if (correct && ctfEntityAnswer.getSolvedAt() == null) {
            ctfEntityAnswer.setSolvedAt(LocalDateTime.now());
        }

        // Update points only if answer is correct and not already solved
        if (correct && !ctfEntityAnswer.getCorrect()) { // First correct attempt
            Integer newPoints = roomUser.getPoints() + ctfEntity.getPoints();
            roomUser.updatePoints(newPoints, "answered correctly");
            ctfEntityAnswer.setScore(ctfEntity.getPoints());
            roomUser = roomUserService.save(roomUser);
        }

        // Never downgrade an already-solved challenge back to unsolved. Previously this
        // unconditionally wrote the current attempt's correctness, so a wrong answer after
        // solving flipped correct=false and the next correct answer re-awarded points
        // (infinite points). Only ever upgrade to solved.
        if (correct) {
            ctfEntityAnswer.setCorrect(true);
        }
        ctfEntityAnswer = ctfAnswerService.saveAnsweredCTFEntity(ctfEntityAnswer);

        // Build response
        CTFEntityAnswerResponse response = modelMapper.map(ctfEntityAnswer, CTFEntityAnswerResponse.class);
        List<CTFEntityHintUsageResponse> hintUsageResponseList = ctfEntityAnswer.getHintUsages()
                .stream()
                .map(hintUsage -> modelMapper.map(hintUsage, CTFEntityHintUsageResponse.class))
                .collect(Collectors.toList());

        response.setHintUsages(hintUsageResponseList);

        response.setJoinRoomResponse(JoinRoomResponse.builder()
                .roomId(ctfEntityAnswerRequest.getRoomId())
                .points(roomUser.getPoints())
                .userId(roomUser.getUser().getId())
                .roomUserStatus(roomUser.getRoomUserStatus())
                .build());

        return response;
    }

    public List<Flag> getFlagsByCtfEntityId(String ctfEntityId) {
        return flagRepository.getFlagsByCtfEntityId(ctfEntityId);
    }
}
