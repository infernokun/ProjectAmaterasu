package com.infernokun.amaterasu.console;

import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVncTicket;
import com.infernokun.amaterasu.services.entity.lab.RemoteServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The relay only connects the user a console was issued to, and passes bytes both ways. */
class ConsoleRelayHandlerTest {
    private ConsoleSessions sessions;
    private ProxmoxConsoleConnector connector;
    private ConsoleRelayHandler handler;
    private final ProxmoxVncTicket ticket = new ProxmoxVncTicket("5900", "PVEVNC:t", "root@pam");
    private final RemoteServer server = RemoteServer.builder().name("pve-a").build();

    @BeforeEach
    void setUp() {
        sessions = new ConsoleSessions();
        connector = mock(ProxmoxConsoleConnector.class);
        RemoteServerService servers = mock(RemoteServerService.class);
        when(servers.findServerById("server-1")).thenReturn(server);
        handler = new ConsoleRelayHandler(sessions, servers, connector);
    }

    private WebSocketSession browser(String userId, String consoleId) {
        WebSocketSession session = mock(WebSocketSession.class);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(userId).build();
        when(session.getPrincipal()).thenReturn(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
        when(session.getUri()).thenReturn(URI.create("ws://host/amaterasu-rest/socket-handler/console/" + consoleId));
        when(session.getId()).thenReturn("ws-" + userId);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void anotherUserCannotUseTheConsole() throws Exception {
        String id = sessions.issue("alice", "server-1", 101, ticket);
        WebSocketSession bob = browser("bob", id);

        handler.afterConnectionEstablished(bob);

        ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
        verify(bob).close(status.capture());
        assertThat(status.getValue().getCode()).isEqualTo(ConsoleRelayHandler.CLOSE_NOT_ALLOWED);
        verify(connector, never()).connect(any(), anyInt(), any(), any());
        assertThat(handler.openRelays()).isZero();
    }

    @Test
    void relaysBytesBetweenTheBrowserAndTheNode() throws Exception {
        String id = sessions.issue("alice", "server-1", 101, ticket);
        WebSocket upstream = mock(WebSocket.class);
        when(upstream.sendBinary(any(), eq(true))).thenReturn(CompletableFuture.completedFuture(upstream));
        when(upstream.sendClose(anyInt(), any())).thenReturn(CompletableFuture.completedFuture(upstream));
        ArgumentCaptor<WebSocket.Listener> listener = ArgumentCaptor.forClass(WebSocket.Listener.class);
        when(connector.connect(eq(server), eq(101), eq(ticket), listener.capture()))
                .thenReturn(CompletableFuture.completedFuture(upstream));
        WebSocketSession alice = browser("alice", id);

        handler.afterConnectionEstablished(alice);
        handler.handleMessage(alice, new BinaryMessage(new byte[]{1, 2, 3}));
        verify(upstream, timeout(1000)).sendBinary(ByteBuffer.wrap(new byte[]{1, 2, 3}), true);

        listener.getValue().onBinary(upstream, ByteBuffer.wrap("RFB 003.008\n".getBytes()), true);
        ArgumentCaptor<WebSocketMessage<?>> sent = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(alice, timeout(1000)).sendMessage(sent.capture());
        assertThat(((ByteBuffer) sent.getValue().getPayload()).remaining()).isEqualTo(12);

        handler.afterConnectionClosed(alice, CloseStatus.NORMAL);
        verify(upstream, timeout(1000)).sendClose(WebSocket.NORMAL_CLOSURE, "");
        assertThat(handler.openRelays()).isZero();
    }

    @Test
    void tellsTheBrowserWhenTheNodeRefuses() throws Exception {
        String id = sessions.issue("alice", "server-1", 101, ticket);
        when(connector.connect(any(), anyInt(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new java.io.IOException("401 no ticket")));
        WebSocketSession alice = browser("alice", id);

        handler.afterConnectionEstablished(alice);

        ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
        verify(alice, timeout(1000)).close(status.capture());
        assertThat(status.getValue().getCode()).isEqualTo(ConsoleRelayHandler.CLOSE_UPSTREAM);
        assertThat(status.getValue().getReason()).contains("pve-a");
    }
}
