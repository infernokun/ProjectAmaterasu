package com.infernokun.amaterasu.models.entities.ctf.dto.play;

import com.infernokun.amaterasu.models.enums.RoomUserStatus;

/** A player's place in a lab: joined or not, and their team there, if any. */
public record LabSeat(String roomId, String userId, RoomUserStatus status, int points, String teamId, String teamName) {
}
