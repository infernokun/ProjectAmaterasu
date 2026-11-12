package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.components.TeamResolver;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.PointsHistoryEntry;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityAnswer;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.ctf.TeamPointsAward;
import com.infernokun.amaterasu.models.entities.ctf.dto.CTFEntityAnswerRequest;
import com.infernokun.amaterasu.models.enums.TeamCredit;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.repositories.ctf.CTFAnswerRepository;
import com.infernokun.amaterasu.repositories.ctf.CTFEntityRepository;
import com.infernokun.amaterasu.repositories.ctf.FlagRepository;
import com.infernokun.amaterasu.repositories.ctf.TeamPointsAwardRepository;
import com.infernokun.amaterasu.services.entity.AuditService;
import com.infernokun.amaterasu.websocket.RoomActivityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What admins and a lab's facilitators change in a lab's scores: awards to a player or a team,
 * credit for a question, accepting another answer, and resetting progress. Each change is audited
 * with who made it and why, and open views reload over the websocket.
 */
@Service
@RequiredArgsConstructor
public class RoomPointsService {
    public static final int MAX_AWARD = 10_000;
    public static final int MAX_REASON = 200;
    public static final String AWARD = "AWARD";
    public static final String CREDIT = "CREDIT";
    public static final String RESET = "RESET";

    private final RoomUserService roomUserService;
    private final CTFAnswerRepository answerRepository;
    private final CTFEntityRepository ctfEntityRepository;
    private final FlagRepository flagRepository;
    private final TeamPointsAwardRepository teamAwards;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final TeamResolver teamResolver;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    /** A whole number from -MAX_AWARD to MAX_AWARD, not 0. */
    public static int requireAward(Integer points) {
        int value = points == null ? 0 : points;
        if (value == 0 || Math.abs(value) > MAX_AWARD) {
            throw new IllegalArgumentException("Points must be a whole number from -" + MAX_AWARD + " to " + MAX_AWARD
                    + ", and not 0");
        }
        return value;
    }

