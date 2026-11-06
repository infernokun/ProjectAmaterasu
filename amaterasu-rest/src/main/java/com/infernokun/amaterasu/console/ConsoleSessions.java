package com.infernokun.amaterasu.console;

import com.infernokun.amaterasu.models.proxmox.ProxmoxVncTicket;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Console tickets the API handed out and the browser has not used yet. Each one is good for one
 * websocket, by the user it was issued to, within a short time. Proxmox stops waiting for the
 * connection soon after the ticket is issued anyway.
 */
@Component
public class ConsoleSessions {
    static final Duration TTL = Duration.ofSeconds(30);

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public ConsoleSessions() {
        this(Clock.systemUTC());
    }

    ConsoleSessions(Clock clock) {
        this.clock = clock;
    }

    /** A console waiting for its websocket. */
    public record Pending(String userId, String serverId, int vmid, ProxmoxVncTicket ticket, Instant expiresAt) {
    }

    public String issue(String userId, String serverId, int vmid, ProxmoxVncTicket ticket) {
        Instant now = clock.instant();
        pending.values().removeIf(entry -> entry.expiresAt().isBefore(now));
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(id, new Pending(userId, serverId, vmid, ticket, now.plus(TTL)));
        return id;
    }

    /** Takes the console out of the waiting list if this user may open it now. */
    public Optional<Pending> claim(String id, String userId) {
        if (id == null || userId == null) {
            return Optional.empty();
        }
        Pending entry = pending.get(id);
        if (entry == null || !Objects.equals(entry.userId(), userId) || !pending.remove(id, entry)) {
            return Optional.empty();
        }
        return entry.expiresAt().isBefore(clock.instant()) ? Optional.empty() : Optional.of(entry);
    }
}
