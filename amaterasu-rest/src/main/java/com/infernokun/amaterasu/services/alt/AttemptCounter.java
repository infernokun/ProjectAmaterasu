package com.infernokun.amaterasu.services.alt;

import java.time.Duration;

/** Fixed-window counters for login and rate limits. */
public interface AttemptCounter {
    /** Adds one and returns the count in the current window, which starts at the first hit. */
    long increment(String key, Duration window);

    long count(String key);

    void reset(String key);
}
