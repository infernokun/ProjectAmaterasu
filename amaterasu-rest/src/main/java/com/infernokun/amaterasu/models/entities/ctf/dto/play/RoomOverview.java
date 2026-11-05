package com.infernokun.amaterasu.models.entities.ctf.dto.play;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

/** A room as a player sees it: its challenges, how many solved each, and the viewer's own progress. */
public record RoomOverview(
        RoomInfo room,
        int playerCount,
        int teamCount,
        Me me,
        List<Challenge> challenges) {

    public record RoomInfo(String id, String name, String surroundTag, String creator, List<String> facilitators) {
    }

    /** The viewer's standing; joined is false when they have not joined the room. */
    public record Me(boolean joined, int points, int rank, int solved) {
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
            /* Left out while the challenge is locked. */
            String description,
            List<String> attachments,
            List<HintView> hints,
            int attempts,
            boolean solved,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant solvedAt) {
    }

    /** The hint text is only sent once the viewer has unlocked it. */
    public record HintView(String id, int number, int cost, int unlockAfterAttempts, boolean unlocked, String text,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) Instant usedAt) {
    }
}
