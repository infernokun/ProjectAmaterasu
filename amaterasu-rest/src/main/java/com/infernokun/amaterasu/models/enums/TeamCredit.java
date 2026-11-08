package com.infernokun.amaterasu.models.enums;

/**
 * How a solve counts in a team. Points always go to the player who answered; with SHARED_SOLVES a
 * question one member solved is solved for the whole team.
 */
public enum TeamCredit {
    SEE_TEAMMATES,
    SHARED_SOLVES
}
