package com.infernokun.amaterasu.config;

import com.infernokun.amaterasu.services.alt.AttemptCounter;
import com.infernokun.amaterasu.services.alt.FallbackAttemptCounter;
import com.infernokun.amaterasu.services.alt.MemoryAttemptCounter;
import com.infernokun.amaterasu.services.alt.RedisAttemptCounter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration
public class ThrottleConfig {

    // redis (default) shares limits across restarts and instances; memory keeps them in this process
    @Bean
    public AttemptCounter attemptCounter(@Value("${amaterasu.throttleStore:redis}") String store,
                                         ObjectProvider<StringRedisTemplate> redis) {
        Clock clock = Clock.systemUTC();
        MemoryAttemptCounter memory = new MemoryAttemptCounter(clock);
        StringRedisTemplate template = redis.getIfAvailable();
        if (!"redis".equalsIgnoreCase(store) || template == null) {
            return memory;
        }
        return new FallbackAttemptCounter(new RedisAttemptCounter(template), memory, clock);
    }
}
