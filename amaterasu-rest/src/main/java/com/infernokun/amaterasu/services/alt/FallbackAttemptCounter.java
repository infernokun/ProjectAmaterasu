package com.infernokun.amaterasu.services.alt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

/**
 * Uses the primary counter and switches to the fallback while it is unreachable, so logins keep working.
 * After a failure the primary is skipped for a while, so only one request waits for its timeout.
 */
@Slf4j
public class FallbackAttemptCounter implements AttemptCounter {
    static final Duration RETRY_AFTER = Duration.ofSeconds(30);

    private final AttemptCounter primary;
    private final AttemptCounter fallback;
    private final Clock clock;
    private volatile Instant skipPrimaryUntil = Instant.MIN;

    public FallbackAttemptCounter(AttemptCounter primary, AttemptCounter fallback, Clock clock) {
        this.primary = primary;
        this.fallback = fallback;
        this.clock = clock;
    }

    @Override
    public long increment(String key, Duration window) {
        return call(counter -> counter.increment(key, window));
    }

    @Override
    public long count(String key) {
        return call(counter -> counter.count(key));
    }

    @Override
    public void reset(String key) {
        call(counter -> {
            counter.reset(key);
            return 0L;
        });
    }

    private long call(Function<AttemptCounter, Long> action) {
        if (clock.instant().isBefore(skipPrimaryUntil)) {
            return action.apply(fallback);
        }
        try {
            return action.apply(primary);
        } catch (DataAccessException e) {
            skipPrimaryUntil = clock.instant().plus(RETRY_AFTER);
            log.warn("Rate limit store unavailable, counting in memory for {}s: {}", RETRY_AFTER.toSeconds(),
                    e.getMessage());
            return action.apply(fallback);
        }
    }
}
