package com.infernokun.amaterasu.console;

import com.infernokun.amaterasu.models.proxmox.ProxmoxVncTicket;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleSessionsTest {
    private final ProxmoxVncTicket ticket = new ProxmoxVncTicket("5900", "PVEVNC:t", "root@pam");

    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-06T10:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void aConsoleOpensOnceForTheUserItWasIssuedTo() {
        ConsoleSessions sessions = new ConsoleSessions();
        String id = sessions.issue("alice", "server-1", 101, ticket);

        assertThat(sessions.claim(id, "bob")).isEmpty();
        assertThat(sessions.claim(id, "alice")).hasValueSatisfying(pending -> {
            assertThat(pending.vmid()).isEqualTo(101);
            assertThat(pending.serverId()).isEqualTo("server-1");
            assertThat(pending.ticket()).isEqualTo(ticket);
        });
        assertThat(sessions.claim(id, "alice")).isEmpty();
    }

    @Test
    void unknownIdsAndMissingUsersOpenNothing() {
        ConsoleSessions sessions = new ConsoleSessions();
        String id = sessions.issue("alice", "server-1", 101, ticket);
        assertThat(sessions.claim("nope", "alice")).isEmpty();
        assertThat(sessions.claim(id, null)).isEmpty();
        assertThat(sessions.claim(null, "alice")).isEmpty();
    }

    @Test
    void aConsoleLinkExpires() {
        MovableClock clock = new MovableClock();
        ConsoleSessions sessions = new ConsoleSessions(clock);
        String id = sessions.issue("alice", "server-1", 101, ticket);
        clock.now = clock.now.plus(ConsoleSessions.TTL).plus(Duration.ofSeconds(1));
        assertThat(sessions.claim(id, "alice")).isEmpty();
    }

    @Test
    void idsAreLongAndDifferent() {
        ConsoleSessions sessions = new ConsoleSessions();
        String a = sessions.issue("alice", "s", 1, ticket);
        String b = sessions.issue("alice", "s", 1, ticket);
        assertThat(a).isNotEqualTo(b).hasSizeGreaterThanOrEqualTo(32);
    }
}
