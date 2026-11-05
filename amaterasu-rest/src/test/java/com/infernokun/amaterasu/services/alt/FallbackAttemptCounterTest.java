package com.infernokun.amaterasu.services.alt;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackAttemptCounterTest {

    private static class Unreachable implements AttemptCounter {
        int calls;
        @Override public long increment(String key, Duration window) { calls++; throw new QueryTimeoutException("down"); }
        @Override public long count(String key) { calls++; throw new QueryTimeoutException("down"); }
        @Override public void reset(String key) { calls++; throw new QueryTimeoutException("down"); }
    }

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void countsInMemoryWhileThePrimaryIsDown() {
        MutableClock clock = new MutableClock();
        Unreachable primary = new Unreachable();
        AttemptCounter counter = new FallbackAttemptCounter(primary, new MemoryAttemptCounter(clock), clock);

        assertThat(counter.increment("k", Duration.ofMinutes(5))).isEqualTo(1);
        assertThat(counter.increment("k", Duration.ofMinutes(5))).isEqualTo(2);
        assertThat(counter.count("k")).isEqualTo(2);
        // Only the first call waited on the primary
        assertThat(primary.calls).isEqualTo(1);

        clock.now = clock.now.plus(FallbackAttemptCounter.RETRY_AFTER).plusSeconds(1);
        counter.count("k");
        assertThat(primary.calls).isEqualTo(2);
        counter.reset("k");
        assertThat(counter.count("k")).isZero();
    }

    @Test
    void usesThePrimaryWhenItWorks() {
        Clock clock = Clock.systemUTC();
        MemoryAttemptCounter primary = new MemoryAttemptCounter(clock);
        MemoryAttemptCounter fallback = new MemoryAttemptCounter(clock);
        new FallbackAttemptCounter(primary, fallback, clock).increment("k", Duration.ofMinutes(1));

        assertThat(primary.count("k")).isEqualTo(1);
        assertThat(fallback.count("k")).isZero();
    }
}
