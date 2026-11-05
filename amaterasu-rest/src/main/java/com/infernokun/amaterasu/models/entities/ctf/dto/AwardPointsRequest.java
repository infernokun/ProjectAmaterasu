package com.infernokun.amaterasu.models.entities.ctf.dto;

/** Points to add (or take away, when negative) and the reason the player sees. */
public record AwardPointsRequest(Integer points, String reason) {
}
