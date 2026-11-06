package com.infernokun.amaterasu.services.alt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Counters for a single instance; they reset when the API restarts. */
public class MemoryAttemptCounter implements AttemptCounter {
    private static final int PRUNE_ABOVE = 10_000;

    private record Window(long count, Instant expiresAt) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public MemoryAttemptCounter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long increment(String key, Duration window) {
        Instant now = clock.instant();
        if (windows.size() > PRUNE_ABOVE) {
            windows.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
        }
        return windows.compute(key, (k, current) -> current == null || !current.expiresAt().isAfter(now)
                ? new Window(1, now.plus(window))
                : new Window(current.count() + 1, current.expiresAt())).count();
    }

    @Override
    public long count(String key) {
        Window current = windows.get(key);
        if (current == null) {
            return 0;
        }
        if (!current.expiresAt().isAfter(clock.instant())) {
            windows.remove(key, current);
            return 0;
        }
        return current.count();
    }

    @Override
    public void reset(String key) {
        windows.remove(key);
    }
}
