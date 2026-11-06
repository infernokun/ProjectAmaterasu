package com.infernokun.amaterasu.models;

/**
 * A connection test. {@code reason} says why a server cannot be used; {@code warning} what a usable
 * one cannot do yet, such as deploy labs with a read-only token.
 */
public record ServerCheck(boolean connected, String reason, String warning) {
    public static ServerCheck ok() {
        return new ServerCheck(true, null, null);
    }

    public static ServerCheck failed(String reason) {
        return new ServerCheck(false, reason, null);
    }

    public static ServerCheck warning(String warning) {
        return new ServerCheck(true, null, warning);
    }
}
