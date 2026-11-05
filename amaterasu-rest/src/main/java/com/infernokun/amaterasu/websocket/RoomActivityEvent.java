package com.infernokun.amaterasu.websocket;

/** Published when points change in a room (a solve, a hint, an award) or a flag is tried. */
public record RoomActivityEvent(String roomId) {
}
