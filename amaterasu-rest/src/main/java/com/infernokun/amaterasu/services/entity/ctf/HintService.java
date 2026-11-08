package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomOverview.ChallengeState;
import com.infernokun.amaterasu.websocket.RoomActivityEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.infernokun.amaterasu.models.entities.ctf.*;
import com.infernokun.amaterasu.models.entities.ctf.dto.CTFEntityHintResponse;
import com.infernokun.amaterasu.models.entities.ctf.dto.JoinRoomResponse;
import com.infernokun.amaterasu.repositories.ctf.HintRepository;
import com.infernokun.amaterasu.services.entity.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class HintService {
    private final HintRepository hintRepository;
    private final CTFEntityService ctfEntityService;
    private final RoomService roomService;
    private final UserService userService;
    private final RoomUserService roomUserService;
    private final CTFAnswerService ctfAnswerService;
    private final ModelMapper modelMapper;
    private final ApplicationEventPublisher events;
    private final CurrentUser currentUser;

    public Hint getHintById(String hintId) {
        return hintRepository.findById(hintId).orElseThrow(()
                -> new ResourceNotFoundException("Hint not found"));
    }

    public Hint save(Hint hint) {
        return hintRepository.save(hint);
    }

    public CTFEntityHintResponse useHint(String hintId, String roomId, String userId, String ctfEntityId) {
        // Validate entities exist
        Room room = roomService.findByRoomId(roomId);
        User user = userService.findUserById(userId);
        CTFEntity ctfEntity = ctfEntityService.findCTFEntityById(ctfEntityId);
        Hint hint = getHintById(hintId);

        currentUser.requireCanSee(room);
        if (roomUserService.findByUserAndRoom(user, room).map(seat -> seat.getRoomUserStatus() == RoomUserStatus.KICKED
                || seat.getRoomUserStatus() == RoomUserStatus.BANNED).orElse(false)) {
            throw new IllegalStateException("You can no longer unlock hints in this lab");
        }

        // Validate hint belongs to the CTF entity
        validateHintBelongsToEntity(hint, ctfEntityId);
        if (ctfEntity.getRoom() == null || !ctfEntity.getRoom().getId().equals(room.getId())) {
            throw new IllegalArgumentException("That question is not in this lab");
        }
        if (RoomPlayService.stateOf(ctfEntity, LocalDateTime.now()) != ChallengeState.OPEN) {
            throw new IllegalStateException("Hints are only available while the challenge is open");
        }

        // Validate user has enough points
        RoomUser roomUser = roomUserService.findByUserAndRoom(user, room).orElse(null);
        if (roomUser == null && hint.getCost() != null && hint.getCost() > 0) {
            throw new IllegalStateException("Not enough points to use this hint");
        }
        roomUser = roomUser != null ? roomUser : roomUserService.seatFor(user, room);
        validateUserHasEnoughPoints(roomUser, hint);

        // Get or create CTF answer
        CTFEntityAnswer ctfEntityAnswer = ctfAnswerService.findByRoomUserIdAndCtfEntityId(roomUser, ctfEntity);

        // Check if hint already used
        validateHintNotAlreadyUsed(ctfEntityAnswer, hintId);

        int needed = hint.getUnlockAfterAttempts() == null ? 0 : hint.getUnlockAfterAttempts();
        if (ctfEntityAnswer.getAttempts() < needed) {
            throw new IllegalStateException("This hint opens after " + needed + " attempts");
        }

        // Process hint usage
        return processHintUsage(roomUser, hint, ctfEntityAnswer);
    }

    private void validateHintBelongsToEntity(Hint hint, String ctfEntityId) {
        if (!hint.getCtfEntity().getId().equals(ctfEntityId)) {
            throw new IllegalArgumentException("Hint does not belong to the specified CTF entity");
        }
    }

    private void validateUserHasEnoughPoints(RoomUser roomUser, Hint hint) {
        if (roomUser.getPoints() < hint.getCost()) {
            throw new IllegalStateException("Not enough points to use this hint");
        }
    }

    private void validateHintNotAlreadyUsed(CTFEntityAnswer ctfEntityAnswer, String hintId) {
        if (ctfEntityAnswer.getHintsUsed().stream().anyMatch(h -> h.getId().equals(hintId))) {
            throw new IllegalStateException("Hint has already been used by this user");
        }
    }

    private CTFEntityHintResponse processHintUsage(RoomUser roomUser, Hint hint, CTFEntityAnswer ctfEntityAnswer) {
        // Deduct points from user
        roomUser.updatePoints(roomUser.getPoints() - hint.getCost(), "hint used");
        roomUser = roomUserService.save(roomUser);

        CTFEntityHintUsage hintUsage = CTFEntityHintUsage.builder()
                .ctfEntityAnswer(ctfEntityAnswer)
                .hint(hint)
                .pointsDeducted(hint.getCost())
                .usageOrder(ctfEntityAnswer.getHintUsages().size() + 1)
                .usedAt(LocalDateTime.now())
                .build();

        ctfEntityAnswer.getHintUsages().add(hintUsage);

        ctfEntityAnswer = ctfAnswerService.saveAnsweredCTFEntity(ctfEntityAnswer);

        events.publishEvent(new RoomActivityEvent(roomUser.getRoom().getId()));
        return buildHintResponse(ctfEntityAnswer, roomUser, hint);
    }

    private CTFEntityHintResponse buildHintResponse(CTFEntityAnswer ctfEntityAnswer, RoomUser roomUser, Hint hint) {
        CTFEntityHintResponse response = modelMapper.map(ctfEntityAnswer, CTFEntityHintResponse.class);
        response.setJoinRoomResponse(JoinRoomResponse.builder()
                .points(roomUser.getPoints())
                .roomId(roomUser.getRoom().getId())
                .userId(roomUser.getUser().getId())
                .roomUserStatus(roomUser.getRoomUserStatus())
                .build());
        response.setRequestedHint(hint);
        return response;
    }

    // Helper class for statistics
    public record HintStatistics(long totalHints, long unlockedHints, long usedHints, int totalPointsDeducted) { }
}