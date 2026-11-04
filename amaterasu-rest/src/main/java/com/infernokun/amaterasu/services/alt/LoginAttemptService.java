package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.exceptions.TooManyAttemptsException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * Slows down password guessing and signup or refresh floods. Failed logins are counted per username
 * and per client address over a window; past the limit further attempts are refused until the window
 * ends.
 */
@Service
public class LoginAttemptService {
    static final int MAX_PER_USERNAME = 5;
    // A classroom often shares one address, so the address limit is generous
    static final int MAX_PER_ADDRESS = 50;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final AttemptCounter counter;

    public LoginAttemptService(AttemptCounter counter) {
        this.counter = counter;
    }

    public void checkAllowed(String username, String address) {
        if (counter.count(userKey(username)) >= MAX_PER_USERNAME
                || counter.count(addressKey(address)) >= MAX_PER_ADDRESS) {
            throw new TooManyAttemptsException("Too many failed logins, try again in " + WINDOW.toMinutes() + " minutes");
        }
    }

    public void recordFailure(String username, String address) {
        counter.increment(userKey(username), WINDOW);
        counter.increment(addressKey(address), WINDOW);
    }

    public void recordSuccess(String username) {
        counter.reset(userKey(username));
    }

    /** Counts every call, successful or not, and refuses once an address passes the limit for an action. */
    public void throttle(String action, String address, int limit, Duration window) {
        if (counter.increment("rate:" + action + ":" + address, window) > limit) {
            throw new TooManyAttemptsException("Too many requests, try again later");
        }
    }

    private static String userKey(String username) {
        return "user:" + (username == null ? "" : username.toLowerCase(Locale.ROOT));
    }

    private static String addressKey(String address) {
        return "addr:" + address;
    }
}
