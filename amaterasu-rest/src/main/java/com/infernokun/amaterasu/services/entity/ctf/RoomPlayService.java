package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.models.PointsHistoryEntry;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityAnswer;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntityHintUsage;
import com.infernokun.amaterasu.models.entities.ctf.Hint;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.RoomUser;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.EnvironmentView;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.LabCard;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.LabChallenge;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomLive;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomOverview;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomOverview.ChallengeState;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomStandings;
import com.infernokun.amaterasu.models.enums.RoomUserStatus;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.repositories.ctf.CTFAnswerRepository;
import com.infernokun.amaterasu.repositories.ctf.LabFileRepository;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.LabFileView;
import com.infernokun.amaterasu.services.entity.lab.LabEnvironmentService;
import com.infernokun.amaterasu.utils.ScenarioText;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read models for playing in a room: the room view, the scoreboard and the facilitator's live view. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomPlayService {
    static final Duration RECENT = Duration.ofMinutes(15);
    private static final int FEED_LIMIT = 60;

    private final RoomService roomService;
    private final RoomUserService roomUserService;
    private final CTFEntityService ctfEntityService;
    private final CTFAnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final LabEnvironmentService labEnvironmentService;
    private final CurrentUser currentUser;
    private final LabFileRepository labFileRepository;

    public RoomOverview overview(String roomId, String viewerId) {
        Room room = roomService.findByRoomId(roomId);
        if (currentUser.isAuthenticated()) {
            currentUser.requireCanSee(room);
        }
        List<CTFEntity> challenges = playable(ctfEntityService.findCTFEntitiesByRoomId(roomId));
        List<RoomUser> players = players(roomId);
        List<CTFEntityAnswer> answers = answerRepository.findByRoomUserRoomId(roomId);
        LocalDateTime now = LocalDateTime.now();

        Map<String, Long> solves = answers.stream().filter(answer -> Boolean.TRUE.equals(answer.getCorrect()))
                .collect(Collectors.groupingBy(answer -> answer.getCtfEntity().getId(), Collectors.counting()));

        User viewerUser = Optional.ofNullable(viewerId).flatMap(userRepository::findById).orElse(null);
        Optional<RoomUser> viewer = Optional.ofNullable(viewerUser).flatMap(user -> roomUserService.findByUserAndRoom(user, room));
        Map<String, CTFEntityAnswer> mine = viewer.map(roomUser -> answers.stream()
                        .filter(answer -> answer.getRoomUser().getId().equals(roomUser.getId()))
                        .collect(Collectors.toMap(answer -> answer.getCtfEntity().getId(), Function.identity(), (a, b) -> a)))
                .orElse(Map.of());

        Team myTeam = viewerUser != null ? viewerUser.getTeam() : null;
        RoomOverview.Me me = viewer.map(roomUser -> {
            List<RoomUser> ranked = rank(players);
            int rank = ranked.indexOf(ranked.stream()
                    .filter(other -> other.getId().equals(roomUser.getId())).findFirst().orElse(null)) + 1;
            int solved = (int) mine.values().stream().filter(answer -> Boolean.TRUE.equals(answer.getCorrect())).count();
            return new RoomOverview.Me(value(roomUser.getPoints()), rank, solved,
                    myTeam != null ? myTeam.getId() : null, myTeam != null ? myTeam.getName() : null);
        }).orElse(new RoomOverview.Me(0, 0, 0, myTeam != null ? myTeam.getId() : null,
                myTeam != null ? myTeam.getName() : null));

        // Who else on the viewer's team solved each question
        String myTeamId = myTeam != null ? myTeam.getId() : null;
        String myUserId = viewerUser != null ? viewerUser.getId() : null;
        Map<String, List<String>> teammates = new HashMap<>();
        if (myTeamId != null) {
            answers.stream()
                    .filter(answer -> Boolean.TRUE.equals(answer.getCorrect())
                            && !answer.getRoomUser().getUser().getId().equals(myUserId)
                            && myTeamId.equals(teamId(answer.getRoomUser())))
                    .forEach(answer -> teammates.computeIfAbsent(answer.getCtfEntity().getId(), key -> new ArrayList<>())
                            .add(answer.getRoomUser().getUser().getUsername()));
        }

        List<LabFileView> files = labFileRepository.listByRoom(roomId);
        Map<String, LabFileView> filesByName = files.stream()
                .collect(Collectors.toMap(LabFileView::name, Function.identity(), (a, b) -> a));
        List<RoomOverview.Challenge> views = challenges.stream()
                .map(challenge -> challengeView(challenge, mine.get(challenge.getId()),
                        solves.getOrDefault(challenge.getId(), 0L), now, filesByName,
                        teammates.getOrDefault(challenge.getId(), List.of())))
                .toList();

        boolean canRun = currentUser.isAuthenticated() && currentUser.canRun(room);
        return new RoomOverview(roomInfo(room, canRun), players.size(), teamCount(players), me, views,
                labEnvironmentService.view(room, viewerUser, canRun), files);
    }

    /** Every lab for the Labs list, with the viewer's environment status and progress. */
    public List<LabCard> cards(String viewerId) {
        User viewer = Optional.ofNullable(viewerId).flatMap(userRepository::findById).orElse(null);
        List<LabCard> cards = new ArrayList<>();
        for (Room room : roomService.findAllRooms()) {
            if (!currentUser.canSee(room)) {
                continue;
            }
            List<CTFEntity> challenges = playable(ctfEntityService.findCTFEntitiesByRoomId(room.getId()));
            List<RoomUser> players = players(room.getId());
            Optional<RoomUser> seat = viewer == null ? Optional.empty() : roomUserService.findByUserAndRoom(viewer, room);
            Set<String> playableIds = challenges.stream().map(CTFEntity::getId).collect(Collectors.toSet());
            int solved = seat.map(roomUser -> (int) answerRepository.findByRoomUserId(roomUser.getId()).stream()
                    .filter(answer -> Boolean.TRUE.equals(answer.getCorrect()) && playableIds.contains(answer.getCtfEntity().getId()))
                    .count()).orElse(0);
            boolean canRun = currentUser.isAuthenticated() && currentUser.canRun(room);
            Team team = viewer != null ? viewer.getTeam() : null;
            cards.add(new LabCard(room.getId(), room.getName(), ScenarioText.summary(room.getScenario()),
                    room.effectiveMode(), labEnvironmentService.view(room, viewer, canRun, false), challenges.size(),
                    players.size(), seat.map(roomUser -> value(roomUser.getPoints())).orElse(0), solved,
                    canRun, team != null ? team.getId() : null, team != null ? team.getName() : null));
        }
        cards.sort(Comparator.comparing(LabCard::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return cards;
    }

    /** The lab's environment for the viewer, for refreshing the environment bar alone. */
    public EnvironmentView environment(String roomId, String viewerId) {
        Room room = roomService.findByRoomId(roomId);
        User viewer = Optional.ofNullable(viewerId).flatMap(userRepository::findById).orElse(null);
        return labEnvironmentService.view(room, viewer, currentUser.isAuthenticated() && currentUser.canRun(room));
    }

    public RoomStandings standings(String roomId) {
        Room room = roomService.findByRoomId(roomId);
        List<RoomUser> ranked = rank(players(roomId));
        Map<String, List<CTFEntityAnswer>> answersByPlayer = answerRepository.findByRoomUserRoomId(roomId).stream()
                .collect(Collectors.groupingBy(answer -> answer.getRoomUser().getId()));
        Instant hourAgo = toInstant(LocalDateTime.now().minusHours(1));

        List<RoomStandings.Player> players = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            RoomUser roomUser = ranked.get(i);
            List<CTFEntityAnswer> answers = answersByPlayer.getOrDefault(roomUser.getId(), List.of());
            List<RoomStandings.Point> history = history(roomUser);
            Team team = roomUser.getUser().getTeam();
            Map<String, Integer> byCategory = new TreeMap<>();
            answers.stream().filter(answer -> Boolean.TRUE.equals(answer.getCorrect()))
                    .forEach(answer -> byCategory.merge(categoryOf(answer.getCtfEntity()), 1, Integer::sum));
            players.add(new RoomStandings.Player(
                    i + 1,
                    roomUser.getUser().getId(),
                    roomUser.getUser().getUsername(),
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    value(roomUser.getPoints()),
                    (int) answers.stream().filter(answer -> Boolean.TRUE.equals(answer.getCorrect())).count(),
                    answers.stream().mapToInt(answer -> orEmpty(answer.getHintUsages()).size()).sum(),
                    answers.stream().mapToInt(RoomPlayService::wrongAttempts).sum(),
                    answers.stream().map(CTFEntityAnswer::getSolvedAt).filter(Objects::nonNull)
                            .max(Comparator.naturalOrder()).map(RoomPlayService::toInstant).orElse(null),
                    value(roomUser.getPoints()) - totalAt(history, hourAgo),
                    byCategory,
                    history));
        }

        return new RoomStandings(room.getId(), room.getName(),
                room.getCreatedAt() != null ? toInstant(room.getCreatedAt()) : null, players,
                teams(players, teamSizes()));
    }

    public RoomLive live(String roomId) {
        Room room = roomService.findByRoomId(roomId);
        List<RoomUser> players = rank(players(roomId));
        List<CTFEntity> challenges = playable(ctfEntityService.findCTFEntitiesByRoomId(roomId));
        List<CTFEntityAnswer> answers = answerRepository.findByRoomUserRoomId(roomId);
        LocalDateTime since = LocalDateTime.now().minus(RECENT);

        int solvesRecent = 0;
        int solvesTotal = 0;
        int wrongRecent = 0;
        int hintsUnlocked = 0;
        int hintPoints = 0;
        Map<String, Integer> wrongRecentByChallenge = new HashMap<>();
        List<RoomLive.FeedItem> feed = new ArrayList<>();

        for (CTFEntityAnswer answer : answers) {
            User user = answer.getRoomUser().getUser();
            String team = teamName(answer.getRoomUser());
            CTFEntity challenge = answer.getCtfEntity();
            if (Boolean.TRUE.equals(answer.getCorrect())) {
                solvesTotal++;
                if (answer.getSolvedAt() != null && answer.getSolvedAt().isAfter(since)) {
                    solvesRecent++;
                }
                if (answer.getSolvedAt() != null) {
                    feed.add(new RoomLive.FeedItem(RoomLive.FeedKind.SOLVE, toInstant(answer.getSolvedAt()),
                            user.getUsername(), team, "solved " + challenge.getQuestion(), null, value(answer.getScore())));
                }
            }
            int wrong = recentWrongAttempts(answer, since);
            wrongRecent += wrong;
            if (wrong > 0) {
                wrongRecentByChallenge.merge(challenge.getId(), wrong, Integer::sum);
            }
            for (CTFEntityHintUsage usage : orEmpty(answer.getHintUsages())) {
                hintsUnlocked++;
                hintPoints += value(usage.getPointsDeducted());
                feed.add(new RoomLive.FeedItem(RoomLive.FeedKind.HINT, toInstant(usage.getUsedAt()), user.getUsername(),
                        team, "unlocked hint " + value(usage.getUsageOrder()) + " on " + challenge.getQuestion(), null,
                        -value(usage.getPointsDeducted())));
            }
        }

        for (RoomUser player : players) {
            for (PointsHistoryEntry entry : orEmpty(player.getPointsHistory())) {
                if (entry.getAwardedBy() != null) {
                    feed.add(new RoomLive.FeedItem(RoomLive.FeedKind.AWARD, toInstant(entry.getTimestamp()),
                            entry.getAwardedBy(), teamName(player), "awarded " + player.getUser().getUsername(),
                            entry.getReason(), value(entry.getPointsChange())));
                }
            }
        }
        feed.sort(Comparator.comparing(RoomLive.FeedItem::at, Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, String> titles = challenges.stream().collect(Collectors.toMap(CTFEntity::getId, CTFEntity::getQuestion));
        RoomLive.TopWrong topWrong = wrongRecentByChallenge.entrySet().stream()
                .filter(entry -> titles.containsKey(entry.getKey()))
                .max(Map.Entry.comparingByValue())
                .map(entry -> new RoomLive.TopWrong(titles.get(entry.getKey()), entry.getValue()))
                .orElse(null);

        Map<String, List<CTFEntityAnswer>> byChallenge = answers.stream()
                .collect(Collectors.groupingBy(answer -> answer.getCtfEntity().getId()));
        List<RoomLive.ChallengeHealth> health = challenges.stream().map(challenge -> {
            List<CTFEntityAnswer> forChallenge = byChallenge.getOrDefault(challenge.getId(), List.of());
            return new RoomLive.ChallengeHealth(
                    challenge.getId(),
                    challenge.getQuestion(),
                    (int) forChallenge.stream().filter(answer -> Boolean.TRUE.equals(answer.getCorrect())).count(),
                    forChallenge.stream().mapToInt(RoomPlayService::wrongAttempts).sum(),
                    (int) forChallenge.stream().filter(answer -> value(answer.getAttempts()) > 0).count(),
                    forChallenge.stream().mapToInt(answer -> orEmpty(answer.getHintUsages()).size()).sum());
        }).sorted(Comparator.comparingInt(RoomLive.ChallengeHealth::wrongAttempts).reversed()
                .thenComparing(RoomLive.ChallengeHealth::title, Comparator.nullsLast(Comparator.naturalOrder()))).toList();

        List<RoomLive.PlayerRef> refs = players.stream().map(player -> new RoomLive.PlayerRef(
                player.getUser().getId(), player.getUser().getUsername(), teamName(player),
                value(player.getPoints()))).toList();

        return new RoomLive(room.getId(), room.getName(), players.size(), teamCount(players), solvesRecent,
                solvesTotal, wrongRecent, topWrong, hintsUnlocked, hintPoints,
                feed.size() > FEED_LIMIT ? feed.subList(0, FEED_LIMIT) : feed, health, refs);
    }

    /**
     * Questions that use a lab definition. Authors and admins see all of them; everyone else only sees
     * released questions in labs they can see.
     */
    public List<LabChallenge> forLab(String labId, String viewerId, boolean seeAll) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Optional<RoomUser>> seats = new HashMap<>();
        Optional<User> viewer = Optional.ofNullable(viewerId).flatMap(userRepository::findById);
        List<LabChallenge> result = new ArrayList<>();
        // Questions linked to the environment directly, then those of the labs that run it
        Map<String, CTFEntity> uses = new LinkedHashMap<>();
        ctfEntityService.findByLabId(labId).forEach(challenge -> uses.put(challenge.getId(), challenge));
        roomService.findAllRooms().stream().filter(room -> labId.equals(room.getEnvironmentId()))
                .flatMap(room -> ctfEntityService.findCTFEntitiesByRoomId(room.getId()).stream())
                .forEach(challenge -> uses.putIfAbsent(challenge.getId(), challenge));
        for (CTFEntity challenge : uses.values()) {
            Room room = challenge.getRoom();
            if (room == null) {
                continue;
            }
            Optional<RoomUser> seat = seats.computeIfAbsent(room.getId(), id -> viewer
                    .flatMap(user -> roomUserService.findByUserAndRoom(user, room)));
            ChallengeState state = stateOf(challenge, now);
            boolean released = !Boolean.FALSE.equals(challenge.getVisible()) && !Boolean.FALSE.equals(challenge.getIsActive())
                    && state != ChallengeState.LOCKED;
            if (!seeAll && !(room.isLive() && released)) {
                continue;
            }
            boolean solved = seat.flatMap(roomUser -> answerRepository
                            .findByRoomUserIdAndCtfEntityId(roomUser.getId(), challenge.getId()))
                    .map(answer -> Boolean.TRUE.equals(answer.getCorrect())).orElse(false);
            result.add(new LabChallenge(challenge.getId(), challenge.getQuestion(), challenge.getQuestion(), room.getId(),
                    room.getName(), categoryOf(challenge),
                    challenge.getDifficultyLevel() != null ? challenge.getDifficultyLevel().name() : null,
                    value(challenge.getPoints()), state, solved));
        }
        return result;
    }

    private RoomOverview.Challenge challengeView(CTFEntity challenge, CTFEntityAnswer answer, long solves,
                                                 LocalDateTime now, Map<String, LabFileView> files, List<String> teammates) {
        ChallengeState state = stateOf(challenge, now);
        List<RoomOverview.HintView> hints = new ArrayList<>();
        List<Hint> ordered = orEmpty(challenge.getHints()).stream()
                .sorted(Comparator.comparing(Hint::getOrderIndex, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (int i = 0; i < ordered.size(); i++) {
            Hint hint = ordered.get(i);
            Optional<CTFEntityHintUsage> usage = answer == null ? Optional.empty() : orEmpty(answer.getHintUsages()).stream()
                    .filter(used -> used.getHint().getId().equals(hint.getId())).findFirst();
            hints.add(new RoomOverview.HintView(hint.getId(), i + 1, value(hint.getCost()),
                    value(hint.getUnlockAfterAttempts()), usage.isPresent(),
                    usage.map(used -> hint.getHint()).orElse(null),
                    usage.map(used -> toInstant(used.getUsedAt())).orElse(null)));
        }
        return new RoomOverview.Challenge(
                challenge.getId(),
                challenge.getQuestion(),
                categoryOf(challenge),
                challenge.getDifficultyLevel() != null ? challenge.getDifficultyLevel().name() : null,
                value(challenge.getPoints()),
                challenge.getAuthor(),
                challenge.getMaxAttempts(),
                solves,
                state,
                toInstant(challenge.getReleaseDate()),
                toInstant(challenge.getExpirationDate()),
                state == ChallengeState.LOCKED ? null : challenge.getDescription(),
                state == ChallengeState.LOCKED ? List.of() : orEmpty(challenge.getAttachments()).stream()
                        .map(name -> {
                            LabFileView file = files.get(name);
                            return new RoomOverview.Attachment(name, file != null ? file.id() : null,
                                    file != null ? file.size() : null);
                        }).toList(),
                state == ChallengeState.LOCKED ? List.of() : hints,
                answer != null ? value(answer.getAttempts()) : 0,
                answer != null && Boolean.TRUE.equals(answer.getCorrect()),
                answer != null ? toInstant(answer.getSolvedAt()) : null,
                teammates);
    }

    /** Whether flags and hints are accepted for a challenge right now. */
    public static ChallengeState stateOf(CTFEntity challenge, LocalDateTime now) {
        if (challenge.getReleaseDate() != null && now.isBefore(challenge.getReleaseDate())) {
            return ChallengeState.LOCKED;
        }
        if (challenge.getExpirationDate() != null && now.isAfter(challenge.getExpirationDate())) {
            return ChallengeState.EXPIRED;
        }
        return ChallengeState.OPEN;
    }

    private RoomOverview.RoomInfo roomInfo(Room room, boolean canRun) {
        String creator = room.getCreator() != null ? room.getCreator().getUsername() : null;
        List<String> facilitators = orEmpty(room.getFacilitators()).stream()
                .map(entry -> userRepository.findById(entry).map(User::getUsername).orElse(entry))
                .toList();
        return new RoomOverview.RoomInfo(room.getId(), room.getName(), room.getSurroundTag(), creator, facilitators,
                room.getScenario(), room.effectiveMode(), canRun, room.effectiveTeamCredit());
    }

    /** Everyone who answered a question or unlocked a hint in the lab. */
    private List<RoomUser> players(String roomId) {
        return roomUserService.findByRoomIdOrderByPointsDesc(roomId).stream()
                .filter(roomUser -> roomUser.getRoomUserStatus() != RoomUserStatus.KICKED
                        && roomUser.getRoomUserStatus() != RoomUserStatus.BANNED)
                .toList();
    }

    private static List<CTFEntity> playable(List<CTFEntity> challenges) {
        return challenges.stream()
                .filter(challenge -> !Boolean.FALSE.equals(challenge.getVisible())
                        && !Boolean.FALSE.equals(challenge.getIsActive()))
                .sorted(CTFEntityService.ROOM_ORDER)
                .toList();
    }

    /** Points first; equal points go to whoever reached them first. */
    static List<RoomUser> rank(List<RoomUser> players) {
        return players.stream().sorted(Comparator
                        .comparing((RoomUser player) -> value(player.getPoints())).reversed()
                        .thenComparing(RoomPlayService::reachedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(player -> player.getUser().getUsername(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static LocalDateTime reachedAt(RoomUser player) {
        List<PointsHistoryEntry> history = player.getPointsHistory();
        return history == null || history.isEmpty() ? null : history.getLast().getTimestamp();
    }

    private static List<RoomStandings.Point> history(RoomUser player) {
        List<RoomStandings.Point> points = new ArrayList<>();
        List<PointsHistoryEntry> entries = orEmpty(player.getPointsHistory());
        // History times are stored to the second, so the starting zero must not land after them
        LocalDateTime start = player.getCreatedAt() != null ? player.getCreatedAt().truncatedTo(ChronoUnit.SECONDS) : null;
        if (!entries.isEmpty() && entries.getFirst().getTimestamp() != null
                && (start == null || entries.getFirst().getTimestamp().isBefore(start))) {
            start = entries.getFirst().getTimestamp();
        }
        if (start != null) {
            points.add(new RoomStandings.Point(toInstant(start), 0));
        }
        for (PointsHistoryEntry entry : entries) {
            points.add(new RoomStandings.Point(toInstant(entry.getTimestamp()), value(entry.getTotalPoints())));
        }
        return points;
    }

    private static int totalAt(List<RoomStandings.Point> history, Instant at) {
        int total = 0;
        for (RoomStandings.Point point : history) {
            if (point.at() != null && point.at().isAfter(at)) {
                break;
            }
            total = point.total();
        }
        return total;
    }

    /** Members of each team, whether or not they played this lab. */
    private Map<String, Integer> teamSizes() {
        Map<String, Integer> sizes = new HashMap<>();
        userRepository.findAll().stream().filter(user -> user.getTeam() != null)
                .forEach(user -> sizes.merge(user.getTeam().getId(), 1, Integer::sum));
        return sizes;
    }

    private static List<RoomStandings.TeamRow> teams(List<RoomStandings.Player> players, Map<String, Integer> sizes) {
        Map<String, List<RoomStandings.Player>> byTeam = new LinkedHashMap<>();
        players.stream().filter(player -> player.teamId() != null)
                .forEach(player -> byTeam.computeIfAbsent(player.teamId(), key -> new ArrayList<>()).add(player));

        List<RoomStandings.TeamRow> rows = byTeam.values().stream().map(members -> {
            RoomStandings.Player first = members.getFirst();
            return new RoomStandings.TeamRow(0, first.teamId(), first.teamName(), members.size(),
                    Math.max(members.size(), sizes.getOrDefault(first.teamId(), 0)),
                    members.stream().mapToInt(RoomStandings.Player::points).sum(),
                    members.stream().mapToInt(RoomStandings.Player::solves).sum(),
                    members.stream().mapToInt(RoomStandings.Player::hintsUsed).sum(),
                    members.stream().map(RoomStandings.Player::lastSolveAt).filter(Objects::nonNull)
                            .max(Comparator.naturalOrder()).orElse(null),
                    members.stream().mapToInt(RoomStandings.Player::lastHourChange).sum(),
                    teamHistory(members));
        }).sorted(Comparator.comparingInt(RoomStandings.TeamRow::points).reversed()
                .thenComparing(row -> row.history().isEmpty() ? null : row.history().getLast().at(),
                        Comparator.nullsLast(Comparator.naturalOrder()))).toList();

        List<RoomStandings.TeamRow> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            RoomStandings.TeamRow row = rows.get(i);
            ranked.add(new RoomStandings.TeamRow(i + 1, row.teamId(), row.name(), row.members(), row.teamSize(), row.points(),
                    row.solves(), row.hintsUsed(), row.lastSolveAt(), row.lastHourChange(), row.history()));
        }
        return ranked;
    }

    /** The team's total after every change by any member. */
    private static List<RoomStandings.Point> teamHistory(List<RoomStandings.Player> members) {
        record Change(Instant at, String member, int total) { }
        List<Change> changes = new ArrayList<>();
        for (RoomStandings.Player member : members) {
            for (RoomStandings.Point point : member.history()) {
                changes.add(new Change(point.at(), member.userId(), point.total()));
            }
        }
        changes.sort(Comparator.comparing(Change::at, Comparator.nullsFirst(Comparator.naturalOrder())));
        Map<String, Integer> current = new HashMap<>();
        List<RoomStandings.Point> history = new ArrayList<>();
        for (Change change : changes) {
            current.put(change.member(), change.total());
            history.add(new RoomStandings.Point(change.at(),
                    current.values().stream().mapToInt(Integer::intValue).sum()));
        }
        return history;
    }

    private static int wrongAttempts(CTFEntityAnswer answer) {
        return Math.max(0, value(answer.getAttempts()) - (Boolean.TRUE.equals(answer.getCorrect()) ? 1 : 0));
    }

    private static int recentWrongAttempts(CTFEntityAnswer answer, LocalDateTime since) {
        int recent = (int) orEmpty(answer.getAttemptTimes()).stream().filter(time -> time != null && time.isAfter(since)).count();
        boolean solvedRecently = answer.getSolvedAt() != null && answer.getSolvedAt().isAfter(since);
        return Math.max(0, recent - (solvedRecently ? 1 : 0));
    }

    private static int teamCount(List<RoomUser> players) {
        return (int) players.stream().map(RoomPlayService::teamId).filter(Objects::nonNull).distinct().count();
    }

    private static String teamId(RoomUser player) {
        Team team = player.getUser() != null ? player.getUser().getTeam() : null;
        return team != null ? team.getId() : null;
    }

    private static String teamName(RoomUser player) {
        Team team = player.getUser() != null ? player.getUser().getTeam() : null;
        return team != null ? team.getName() : null;
    }

    private static String categoryOf(CTFEntity challenge) {
        return challenge.getCategory() == null || challenge.getCategory().isBlank() ? "General" : challenge.getCategory();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }

    static Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant();
    }
}
