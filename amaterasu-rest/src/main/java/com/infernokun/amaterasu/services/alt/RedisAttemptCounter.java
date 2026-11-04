package com.infernokun.amaterasu.services.alt;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

/** Counters shared by every API instance, so limits hold across restarts and replicas. */
public class RedisAttemptCounter implements AttemptCounter {
    private static final String PREFIX = "amaterasu:attempts:";

    // Increment and start the expiry in one step, so a key can never be left without one
    private static final RedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisAttemptCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration window) {
        Long count = redis.execute(INCREMENT, List.of(PREFIX + key), String.valueOf(window.toMillis()));
        return count == null ? 0 : count;
    }

    @Override
    public long count(String key) {
        String value = redis.opsForValue().get(PREFIX + key);
        return value == null ? 0 : Long.parseLong(value);
    }

    @Override
    public void reset(String key) {
        redis.delete(PREFIX + key);
    }
}