    public static String requireReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_REASON) {
            throw new IllegalArgumentException("Give a reason of up to " + MAX_REASON + " characters");
        }
        return trimmed;
    }

    /** Adds (or takes away) points for one player, with the reason they see in their points history. */
    @Transactional
    public RoomUser award(Room room, User user, int points, String reason) {
        RoomUser roomUser = roomUserService.seatFor(user, room);
        change(roomUser, points, reason, AWARD);
        roomUser = roomUserService.save(roomUser);
        Team team = teamResolver.teamOf(room, user);
        auditService.record("POINTS_AWARDED", "USER", user.getId(), user.getUsername(), team != null ? team.getId() : null,
                points + " pts in " + room.getName() + ": " + reason);
        events.publishEvent(new RoomActivityEvent(room.getId()));
        return roomUser;
    }

    /** Adds (or takes away) points for a team as a whole; the Teams board adds them to its members' points. */
    @Transactional
    public TeamPointsAward awardTeam(Room room, String teamId, int points, String reason) {
        Team team = teamRepository.findById(teamId).filter(found -> teamResolver.playsIn(room, found))
                .orElseThrow(() -> new ResourceNotFoundException("No team " + teamId + " plays in " + room.getName()));
        TeamPointsAward award = teamAwards.save(TeamPointsAward.builder()
                .roomId(room.getId())
                .teamId(team.getId())
                .teamName(team.getName())
                .points(points)
                .reason(reason)
                .awardedBy(currentUser.username())
                .awardedAt(LocalDateTime.now())
                .build());
        auditService.record("TEAM_POINTS_AWARDED", "TEAM", team.getId(), team.getName(), team.getId(),
                points + " pts in " + room.getName() + ": " + reason);
        events.publishEvent(new RoomActivityEvent(room.getId()));
        return award;
    }

    /** What giving credit did. {@code team} is set when the solve also counts for the player's team. */
    public record Credited(String userId, String username, String questionId, int points, String team) {
    }

    /**
     * Marks a question solved for a player, as if they had found the flag: full points unless a custom
     * amount is given. With shared solves the team has it too. {@code attempt} is the attempt the
     * credit is for, when there is one.
     */
    @Transactional
    public Credited credit(Room room, User user, CTFEntity question, Integer customPoints, String reason, Integer attempt) {
        requireInRoom(room, question);
        int points = customPoints == null ? value(question.getPoints()) : customPoints;
        if (points < 0 || points > MAX_AWARD) {
            throw new IllegalArgumentException("Credit is from 0 to " + MAX_AWARD + " points");
        }
        RoomUser roomUser = roomUserService.seatFor(user, room);
        Credited credited = creditOne(room, roomUser, question, points, reason, attempt);
        events.publishEvent(new RoomActivityEvent(room.getId()));
        return credited;
    }

    private Credited creditOne(Room room, RoomUser roomUser, CTFEntity question, int points, String reason, Integer attempt) {
        User user = roomUser.getUser();
        CTFEntityAnswer answer = answerRepository.findByRoomUserIdAndCtfEntityId(roomUser.getId(), question.getId())
                .orElseGet(() -> CTFEntityAnswer.builder().roomUser(roomUser).ctfEntity(question).correct(false)
                        .answers(new ArrayList<>()).attemptTimes(new ArrayList<>()).attempts(0).build());
        if (Boolean.TRUE.equals(answer.getCorrect())) {
            throw new IllegalStateException(user.getUsername() + " already solved " + question.getQuestion());
        }
        Team team = teamResolver.teamOf(room, user);
        boolean shared = room.effectiveTeamCredit() == TeamCredit.SHARED_SOLVES && team != null;
        if (shared) {
            answerRepository.findByCtfEntityId(question.getId()).stream()
                    .filter(other -> Boolean.TRUE.equals(other.getCorrect()) && other.getRoomUser().getUser() != null)
                    .filter(other -> {
                        Team theirs = teamResolver.teamOf(room, other.getRoomUser().getUser());
                        return theirs != null && theirs.getId().equals(team.getId());
                    })
                    .findFirst()
                    .ifPresent(other -> {
                        throw new IllegalStateException(other.getRoomUser().getUser().getUsername() + " already solved "
                                + question.getQuestion() + " for " + team.getName());
                    });
        }
        String actor = currentUser.username();
        if (attempt != null) {
            List<CTFEntityAnswerRequest> attempts = answer.getAnswers();
            if (attempt < 0 || attempt >= attempts.size()) {
                throw new ResourceNotFoundException("That attempt is not in " + user.getUsername() + "'s answers");
            }
            attempts.get(attempt).setCreditedBy(actor);
            answer.setAnswers(new ArrayList<>(attempts));
        }
        LocalDateTime now = LocalDateTime.now();
        answer.setCorrect(true);
        answer.setSolvedAt(now);
        answer.setScore(points);
        answer.setCreditedBy(actor);
        answerRepository.save(answer);

        change(roomUser, points, "Credit for " + question.getQuestion() + ": " + reason, CREDIT);
        roomUserService.save(roomUser);
        auditService.record("CREDIT_GIVEN", "CHALLENGE", question.getId(), question.getQuestion(),
                team != null ? team.getId() : null,
                user.getUsername() + " got " + points + " pts in " + room.getName() + ": " + reason);
        return new Credited(user.getId(), user.getUsername(), question.getId(), points, shared ? team.getName() : null);
    }

    /** What accepting an answer did. */
    public record Accepted(String flagId, String flag, List<Credited> credited, List<String> skipped) {
    }

    /**
     * Adds an answer as another accepted flag on the question. With {@code creditExisting}, everyone
     * who already submitted it, and has not solved the question, is credited full points.
     */
    @Transactional
    public Accepted acceptAnswer(Room room, CTFEntity question, String answerText, Boolean caseSensitive,
                                 boolean creditExisting, String reason) {
        requireInRoom(room, question);
        String text = answerText == null ? "" : answerText.trim();
        if (text.isEmpty() || text.length() > 1000) {
            throw new IllegalArgumentException("Give the answer to accept, up to 1000 characters");
        }
        List<Flag> flags = flagRepository.getFlagsByCtfEntityId(question.getId());
        if (flags.stream().anyMatch(flag -> FlagService.accepts(flag, text, room.getSurroundTag()))) {
            throw new IllegalStateException("That answer is already accepted for " + question.getQuestion());
        }
        Flag flag = new Flag();
        flag.setFlag(text);
        flag.setCaseSensitive(!Boolean.FALSE.equals(caseSensitive));
        flag.setSurroundWithTag(false);
        flag.setCtfEntity(question);
        flag = flagRepository.save(flag);
        question.getFlags().add(flag);

        List<Credited> credited = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        if (creditExisting) {
            record Match(CTFEntityAnswer answer, int attempt, LocalDateTime at) {
            }
            List<Match> matches = new ArrayList<>();
            for (CTFEntityAnswer answer : answerRepository.findByCtfEntityId(question.getId())) {
                if (Boolean.TRUE.equals(answer.getCorrect())) {
                    continue;
                }
                List<CTFEntityAnswerRequest> attempts = answer.getAnswers() == null ? List.of() : answer.getAnswers();
                for (int i = 0; i < attempts.size(); i++) {
                    if (FlagService.accepts(flag, attempts.get(i).getFlag(), room.getSurroundTag())) {
                        matches.add(new Match(answer, i, attemptTime(answer, i)));
                        break;
                    }
                }
            }
            matches.sort(Comparator.comparing(Match::at, Comparator.nullsLast(Comparator.naturalOrder())));
            Set<String> seen = new HashSet<>();
            for (Match match : matches) {
                RoomUser roomUser = match.answer().getRoomUser();
                if (!seen.add(roomUser.getId())) {
                    continue;
                }
                try {
                    credited.add(creditOne(room, roomUser, question, value(question.getPoints()),
                            "Accepted answer: " + reason, match.attempt()));
                } catch (IllegalStateException alreadySolved) {
                    skipped.add(alreadySolved.getMessage());
                }
            }
        }
        auditService.record("FLAG_ACCEPTED", "CHALLENGE", question.getId(), question.getQuestion(), null,
                "Accepted \"" + text + "\" in " + room.getName()
                        + (creditExisting ? ", credited " + credited.size() + (credited.size() == 1 ? " player" : " players") : "")
                        + ": " + reason);
        events.publishEvent(new RoomActivityEvent(room.getId()));
        return new Accepted(flag.getId(), flag.getFlag(), credited, skipped);
    }

    /** What a reset took away. */
    public record Reset(String userId, String username, String questionId, int questionsReset, int pointsChange) {
    }

    /**
     * Takes back a player's progress on one question, or in the whole lab when {@code question} is
     * null: answers, attempts and hints go, and so do the points they brought or cost.
     */
    @Transactional
    public Reset reset(Room room, User user, CTFEntity question, String reason) {
        RoomUser roomUser = roomUserService.findByUserAndRoom(user, room)
                .orElseThrow(() -> new ResourceNotFoundException(user.getUsername() + " has no progress in " + room.getName()));
        List<CTFEntityAnswer> answers;
        if (question != null) {
            requireInRoom(room, question);
            answers = answerRepository.findByRoomUserIdAndCtfEntityId(roomUser.getId(), question.getId()).map(List::of)
                    .orElseThrow(() -> new ResourceNotFoundException(user.getUsername() + " has no progress on "
                            + question.getQuestion()));
        } else {
            answers = answerRepository.findByRoomUserId(roomUser.getId());
        }
        int before = value(roomUser.getPoints());
        int change;
        if (question != null) {
            CTFEntityAnswer answer = answers.getFirst();
            int hints = hintCost(answer);
            change = -value(answer.getScore()) + hints;
        } else {
            change = -before;
        }
        answerRepository.deleteAll(answers);
        String what = question != null ? question.getQuestion() : "the whole lab";
        change(roomUser, change, "Progress reset on " + what + ": " + reason, RESET);
        roomUserService.save(roomUser);
        Team team = teamResolver.teamOf(room, user);
        auditService.record("PROGRESS_RESET", "USER", user.getId(), user.getUsername(), team != null ? team.getId() : null,
                "Reset " + what + " in " + room.getName() + " (" + signed(change) + " pts): " + reason);
        events.publishEvent(new RoomActivityEvent(room.getId()));
        return new Reset(user.getId(), user.getUsername(), question != null ? question.getId() : null, answers.size(), change);
    }

    private static int hintCost(CTFEntityAnswer answer) {
        return answer.getHintUsages() == null ? 0 : answer.getHintUsages().stream().filter(Objects::nonNull)
                .mapToInt(usage -> value(usage.getPointsDeducted())).sum();
    }

    private void change(RoomUser roomUser, int points, String reason, String kind) {
        int total = value(roomUser.getPoints()) + points;
        if (roomUser.getPointsHistory() == null) {
            roomUser.setPointsHistory(new ArrayList<>());
        }
        roomUser.getPointsHistory().add(new PointsHistoryEntry(LocalDateTime.now(), total, points, reason,
                currentUser.username(), kind));
        roomUser.setPoints(total);
        roomUser.setUpdatedAt(LocalDateTime.now());
    }

    private static void requireInRoom(Room room, CTFEntity question) {
        if (question.getRoom() == null || !room.getId().equals(question.getRoom().getId())) {
            throw new IllegalArgumentException("That question is not in " + room.getName());
        }
    }

    private static LocalDateTime attemptTime(CTFEntityAnswer answer, int index) {
        List<LocalDateTime> times = answer.getAttemptTimes();
        if (times != null && index < times.size() && times.get(index) != null) {
            return times.get(index);
        }
        CTFEntityAnswerRequest attempt = answer.getAnswers().get(index);
        return attempt.getSubmissionTime();
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }

    /** For the controller: the user, or 404. */
    public User requireUser(String userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));
    }

    /** For the controller: a question of the lab, or 404. */
    public CTFEntity requireQuestion(Room room, String questionId) {
        CTFEntity question = ctfEntityRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question " + questionId + " not found"));
        requireInRoom(room, question);
        return question;
    }
}
