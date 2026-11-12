package com.infernokun.amaterasu.models.entities.ctf.dto.manage;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.infernokun.amaterasu.models.PointsHistoryEntry;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.EnvironmentView.EnvironmentCopy;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.TeamCredit;
import com.infernokun.amaterasu.models.enums.TeamMode;

import java.time.Instant;
import java.util.List;

/** What admins and a lab's facilitators see when they manage one lab. Times are ISO instants. */
public final class LabManage {
    private LabManage() {
    }

    /**
     * The lab at a glance. {@code activePlayers} did something in the last 15 minutes; {@code
     * solvesOverTime} counts solves and attempts per {@code bucketMinutes}, oldest first.
     */
    public record Overview(
            String roomId,
            String roomName,
            TeamMode teamMode,
            TeamCredit teamCredit,
            EnvironmentMode environmentMode,
            int players,
            int activePlayers,
            int teams,
            int questions,
            int solves,
            int attempts,
            int wrongAttempts,
            int nearMisses,
            int hintsUsed,
            int hintPoints,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant firstActivityAt,
            int bucketMinutes,
            List<TimeBucket> solvesOverTime,
            List<QuestionRate> questionRates,
            List<EnvironmentCopy> copies) {
    }

    public record TimeBucket(@JsonFormat(shape = JsonFormat.Shape.STRING) Instant at, int solves, int attempts) {
    }

    /** {@code solveRate} is the percent of the lab's players who solved it. */
    public record QuestionRate(String id, String title, int points, int solves, int attemptedBy, int attempts,
                               int solveRate, int hintsUsed) {
    }

    /** The flag a wrong answer nearly matched and why. */
    public record NearMissView(String flagId, String flag, List<String> reasons) {
    }

    /**
     * One attempt. {@code id} is the answer's id and the attempt's position, "answerId:index", which
     * the credit action takes. {@code creditedBy} is set when staff gave credit for this attempt;
     * {@code questionSolved} says whether the player has the question now.
     */
    public record Submission(
            String id,
            String answerId,
            int index,
            String userId,
            String username,
            String teamId,
            String teamName,
            String questionId,
            String questionTitle,
            int questionPoints,
            String flag,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant at,
            boolean correct,
            String creditedBy,
            boolean questionSolved,
            NearMissView nearMiss) {
    }

    public record SubmissionPage(List<Submission> items, int page, int size, long total, int nearMisses) {
    }

    public record PlayerRow(
            String userId,
            String username,
            String displayName,
            String teamId,
            String teamName,
            int points,
            int solves,
            int attempts,
            int wrongAttempts,
            int hintsUsed,
            int hintPoints,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant firstActivityAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastActivityAt) {
    }

    /** {@code points} adds the members' points and awards to the team as a whole ({@code awardPoints}). */
    public record TeamRow(String teamId, String name, List<String> members, int points, int awardPoints, int solves,
                          int attempts, int hintsUsed) {
    }

    public record Players(List<PlayerRow> players, List<TeamRow> teams) {
    }

    /** A player's state on one question. {@code solvedByTeammate} names who solved it for a shared-solves team. */
    public record QuestionProgress(String questionId, String title, int points, boolean solved,
                                   @JsonFormat(shape = JsonFormat.Shape.STRING) Instant solvedAt, int score,
                                   int attempts, int hintsUsed, int hintPoints, String creditedBy,
                                   String solvedByTeammate) {
    }

    public record PlayerDetail(PlayerRow player, List<QuestionProgress> questions, List<Submission> submissions,
                               List<PointsHistoryEntry> history) {
    }

    /** A wrong answer, how often it was given and by how many players. */
    public record WrongAnswer(String answer, int count, int players, NearMissView nearMiss) {
    }

    public record QuestionStats(String id, String title, String category, int points, int attempts, int attemptedBy,
                                int solves, int solveRate, int hintsUsed, int nearMisses, List<WrongAnswer> wrongAnswers) {
    }

    public enum ActivityKind { FIRST_ACTIVITY, HINT, SOLVE, CREDIT, AWARD, TEAM_AWARD, RESET, ACCEPTED_ANSWER, TEAMS, ENVIRONMENT }

    public record ActivityItem(ActivityKind kind, @JsonFormat(shape = JsonFormat.Shape.STRING) Instant at, String who,
                               String team, String text, String detail, Integer points) {
    }
}
