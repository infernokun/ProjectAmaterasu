package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityAnswer;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.ctf.TeamPointsAward;
import com.infernokun.amaterasu.models.entities.ctf.dto.CTFEntityAnswerRequest;
import com.infernokun.amaterasu.models.entities.ctf.dto.manage.LabManage;
import com.infernokun.amaterasu.repositories.ctf.CTFAnswerRepository;
import com.infernokun.amaterasu.services.entity.ctf.LabManageService;
import com.infernokun.amaterasu.services.entity.ctf.RoomPointsService;
import com.infernokun.amaterasu.services.entity.ctf.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.infernokun.amaterasu.utils.AmaterasuConstants.buildSuccessResponse;

/**
 * Managing one lab: what happened in it and the fixes staff make to its scores. Admins and the lab's
 * facilitators only; SecurityConfig keeps everyone else out and {@link CurrentUser#requireCanRun} keeps
 * facilitators to their labs. Every action is audited with who did it and why.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/room/{roomId}/manage")
public class LabManageController {
    private final RoomService roomService;
    private final LabManageService labManageService;
    private final RoomPointsService roomPointsService;
    private final CTFAnswerRepository answerRepository;
    private final CurrentUser currentUser;

    /**
     * Credit for a question: either {@code submissionId} ("answerId:index", from the submissions list)
     * or {@code userId} and {@code questionId}. {@code points} defaults to the question's.
     */
    public record CreditRequest(String submissionId, String userId, String questionId, Integer points, String reason) {
    }

    /**
     * Accepts an answer as another flag: {@code answer}, or the answer given in {@code submissionId}.
     * With {@code creditExisting}, everyone who submitted it gets the question.
     */
    public record AcceptRequest(String questionId, String answer, String submissionId, Boolean caseSensitive,
                                Boolean creditExisting, String reason) {
    }

    /** Points for a player ({@code userId}) or a team as a whole ({@code teamId}). */
    public record AwardRequest(String userId, String teamId, Integer points, String reason) {
    }

    /** Resets one question, or the whole lab when {@code questionId} is left out. {@code confirm} must be true. */
    public record ResetRequest(String userId, String questionId, String reason, Boolean confirm) {
    }

    /** What an award did: the player's new total, or the team award. */
    public record Awarded(String userId, String teamId, int points, Integer total) {
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<LabManage.Overview>> overview(@PathVariable String roomId) {
        return buildSuccessResponse("Lab overview", labManageService.overview(runnable(roomId)), HttpStatus.OK);
    }

    @GetMapping("/submissions")
    public ResponseEntity<ApiResponse<LabManage.SubmissionPage>> submissions(@PathVariable String roomId,
                                                                            @RequestParam(required = false) String userId,
                                                                            @RequestParam(required = false) String teamId,
                                                                            @RequestParam(required = false) String questionId,
                                                                            @RequestParam(required = false) String result,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "50") int size) {
        return buildSuccessResponse("Submissions", labManageService.submissions(runnable(roomId), userId, teamId, questionId,
                result, page, size), HttpStatus.OK);
    }

    @GetMapping("/players")
    public ResponseEntity<ApiResponse<LabManage.Players>> players(@PathVariable String roomId) {
        return buildSuccessResponse("Players", labManageService.players(runnable(roomId)), HttpStatus.OK);
    }

    @GetMapping("/players/{userId}")
    public ResponseEntity<ApiResponse<LabManage.PlayerDetail>> player(@PathVariable String roomId, @PathVariable String userId) {
        return buildSuccessResponse("Player", labManageService.player(runnable(roomId), userId), HttpStatus.OK);
    }

    @GetMapping("/questions")
    public ResponseEntity<ApiResponse<List<LabManage.QuestionStats>>> questions(@PathVariable String roomId) {
        return buildSuccessResponse("Questions", labManageService.questions(runnable(roomId)), HttpStatus.OK);
    }

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<List<LabManage.ActivityItem>>> activity(@PathVariable String roomId,
                                                                             @RequestParam(defaultValue = "200") int limit) {
        return buildSuccessResponse("Activity", labManageService.activity(runnable(roomId), limit), HttpStatus.OK);
    }

    @PostMapping("/credit")
    public ResponseEntity<ApiResponse<RoomPointsService.Credited>> credit(@PathVariable String roomId,
                                                                         @RequestBody CreditRequest request) {
        Room room = runnable(roomId);
        if (request == null) {
            throw new IllegalArgumentException("Say who to credit and for which question");
        }
        String reason = RoomPointsService.requireReason(request.reason());
        User user;
        CTFEntity question;
        Integer attempt = null;
        if (request.submissionId() != null && !request.submissionId().isBlank()) {
            Located located = locate(room, request.submissionId());
            user = located.answer().getRoomUser().getUser();
            question = located.answer().getCtfEntity();
            attempt = located.index();
        } else {
            if (request.userId() == null || request.questionId() == null) {
                throw new IllegalArgumentException("Give a submission, or a player and a question");
            }
            user = roomPointsService.requireUser(request.userId());
            question = roomPointsService.requireQuestion(room, request.questionId());
        }
        RoomPointsService.Credited credited = roomPointsService.credit(room, user, question, request.points(), reason, attempt);
        return buildSuccessResponse(credited.username() + " gets " + credited.points() + " pts for " + question.getQuestion()
                + (credited.team() != null ? ", and it counts for " + credited.team() : ""), credited, HttpStatus.OK);
    }

    @PostMapping("/accept-answer")
    public ResponseEntity<ApiResponse<RoomPointsService.Accepted>> acceptAnswer(@PathVariable String roomId,
                                                                               @RequestBody AcceptRequest request) {
        Room room = runnable(roomId);
        if (request == null) {
            throw new IllegalArgumentException("Say which answer to accept");
        }
        String reason = RoomPointsService.requireReason(request.reason());
        CTFEntity question;
        String answer = request.answer();
        if (request.submissionId() != null && !request.submissionId().isBlank()) {
            Located located = locate(room, request.submissionId());
            question = located.answer().getCtfEntity();
            if (answer == null || answer.isBlank()) {
                answer = located.attempt().getFlag();
            }
        } else {
            if (request.questionId() == null) {
                throw new IllegalArgumentException("Give a submission, or a question and an answer");
            }
            question = roomPointsService.requireQuestion(room, request.questionId());
        }
        RoomPointsService.Accepted accepted = roomPointsService.acceptAnswer(room, question, answer, request.caseSensitive(),
                Boolean.TRUE.equals(request.creditExisting()), reason);
        int count = accepted.credited().size();
        return buildSuccessResponse("Accepted \"" + accepted.flag() + "\""
                + (Boolean.TRUE.equals(request.creditExisting()) ? "; " + count + (count == 1 ? " player" : " players") + " credited" : ""),
                accepted, HttpStatus.OK);
    }

    @PostMapping("/award")
    public ResponseEntity<ApiResponse<Awarded>> award(@PathVariable String roomId, @RequestBody AwardRequest request) {
        Room room = runnable(roomId);
        if (request == null) {
            throw new IllegalArgumentException("Say who gets the points");
        }
        int points = RoomPointsService.requireAward(request.points());
        String reason = RoomPointsService.requireReason(request.reason());
        boolean toUser = request.userId() != null && !request.userId().isBlank();
        boolean toTeam = request.teamId() != null && !request.teamId().isBlank();
        if (toUser == toTeam) {
            throw new IllegalArgumentException("Award points to a player or to a team, not both");
        }
        if (toUser) {
            RoomUser roomUser = roomPointsService.award(room, roomPointsService.requireUser(request.userId()), points, reason);
            return buildSuccessResponse(signed(points) + " pts to " + roomUser.getUser().getUsername(),
                    new Awarded(roomUser.getUser().getId(), null, points, roomUser.getPoints()), HttpStatus.OK);
        }
        TeamPointsAward award = roomPointsService.awardTeam(room, request.teamId(), points, reason);
        return buildSuccessResponse(signed(points) + " pts to " + award.getTeamName(),
                new Awarded(null, award.getTeamId(), points, null), HttpStatus.OK);
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<RoomPointsService.Reset>> reset(@PathVariable String roomId, @RequestBody ResetRequest request) {
        Room room = runnable(roomId);
        if (request == null || request.userId() == null) {
            throw new IllegalArgumentException("Say whose progress to reset");
        }
        if (!Boolean.TRUE.equals(request.confirm())) {
            throw new IllegalArgumentException("Resetting progress cannot be undone. Send confirm: true to go ahead.");
        }
        String reason = RoomPointsService.requireReason(request.reason());
        User user = roomPointsService.requireUser(request.userId());
        CTFEntity question = request.questionId() == null || request.questionId().isBlank() ? null
                : roomPointsService.requireQuestion(room, request.questionId());
        RoomPointsService.Reset reset = roomPointsService.reset(room, user, question, reason);
        return buildSuccessResponse("Reset " + (question != null ? question.getQuestion() : "the whole lab") + " for "
                + user.getUsername(), reset, HttpStatus.OK);
    }

    private record Located(CTFEntityAnswer answer, int index, CTFEntityAnswerRequest attempt) {
    }

    /** An attempt by its "answerId:index", which must be in this lab. */
    private Located locate(Room room, String submissionId) {
        int colon = submissionId.lastIndexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("A submission id looks like answerId:index");
        }
        int index;
        try {
            index = Integer.parseInt(submissionId.substring(colon + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("A submission id looks like answerId:index");
        }
        CTFEntityAnswer answer = answerRepository.findById(submissionId.substring(0, colon))
                .filter(found -> found.getRoomUser() != null && found.getRoomUser().getRoom() != null
                        && room.getId().equals(found.getRoomUser().getRoom().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("No submission " + submissionId + " in " + room.getName()));
        if (index < 0 || answer.getAnswers() == null || index >= answer.getAnswers().size()) {
            throw new ResourceNotFoundException("No submission " + submissionId + " in " + room.getName());
        }
        return new Located(answer, index, answer.getAnswers().get(index));
    }

    private Room runnable(String roomId) {
        Room room = roomService.findByRoomId(roomId);
        currentUser.requireCanRun(room);
        return room;
    }

    private static String signed(int points) {
        return points > 0 ? "+" + points : String.valueOf(points);
    }
}
