package com.infernokun.amaterasu.models.entities.ctf.dto.play;

import java.time.Instant;
import java.util.List;

/** What facilitators watch during an event: recent activity, challenge health and the players to award. */
public record RoomLive(
        String roomId,
        String roomName,
        int playerCount,
        int teamCount,
        int solvesRecent,
        int solvesTotal,
        int wrongRecent,
        TopWrong topWrong,
        int hintsUnlocked,
        int hintPoints,
        List<FeedItem> feed,
        List<ChallengeHealth> challenges,
        List<PlayerRef> players) {

    public record TopWrong(String title, int count) {
    }

    public enum FeedKind { SOLVE, HINT, AWARD }

    public record FeedItem(FeedKind kind, Instant at, String who, String team, String text, String detail,
                           int points) {
    }

    public record ChallengeHealth(String id, String title, int solves, int wrongAttempts, int attemptedBy,
                                  int hintsUsed) {
    }

    public record PlayerRef(String userId, String username, String teamName, int points) {
    }
}
