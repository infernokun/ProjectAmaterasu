package com.infernokun.amaterasu.models.entities.ctf.dto.play;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Players and teams in a room, ranked by points; ties go to whoever reached the score first. */
public record RoomStandings(String roomId, String roomName, List<Player> players, List<TeamRow> teams) {

    public record Point(@JsonFormat(shape = JsonFormat.Shape.STRING) Instant at, int total) {
    }

    public record Player(
            int rank,
            String userId,
            String username,
            String teamId,
            String teamName,
            int points,
            int solves,
            int hintsUsed,
            int wrongAttempts,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastSolveAt,
            int lastHourChange,
            Map<String, Integer> solvesByCategory,
            List<Point> history) {
    }

    public record TeamRow(
            int rank,
            String teamId,
            String name,
            int members,
            int points,
            int solves,
            int hintsUsed,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastSolveAt,
            int lastHourChange,
            List<Point> history) {
    }
}
