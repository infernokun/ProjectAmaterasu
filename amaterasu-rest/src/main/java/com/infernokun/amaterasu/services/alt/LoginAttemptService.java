package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.exceptions.TooManyAttemptsException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing and signup or refresh floods. Failed logins are counted per username
 * and per client address over a window; past the limit further attempts are refused until the window
 * ends. In memory, so the counters reset when the API restarts.
 */
@Service
public class LoginAttemptService {
    static final int MAX_PER_USERNAME = 5;
    // A classroom often shares one address, so the address limit is generous
    static final int MAX_PER_ADDRESS = 50;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private record Failures(int count, Instant windowStart) {
    }

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public void checkAllowed(String username, String address) {
        if (blocked(userKey(username), MAX_PER_USERNAME) || blocked(addressKey(address), MAX_PER_ADDRESS)) {
            throw new TooManyAttemptsException("Too many failed logins, try again in " + WINDOW.toMinutes() + " minutes");
        }
    }

    public void recordFailure(String username, String address) {
        increment(userKey(username));
        increment(addressKey(address));
    }

    public void recordSuccess(String username) {
        failures.remove(userKey(username));
    }

    /** Counts every call, successful or not, and refuses once an address passes the limit for an action. */
    public void throttle(String action, String address, int limit, Duration window) {
        String key = "rate:" + action + ":" + address;
        Instant now = clock.instant();
        Failures updated = failures.compute(key, (k, current) ->
                current == null || current.windowStart().plus(window).isBefore(now)
                        ? new Failures(1, now) : new Failures(current.count() + 1, current.windowStart()));
        if (updated.count() > limit) {
            throw new TooManyAttemptsException("Too many requests, try again later");
        }
    }

    private boolean blocked(String key, int limit) {
        Failures current = failures.get(key);
        if (current == null) {
            return false;
        }
        if (expired(current)) {
            failures.remove(key, current);
            return false;
        }
        return current.count() >= limit;
    }

    private void increment(String key) {
        Instant now = clock.instant();
        failures.compute(key, (k, current) ->
                current == null || expired(current) ? new Failures(1, now) : new Failures(current.count() + 1, current.windowStart()));
    }

    private boolean expired(Failures entry) {
        return entry.windowStart().plus(WINDOW).isBefore(clock.instant());
    }

    private static String userKey(String username) {
        return "user:" + (username == null ? "" : username.toLowerCase(Locale.ROOT));
    }

    private static String addressKey(String address) {
        return "addr:" + address;
    }
}
