package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.components.TeamResolver;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.PointsHistoryEntry;
import com.infernokun.amaterasu.models.entities.AuditEvent;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityAnswer;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityHintUsage;
import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.ctf.TeamPointsAward;
import com.infernokun.amaterasu.models.entities.ctf.dto.CTFEntityAnswerRequest;
import com.infernokun.amaterasu.models.entities.ctf.dto.manage.LabManage;
import com.infernokun.amaterasu.models.entities.ctf.dto.manage.LabManage.ActivityItem;
import com.infernokun.amaterasu.models.entities.ctf.dto.manage.LabManage.ActivityKind;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.EnvironmentView.EnvironmentCopy;
import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import com.infernokun.amaterasu.models.enums.TeamCredit;
import com.infernokun.amaterasu.repositories.AuditEventRepository;
import com.infernokun.amaterasu.repositories.ctf.CTFAnswerRepository;
import com.infernokun.amaterasu.repositories.ctf.CTFEntityRepository;
import com.infernokun.amaterasu.repositories.ctf.TeamPointsAwardRepository;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.services.entity.lab.LabEnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read models for managing one lab: the overview, every submission with near misses marked, players
 * and teams, question health and the lab's activity. Teams come from {@link TeamResolver}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabManageService {
    static final Duration ACTIVE = Duration.ofMinutes(15);
    static final int MAX_PAGE = 200;
    static final int WRONG_ANSWERS = 10;
    private static final int[] BUCKET_MINUTES = {5, 15, 30, 60, 180, 360, 720, 1440, 10080};
    private static final int MAX_BUCKETS = 48;
    private static final List<String> ENVIRONMENT_ACTIONS = List.of("LAB_STARTED", "LAB_PAUSED", "LAB_STOPPED", "LAB_ACTION_FAILED");
    private static final List<String> TEAM_ACTIONS = List.of("LAB_TEAM_MODE_CHANGED", "LAB_TEAM_CREATED", "LAB_TEAM_UPDATED",
            "LAB_TEAM_DELETED", "LAB_TEAM_MEMBERS_CHANGED", "LAB_TEAMS_COPIED");

    private final RoomUserService roomUserService;
    private final CTFEntityRepository ctfEntityRepository;
    private final CTFAnswerRepository answerRepository;
    private final TeamResolver teamResolver;
    private final TeamPointsAwardRepository teamAwards;
    private final LabEnvironmentService labEnvironmentService;
    private final LabTrackerRepository labTrackerRepository;
    private final AuditEventRepository auditEventRepository;

    /** One attempt out of an answer record, with whether it was right. */
    record Attempt(CTFEntityAnswer answer, int index, CTFEntityAnswerRequest request, LocalDateTime at, boolean correct) {
        String flag() {
            return request.getFlag();
        }
    }

    /** Everything about the lab the views below read, loaded once per request. */
    private final class Snapshot {
        final Room room;
        final List<CTFEntity> questions;
        final Map<String, CTFEntity> questionsById;
        final List<RoomUser> players;
        final List<CTFEntityAnswer> answers;
        final Map<String, Team> teams;
        final Map<String, LabManage.NearMissView> nearMisses = new HashMap<>();

        Snapshot(Room room) {
            this.room = room;
            this.questions = ctfEntityRepository.findByRoomId(room.getId()).stream().sorted(CTFEntityService.ROOM_ORDER).toList();
            this.questionsById = questions.stream().collect(Collectors.toMap(CTFEntity::getId, Function.identity()));
            this.players = roomUserService.findByRoomIdOrderByPointsDesc(room.getId()).stream()
                    .filter(player -> player.getRoomUserStatus() != RoomUserStatus.KICKED
                            && player.getRoomUserStatus() != RoomUserStatus.BANNED)
                    .toList();
            this.answers = answerRepository.findByRoomUserRoomId(room.getId()).stream()
                    .filter(answer -> answer.getCtfEntity() != null && answer.getRoomUser() != null && answer.getRoomUser().getUser() != null)
                    .toList();
            this.teams = teamResolver.teamsByUser(room);
        }

        Team teamOf(User user) {
            return user == null ? null : teams.get(user.getId());
        }

        LabManage.NearMissView nearMiss(Attempt attempt) {
            if (attempt.correct() || attempt.flag() == null) {
                return null;
            }
            CTFEntity question = attempt.answer().getCtfEntity();
            String key = question.getId() + "\u0000" + attempt.flag();
            if (nearMisses.containsKey(key)) {
                return nearMisses.get(key);
            }
            List<Flag> flags = question.getFlags() == null ? List.of() : question.getFlags();
            LabManage.NearMissView view = NearMiss.check(attempt.flag(), flags, room.getSurroundTag())
                    .map(match -> new LabManage.NearMissView(match.flagId(), match.flag(), match.reasons()))
                    .orElse(null);
            nearMisses.put(key, view);
            return view;
        }
    }

    static List<Attempt> attempts(CTFEntityAnswer answer) {
        List<CTFEntityAnswerRequest> requests = answer.getAnswers() == null ? List.of() : answer.getAnswers();
        List<LocalDateTime> times = answer.getAttemptTimes() == null ? List.of() : answer.getAttemptTimes();
        List<Attempt> attempts = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            CTFEntityAnswerRequest request = requests.get(i);
            if (request == null) {
                continue;
            }
            LocalDateTime at = i < times.size() && times.get(i) != null ? times.get(i) : request.getSubmissionTime();
            // Attempts from before each one's outcome was kept: the last one solved it, unless staff gave credit
            boolean correct = request.getCorrect() != null ? request.getCorrect()
                    : Boolean.TRUE.equals(answer.getCorrect()) && answer.getCreditedBy() == null && i == requests.size() - 1;
            attempts.add(new Attempt(answer, i, request, at, correct));
        }
        return attempts;
    }

    public LabManage.Overview overview(Room room) {
        Snapshot lab = new Snapshot(room);
        LocalDateTime now = LocalDateTime.now();
        List<Attempt> all = lab.answers.stream().flatMap(answer -> attempts(answer).stream()).toList();
        int solves = (int) lab.answers.stream().filter(answer -> Boolean.TRUE.equals(answer.getCorrect())).count();
        int wrong = (int) all.stream().filter(attempt -> !attempt.correct()).count();
        int nearMisses = (int) all.stream().filter(attempt -> lab.nearMiss(attempt) != null).count();
        List<CTFEntityHintUsage> hints = lab.answers.stream().flatMap(answer -> orEmpty(answer.getHintUsages()).stream()).toList();

        Map<String, LocalDateTime> lastActivity = lastActivity(lab);
        LocalDateTime activeSince = now.minus(ACTIVE);
        int active = (int) lastActivity.values().stream().filter(at -> at != null && at.isAfter(activeSince)).count();

        LocalDateTime first = lab.players.stream().map(RoomUser::getCreatedAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDateTime firstAttempt = all.stream().map(Attempt::at).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        if (first == null || (firstAttempt != null && firstAttempt.isBefore(first))) {
            first = firstAttempt;
        }
        int bucket = bucketMinutes(first, now);
        List<LabManage.TimeBucket> buckets = buckets(first, now, bucket,
                lab.answers.stream().map(CTFEntityAnswer::getSolvedAt).filter(Objects::nonNull).toList(),
                all.stream().map(Attempt::at).filter(Objects::nonNull).toList());

        int players = lab.players.size();
        Map<String, List<CTFEntityAnswer>> byQuestion = lab.answers.stream()
                .collect(Collectors.groupingBy(answer -> answer.getCtfEntity().getId()));
        List<LabManage.QuestionRate> rates = lab.questions.stream().map(question -> {
            List<CTFEntityAnswer> answers = byQuestion.getOrDefault(question.getId(), List.of());
            int solved = (int) answers.stream().filter(answer -> Boolean.TRUE.equals(answer.getCorrect())).count();
            return new LabManage.QuestionRate(question.getId(), question.getQuestion(), value(question.getPoints()), solved,
                    (int) answers.stream().filter(answer -> value(answer.getAttempts()) > 0).count(),
                    answers.stream().mapToInt(answer -> value(answer.getAttempts())).sum(),
                    percent(solved, players),
                    answers.stream().mapToInt(answer -> orEmpty(answer.getHintUsages()).size()).sum());
        }).toList();

        List<EnvironmentCopy> copies = labEnvironmentService.copies(room);
        int teams = (int) lab.players.stream().map(player -> lab.teamOf(player.getUser())).filter(Objects::nonNull)
                .map(Team::getId).distinct().count();
        return new LabManage.Overview(room.getId(), room.getName(), room.effectiveTeamMode(), room.effectiveTeamCredit(),
                room.effectiveMode(), players, active, teams, lab.questions.size(), solves, all.size(), wrong, nearMisses,
                hints.size(), hints.stream().mapToInt(usage -> value(usage.getPointsDeducted())).sum(), toInstant(first),
                bucket, buckets, rates, copies);
    }

    /** Every attempt, newest first. {@code result} is "correct", "wrong" or "near"; null or "all" keeps every one. */
    public LabManage.SubmissionPage submissions(Room room, String userId, String teamId, String questionId, String result,
                                                int page, int size) {
        Snapshot lab = new Snapshot(room);
        int pageSize = Math.max(1, Math.min(size <= 0 ? 50 : size, MAX_PAGE));
        int pageIndex = Math.max(0, page);
        List<LabManage.Submission> all = submissions(lab, lab.answers).stream()
                .filter(submission -> blank(userId) || userId.equals(submission.userId()))
                .filter(submission -> blank(teamId) || teamId.equals(submission.teamId())
                        || ("none".equals(teamId) && submission.teamId() == null))
                .filter(submission -> blank(questionId) || questionId.equals(submission.questionId()))
                .filter(submission -> matchesResult(submission, result))
                .toList();
        int nearMisses = (int) all.stream().filter(submission -> submission.nearMiss() != null).count();
        int from = Math.min(all.size(), pageIndex * pageSize);
        int to = Math.min(all.size(), from + pageSize);
        return new LabManage.SubmissionPage(all.subList(from, to), pageIndex, pageSize, all.size(), nearMisses);
    }

    private static boolean matchesResult(LabManage.Submission submission, String result) {
        if (blank(result) || "all".equalsIgnoreCase(result)) {
            return true;
        }
        return switch (result.toLowerCase(Locale.ROOT)) {
            case "correct" -> submission.correct();
            case "wrong" -> !submission.correct();
            case "near" -> submission.nearMiss() != null;
            default -> throw new IllegalArgumentException("result is all, correct, wrong or near");
        };
    }

    private List<LabManage.Submission> submissions(Snapshot lab, List<CTFEntityAnswer> answers) {
        List<LabManage.Submission> submissions = new ArrayList<>();
        for (CTFEntityAnswer answer : answers) {
            User user = answer.getRoomUser().getUser();
            Team team = lab.teamOf(user);
            CTFEntity question = answer.getCtfEntity();
            for (Attempt attempt : attempts(answer)) {
                submissions.add(new LabManage.Submission(answer.getId() + ":" + attempt.index(), answer.getId(), attempt.index(),
                        user.getId(), user.getUsername(), team != null ? team.getId() : null, team != null ? team.getName() : null,
                        question.getId(), question.getQuestion(), value(question.getPoints()), attempt.flag(),
                        toInstant(attempt.at()), attempt.correct(), attempt.request().getCreditedBy(),
                        Boolean.TRUE.equals(answer.getCorrect()), lab.nearMiss(attempt)));
            }
        }
        submissions.sort(Comparator.comparing(LabManage.Submission::at, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LabManage.Submission::id));
        return submissions;
    }

    public LabManage.Players players(Room room) {
        Snapshot lab = new Snapshot(room);
        Map<String, LocalDateTime> lastActivity = lastActivity(lab);
        Map<String, List<CTFEntityAnswer>> byPlayer = lab.answers.stream()
                .collect(Collectors.groupingBy(answer -> answer.getRoomUser().getId()));
        List<LabManage.PlayerRow> rows = lab.players.stream()
                .map(player -> playerRow(lab, player, byPlayer.getOrDefault(player.getId(), List.of()), lastActivity))
                .toList();

        Map<String, List<TeamPointsAward>> awards = teamAwards.findByRoomIdOrderByAwardedAtAsc(room.getId()).stream()
                .collect(Collectors.groupingBy(TeamPointsAward::getTeamId));
        List<LabManage.TeamRow> teams = new ArrayList<>();
        for (Team team : teamResolver.teamsIn(room)) {
            List<LabManage.PlayerRow> members = rows.stream().filter(row -> team.getId().equals(row.teamId())).toList();
            int awardPoints = awards.getOrDefault(team.getId(), List.of()).stream().mapToInt(award -> value(award.getPoints())).sum();
            Set<String> memberIds = members.stream().map(LabManage.PlayerRow::userId).collect(Collectors.toSet());
            int solved = (int) lab.answers.stream()
                    .filter(answer -> Boolean.TRUE.equals(answer.getCorrect()) && memberIds.contains(answer.getRoomUser().getUser().getId()))
                    .map(answer -> answer.getCtfEntity().getId()).distinct().count();
            if (members.isEmpty() && awardPoints == 0) {
                continue;
            }
            teams.add(new LabManage.TeamRow(team.getId(), team.getName(),
                    members.stream().map(LabManage.PlayerRow::username).toList(),
                    members.stream().mapToInt(LabManage.PlayerRow::points).sum() + awardPoints, awardPoints, solved,
                    members.stream().mapToInt(LabManage.PlayerRow::attempts).sum(),
                    members.stream().mapToInt(LabManage.PlayerRow::hintsUsed).sum()));
        }
        teams.sort(Comparator.comparingInt(LabManage.TeamRow::points).reversed().thenComparing(LabManage.TeamRow::name,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return new LabManage.Players(rows, teams);
    }

    /** One player's progress on every question, their attempts and their points history. */
    public LabManage.PlayerDetail player(Room room, String userId) {
        Snapshot lab = new Snapshot(room);
        RoomUser player = lab.players.stream().filter(seat -> seat.getUser().getId().equals(userId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("That player has no progress in " + room.getName()));
        List<CTFEntityAnswer> mine = lab.answers.stream().filter(answer -> answer.getRoomUser().getId().equals(player.getId())).toList();
        Map<String, CTFEntityAnswer> byQuestion = mine.stream()
                .collect(Collectors.toMap(answer -> answer.getCtfEntity().getId(), Function.identity(), (a, b) -> a));
        Team team = lab.teamOf(player.getUser());
        boolean shared = room.effectiveTeamCredit() == TeamCredit.SHARED_SOLVES && team != null;
        List<LabManage.QuestionProgress> questions = lab.questions.stream().map(question -> {
            CTFEntityAnswer answer = byQuestion.get(question.getId());
            String teammate = null;
            if (shared && (answer == null || !Boolean.TRUE.equals(answer.getCorrect()))) {
                teammate = lab.answers.stream()
                        .filter(other -> other.getCtfEntity().getId().equals(question.getId()) && Boolean.TRUE.equals(other.getCorrect())
                                && !other.getRoomUser().getId().equals(player.getId()))
                        .map(other -> other.getRoomUser().getUser())
                        .filter(user -> lab.teamOf(user) != null && lab.teamOf(user).getId().equals(team.getId()))
                        .map(User::getUsername).findFirst().orElse(null);
            }
            List<CTFEntityHintUsage> hints = answer == null ? List.of() : orEmpty(answer.getHintUsages());
            return new LabManage.QuestionProgress(question.getId(), question.getQuestion(), value(question.getPoints()),
                    answer != null && Boolean.TRUE.equals(answer.getCorrect()),
                    answer != null ? toInstant(answer.getSolvedAt()) : null, answer != null ? value(answer.getScore()) : 0,
                    answer != null ? value(answer.getAttempts()) : 0, hints.size(),
                    hints.stream().mapToInt(usage -> value(usage.getPointsDeducted())).sum(),
                    answer != null ? answer.getCreditedBy() : null, teammate);
        }).toList();
        Map<String, List<CTFEntityAnswer>> byPlayer = Map.of(player.getId(), mine);
        LabManage.PlayerRow row = playerRow(lab, player, byPlayer.get(player.getId()), lastActivity(lab));
        return new LabManage.PlayerDetail(row, questions, submissions(lab, mine), orEmpty(player.getPointsHistory()));
    }

    public List<LabManage.QuestionStats> questions(Room room) {
        Snapshot lab = new Snapshot(room);
        int players = lab.players.size();
        Map<String, List<CTFEntityAnswer>> byQuestion = lab.answers.stream()
                .collect(Collectors.groupingBy(answer -> answer.getCtfEntity().getId()));
        return lab.questions.stream().map(question -> {
            List<CTFEntityAnswer> answers = byQuestion.getOrDefault(question.getId(), List.of());
            record Wrong(String answer, Set<String> players, List<Attempt> attempts) {
            }
            Map<String, Wrong> wrong = new LinkedHashMap<>();
            int nearMisses = 0;
            for (CTFEntityAnswer answer : answers) {
                for (Attempt attempt : attempts(answer)) {
                    if (attempt.correct() || attempt.flag() == null) {
                        continue;
                    }
                    String text = attempt.flag().trim();
                    Wrong entry = wrong.computeIfAbsent(text, key -> new Wrong(key, new HashSet<>(), new ArrayList<>()));
                    entry.players().add(answer.getRoomUser().getId());
                    entry.attempts().add(attempt);
                    if (lab.nearMiss(attempt) != null) {
                        nearMisses++;
                    }
                }
            }
            List<LabManage.WrongAnswer> common = wrong.values().stream()
                    .sorted(Comparator.comparingInt((Wrong entry) -> entry.attempts().size()).reversed()
                            .thenComparing(Wrong::answer))
                    .limit(WRONG_ANSWERS)
                    .map(entry -> new LabManage.WrongAnswer(entry.answer(), entry.attempts().size(), entry.players().size(),
                            lab.nearMiss(entry.attempts().getFirst())))
                    .toList();
            int solved = (int) answers.stream().filter(answer -> Boolean.TRUE.equals(answer.getCorrect())).count();
            return new LabManage.QuestionStats(question.getId(), question.getQuestion(), question.getCategory(),
                    value(question.getPoints()), answers.stream().mapToInt(answer -> value(answer.getAttempts())).sum(),
                    (int) answers.stream().filter(answer -> value(answer.getAttempts()) > 0).count(), solved,
                    percent(solved, players), answers.stream().mapToInt(answer -> orEmpty(answer.getHintUsages()).size()).sum(),
                    nearMisses, common);
        }).toList();
    }

    /** The lab's activity, newest first: first activity, hints, solves, credits, awards, resets, team and environment changes. */
    public List<ActivityItem> activity(Room room, int limit) {
        Snapshot lab = new Snapshot(room);
        List<ActivityItem> items = new ArrayList<>();
        for (RoomUser player : lab.players) {
            Team team = lab.teamOf(player.getUser());
            String teamName = team != null ? team.getName() : null;
            if (player.getCreatedAt() != null) {
                items.add(new ActivityItem(ActivityKind.FIRST_ACTIVITY, toInstant(player.getCreatedAt()), player.getUser().getUsername(),
                        teamName, "started the lab", null, null));
            }
            for (PointsHistoryEntry entry : orEmpty(player.getPointsHistory())) {
                if (entry.getAwardedBy() == null) {
                    continue;
                }
                ActivityKind kind = RoomPointsService.CREDIT.equals(entry.getKind()) ? ActivityKind.CREDIT
                        : RoomPointsService.RESET.equals(entry.getKind()) ? ActivityKind.RESET : ActivityKind.AWARD;
                String text = switch (kind) {
                    case CREDIT -> "gave " + player.getUser().getUsername() + " credit";
                    case RESET -> "reset " + player.getUser().getUsername() + "'s progress";
                    default -> "awarded " + player.getUser().getUsername();
                };
                items.add(new ActivityItem(kind, toInstant(entry.getTimestamp()), entry.getAwardedBy(), teamName, text,
                        entry.getReason(), value(entry.getPointsChange())));
            }
        }
        for (CTFEntityAnswer answer : lab.answers) {
            User user = answer.getRoomUser().getUser();
            Team team = lab.teamOf(user);
            String teamName = team != null ? team.getName() : null;
            CTFEntity question = answer.getCtfEntity();
            if (Boolean.TRUE.equals(answer.getCorrect()) && answer.getSolvedAt() != null && answer.getCreditedBy() == null) {
                items.add(new ActivityItem(ActivityKind.SOLVE, toInstant(answer.getSolvedAt()), user.getUsername(), teamName,
                        "solved " + question.getQuestion(), null, value(answer.getScore())));
            }
            for (CTFEntityHintUsage usage : orEmpty(answer.getHintUsages())) {
                items.add(new ActivityItem(ActivityKind.HINT, toInstant(usage.getUsedAt()), user.getUsername(), teamName,
                        "unlocked hint " + value(usage.getUsageOrder()) + " on " + question.getQuestion(), null,
                        -value(usage.getPointsDeducted())));
            }
        }
        for (TeamPointsAward award : teamAwards.findByRoomIdOrderByAwardedAtAsc(room.getId())) {
            items.add(new ActivityItem(ActivityKind.TEAM_AWARD, toInstant(award.getAwardedAt()), award.getAwardedBy(),
                    award.getTeamName(), "awarded the team " + award.getTeamName(), award.getReason(), value(award.getPoints())));
        }
        if (!lab.questions.isEmpty()) {
            auditEventRepository.findTop500ByTargetTypeAndTargetIdInAndActionInOrderByAtDesc("CHALLENGE",
                            lab.questionsById.keySet(), List.of("FLAG_ACCEPTED"))
                    .forEach(event -> items.add(audited(ActivityKind.ACCEPTED_ANSWER, event,
                            "accepted another answer on " + event.getTargetLabel())));
        }
        auditEventRepository.findTop500ByTargetTypeAndTargetIdInAndActionInOrderByAtDesc("ROOM", List.of(room.getId()), TEAM_ACTIONS)
                .forEach(event -> items.add(audited(ActivityKind.TEAMS, event, teamText(event.getAction()))));
        Set<String> trackers = trackerIds(room);
        if (!trackers.isEmpty()) {
            auditEventRepository.findTop500ByTargetTypeAndTargetIdInAndActionInOrderByAtDesc("LAB_TRACKER", trackers, ENVIRONMENT_ACTIONS)
                    .forEach(event -> items.add(audited(ActivityKind.ENVIRONMENT, event, environmentText(event))));
        }
        items.sort(Comparator.comparing(ActivityItem::at, Comparator.nullsLast(Comparator.reverseOrder())));
        int max = Math.max(1, Math.min(limit <= 0 ? 200 : limit, 1000));
        return items.size() > max ? new ArrayList<>(items.subList(0, max)) : items;
    }

    private Set<String> trackerIds(Room room) {
        Set<String> ids = new HashSet<>();
        labTrackerRepository.findByRoomId(room.getId()).stream().map(LabTracker::getId).forEach(ids::add);
        labEnvironmentService.copies(room).stream().map(EnvironmentCopy::trackerId).filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }

    private static ActivityItem audited(ActivityKind kind, AuditEvent event, String text) {
        return new ActivityItem(kind, event.getAt(), event.getActorUsername(), null, text, event.getDetail(), null);
    }

    private static String teamText(String action) {
        return switch (action) {
            case "LAB_TEAM_MODE_CHANGED" -> "changed which teams count";
            case "LAB_TEAM_CREATED" -> "made a lab team";
            case "LAB_TEAM_UPDATED" -> "renamed a lab team";
            case "LAB_TEAM_DELETED" -> "deleted a lab team";
            case "LAB_TEAM_MEMBERS_CHANGED" -> "changed a lab team's members";
            case "LAB_TEAMS_COPIED" -> "copied the site teams";
            default -> action.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private static String environmentText(AuditEvent event) {
        String copy = event.getTargetLabel() != null ? event.getTargetLabel() : "the environment";
        return switch (event.getAction()) {
            case "LAB_STARTED" -> "started " + copy;
            case "LAB_PAUSED" -> "paused " + copy;
            case "LAB_STOPPED" -> "stopped " + copy;
            default -> "could not change " + copy;
        };
    }

    private LabManage.PlayerRow playerRow(Snapshot lab, RoomUser player, List<CTFEntityAnswer> answers,
                                          Map<String, LocalDateTime> lastActivity) {
        User user = player.getUser();
        Team team = lab.teamOf(user);
        List<Attempt> attempts = answers.stream().flatMap(answer -> attempts(answer).stream()).toList();
        List<CTFEntityHintUsage> hints = answers.stream().flatMap(answer -> orEmpty(answer.getHintUsages()).stream()).toList();
        return new LabManage.PlayerRow(user.getId(), user.getUsername(), user.getDisplayName(),
                team != null ? team.getId() : null, team != null ? team.getName() : null, value(player.getPoints()),
                (int) answers.stream().filter(answer -> Boolean.TRUE.equals(answer.getCorrect())).count(),
                attempts.size(), (int) attempts.stream().filter(attempt -> !attempt.correct()).count(), hints.size(),
                hints.stream().mapToInt(usage -> value(usage.getPointsDeducted())).sum(),
                toInstant(player.getCreatedAt()), toInstant(lastActivity.get(player.getId())));
    }

    /** The last thing each player did, by their progress record's id. */
    private static Map<String, LocalDateTime> lastActivity(Snapshot lab) {
        Map<String, LocalDateTime> last = new HashMap<>();
        for (RoomUser player : lab.players) {
            latest(last, player.getId(), player.getCreatedAt());
        }
        for (CTFEntityAnswer answer : lab.answers) {
            String id = answer.getRoomUser().getId();
            attempts(answer).forEach(attempt -> latest(last, id, attempt.at()));
            latest(last, id, answer.getSolvedAt());
            orEmpty(answer.getHintUsages()).forEach(usage -> latest(last, id, usage.getUsedAt()));
        }
        return last;
    }

    private static void latest(Map<String, LocalDateTime> last, String id, LocalDateTime at) {
        if (at != null) {
            last.merge(id, at, (a, b) -> a.isAfter(b) ? a : b);
        }
    }

    static int bucketMinutes(LocalDateTime first, LocalDateTime now) {
        if (first == null) {
            return BUCKET_MINUTES[0];
        }
        long minutes = Math.max(1, Duration.between(first, now).toMinutes());
        for (int size : BUCKET_MINUTES) {
            if (minutes / size < MAX_BUCKETS) {
                return size;
            }
        }
        return BUCKET_MINUTES[BUCKET_MINUTES.length - 1];
    }

    static List<LabManage.TimeBucket> buckets(LocalDateTime first, LocalDateTime now, int minutes,
                                              List<LocalDateTime> solves, List<LocalDateTime> attempts) {
        if (first == null) {
            return List.of();
        }
        LocalDateTime start = first.truncatedTo(ChronoUnit.MINUTES);
        start = start.minusMinutes(start.getMinute() % Math.min(minutes, 60));
        int count = (int) (Duration.between(start, now).toMinutes() / minutes) + 1;
        count = Math.min(count, MAX_BUCKETS + 1);
        int[] solved = new int[count];
        int[] tried = new int[count];
        for (LocalDateTime at : solves) {
            int i = index(start, at, minutes, count);
            if (i >= 0) {
                solved[i]++;
            }
        }
        for (LocalDateTime at : attempts) {
            int i = index(start, at, minutes, count);
            if (i >= 0) {
                tried[i]++;
            }
        }
        List<LabManage.TimeBucket> buckets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            buckets.add(new LabManage.TimeBucket(toInstant(start.plusMinutes((long) i * minutes)), solved[i], tried[i]));
        }
        return buckets;
    }

    private static int index(LocalDateTime start, LocalDateTime at, int minutes, int count) {
        if (at == null || at.isBefore(start)) {
            return -1;
        }
        return (int) Math.min(count - 1, Duration.between(start, at).toMinutes() / minutes);
    }

    private static int percent(int part, int whole) {
        return whole == 0 ? 0 : Math.round(part * 100f / whole);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }

    private static Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant();
    }
}
