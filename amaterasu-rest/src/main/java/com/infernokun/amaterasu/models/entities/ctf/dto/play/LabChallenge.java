package com.infernokun.amaterasu.models.entities.ctf.dto.play;

import com.infernokun.amaterasu.models.entities.ctf.dto.play.RoomOverview.ChallengeState;

/**
 * A challenge that uses a lab, as players may see it: no flags, hints or solution. Title and
 * question carry the same text.
 */
public record LabChallenge(
        String id,
        String title,
        String question,
        String roomId,
        String roomName,
        String category,
        String difficulty,
        int points,
        ChallengeState state,
        boolean solved) {
}
