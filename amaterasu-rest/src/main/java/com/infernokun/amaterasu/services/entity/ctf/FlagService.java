package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.components.TeamResolver;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.ctf.dto.*;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomOverview.ChallengeState;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import com.infernokun.amaterasu.models.enums.TeamCredit;
import com.infernokun.amaterasu.websocket.RoomActivityEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.infernokun.amaterasu.models.entities.ctf.*;
import com.infernokun.amaterasu.repositories.ctf.FlagRepository;
import com.infernokun.amaterasu.services.entity.AuditService;
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
    private final ApplicationEventPublisher events;
    private final AuditService auditService;
    private final CurrentUser currentUser;
    private final TeamResolver teamResolver;

    public Flag saveFlag(Flag flag) {
        return this.flagRepository.save(flag);
    }

    /**
     * A flag matches as written, trimmed, honouring its case setting. A flag marked surroundWithTag
     * also matches when the player wraps it in the lab's flag wrapper, e.g. CTF{answer} for "answer".
     */
    public boolean validateFlag(CTFEntityAnswerRequest ctfEntityAnswerRequest) {
        List<Flag> challengeFlags = getFlagsByCtfEntityId(ctfEntityAnswerRequest.getQuestionId());

        String submitted = ctfEntityAnswerRequest.getFlag() == null
                ? "" : ctfEntityAnswerRequest.getFlag().trim();
        String[] wrapper = challengeFlags.stream().anyMatch(flag -> Boolean.TRUE.equals(flag.getSurroundWithTag()))
                ? wrapperOf(ctfEntityAnswerRequest.getQuestionId()) : null;

        return challengeFlags.stream().anyMatch(flag -> {
            String expected = flag.getFlag() == null ? "" : flag.getFlag().trim();
            if (matches(expected, submitted, flag)) {
                return true;
            }
            return wrapper != null && Boolean.TRUE.equals(flag.getSurroundWithTag())
                    && matches(wrapper[0] + expected + wrapper[1], submitted, flag);
        });
    }

    private static boolean matches(String expected, String submitted, Flag flag) {
        return Boolean.TRUE.equals(flag.getCaseSensitive()) ? expected.equals(submitted) : expected.equalsIgnoreCase(submitted);
    }

    /** The text before and after the answer in the lab's wrapper: "CTF{x}" gives "CTF{" and "}". */
    private String[] wrapperOf(String questionId) {
        CTFEntity challenge = ctfEntityService.findCTFEntityByIdWithFlags(questionId);
        return wrapperParts(challenge != null && challenge.getRoom() != null ? challenge.getRoom().getSurroundTag() : null);
    }

    /** A lab's flag format split around the answer: "CTF{x}" gives "CTF{" and "}"; null when it has none. */
    public static String[] wrapperParts(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        java.util.regex.Matcher parts = java.util.regex.Pattern.compile("^(.*?)(?:x|\\?)(\\W*)$").matcher(tag.trim());
        return parts.matches() ? new String[]{parts.group(1), parts.group(2)} : new String[]{tag.trim() + "{", "}"};
    }

    /** Whether a submitted answer matches one flag, as answering checks it. */
    public static boolean accepts(Flag flag, String submitted, String surroundTag) {
        String answer = submitted == null ? "" : submitted.trim();
        String expected = flag.getFlag() == null ? "" : flag.getFlag().trim();
        if (matches(expected, answer, flag)) {
            return true;
        }
        String[] wrapper = Boolean.TRUE.equals(flag.getSurroundWithTag()) ? wrapperParts(surroundTag) : null;
        return wrapper != null && matches(wrapper[0] + expected + wrapper[1], answer, flag);
    }

    @Transactional
    public CTFEntityAnswerResponse addAnsweredCTFEntity(String userId, CTFEntityAnswerRequest ctfEntityAnswerRequest,
                                                        boolean correct) {
        // Fetch required entities
        User user = userService.findUserById(userId);
        Room room = roomService.findByRoomId(ctfEntityAnswerRequest.getRoomId());
        CTFEntity ctfEntity = ctfEntityService.findCTFEntityByIdWithFlags(ctfEntityAnswerRequest.getQuestionId());

        currentUser.requireCanSee(room);
        if (ctfEntity.getRoom() == null || !room.getId().equals(ctfEntity.getRoom().getId())) {
            throw new IllegalArgumentException("That question is not in this lab");
        }
        if (roomUserService.findByUserAndRoom(user, room).map(seat -> seat.getRoomUserStatus() == RoomUserStatus.KICKED
                || seat.getRoomUserStatus() == RoomUserStatus.BANNED).orElse(false)) {
            throw new IllegalStateException("You can no longer answer in this lab");
        }
        RoomUser roomUser = roomUserService.seatFor(user, room);
        ChallengeState state = RoomPlayService.stateOf(ctfEntity, LocalDateTime.now());
        if (state == ChallengeState.LOCKED) {
            throw new IllegalStateException("This challenge is not open yet");
        }
        if (state == ChallengeState.EXPIRED) {
            throw new IllegalStateException("This challenge has closed");
        }
        if (room.effectiveTeamCredit() == TeamCredit.SHARED_SOLVES) {
            ctfAnswerService.solvedByTeammate(roomUser, ctfEntity).ifPresent(answer -> {
                throw new IllegalStateException(answer.getRoomUser().getUser().getUsername()
                        + " already solved this for your team");
            });
        }

        // Get or create answer record
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

        if (!ctfEntityAnswer.getCorrect() && ctfEntityAnswer.isMaxAttemptsReached()) {
            throw new IllegalStateException("Maximum attempts reached");
        }

        // Update answer record; who and when come from the server, as does whether it was right
        ctfEntityAnswerRequest.setUserId(user.getId());
        ctfEntityAnswerRequest.setSubmissionTime(LocalDateTime.now());
        ctfEntityAnswerRequest.setCorrect(correct);
        ctfEntityAnswerRequest.setCreditedBy(null);
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
            Team team = teamResolver.teamOf(room, user);
            auditService.record("FLAG_SOLVED", "CHALLENGE", ctfEntity.getId(), ctfEntity.getQuestion(), team != null ? team.getId() : null, ctfEntity.getPoints() + " pts in " + room.getName());
        }

        // Never downgrade a solved challenge
        if (correct) {
            ctfEntityAnswer.setCorrect(true);
        }
        ctfEntityAnswer = ctfAnswerService.saveAnsweredCTFEntity(ctfEntityAnswer);
        events.publishEvent(new RoomActivityEvent(room.getId()));

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
