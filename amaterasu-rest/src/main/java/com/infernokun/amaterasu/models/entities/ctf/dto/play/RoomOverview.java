package com.infernokun.amaterasu.models.entities.ctf.dto.play;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.TeamCredit;
import com.infernokun.amaterasu.models.enums.TeamMode;

import java.time.Instant;
import java.util.List;

/**
 * A lab as a player sees it: its scenario, its environment, its questions with how many solved
 * each, the files to download, and the viewer's own progress. {@code environment} is null for a lab
 * with nothing to run.
 */
public record RoomOverview(
        RoomInfo room,
        int playerCount,
        int teamCount,
        Me me,
        List<Challenge> challenges,
        EnvironmentView environment,
        List<LabFileView> files) {

    /** {@code canRun} is true for the lab's facilitators and admins. */
    public record RoomInfo(String id, String name, String surroundTag, String creator, List<String> facilitators,
                           String scenario, EnvironmentMode environmentMode, boolean canRun, TeamCredit teamCredit,
                           TeamMode teamMode) {
    }

    /** The viewer's standing in this lab and their team, if they are on one. Rank is 0 before they score. */
    public record Me(int points, int rank, int solved, String teamId, String teamName) {
    }

    public enum ChallengeState { OPEN, LOCKED, EXPIRED }

    public record Challenge(
            String id,
            String title,
            String category,
            String difficulty,
            int points,
            String author,
            Integer maxAttempts,
            long solves,
            ChallengeState state,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant releaseDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expirationDate,
            /* Left out while the challenge is locked. Markdown. */
            String description,
            List<Attachment> attachments,
            List<HintView> hints,
            int attempts,
            boolean solved,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant solvedAt,
            /* Teammates who solved it; with SHARED_SOLVES that solves it for the viewer too. */
            List<String> solvedByTeammates) {
    }

    /** A file a question names; {@code fileId} is set when the lab has a file by that name to download. */
    public record Attachment(String name, String fileId, Long size) {
    }

    /** The hint text is only sent once the viewer has unlocked it. */
    public record HintView(String id, int number, int cost, int unlockAfterAttempts, boolean unlocked, String text,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) Instant usedAt) {
    }
}
