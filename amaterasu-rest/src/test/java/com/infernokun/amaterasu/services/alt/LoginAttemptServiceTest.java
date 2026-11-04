package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.exceptions.TooManyAttemptsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void locksAUsernameUntilTheWindowEnds() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(clock);

        for (int i = 0; i < LoginAttemptService.MAX_PER_USERNAME; i++) {
            service.checkAllowed("Alice", "10.0.0.1");
            service.recordFailure("alice", "10.0.0.1");
        }
        assertThatThrownBy(() -> service.checkAllowed("ALICE", "10.0.0.2")).isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> service.checkAllowed("bob", "10.0.0.2")).doesNotThrowAnyException();

        clock.now = clock.now.plus(LoginAttemptService.WINDOW).plusSeconds(1);
        assertThatCode(() -> service.checkAllowed("alice", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void successClearsTheUsernameCount() {
        LoginAttemptService service = new LoginAttemptService(new MutableClock());
        for (int i = 0; i < LoginAttemptService.MAX_PER_USERNAME - 1; i++) {
            service.recordFailure("carol", "10.0.0.1");
        }
        service.recordSuccess("carol");
        service.recordFailure("carol", "10.0.0.1");
        assertThatCode(() -> service.checkAllowed("carol", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void throttleCountsEveryCall() {
        LoginAttemptService service = new LoginAttemptService(new MutableClock());
        for (int i = 0; i < 3; i++) {
            service.throttle("register", "10.0.0.9", 3, Duration.ofMinutes(10));
        }
        assertThatThrownBy(() -> service.throttle("register", "10.0.0.9", 3, Duration.ofMinutes(10)))
                .isInstanceOf(TooManyAttemptsException.class);
    }
}
