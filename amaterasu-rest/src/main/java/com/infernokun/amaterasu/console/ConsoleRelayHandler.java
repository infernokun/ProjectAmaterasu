package com.infernokun.amaterasu.console;

import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.services.entity.lab.RemoteServerService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Relays a browser console websocket to the Proxmox node's vncwebsocket, byte for byte. The
 * handshake is authenticated like the live update socket; the session id in the path must be one
 * the API just issued to the same user.
 */
@Slf4j
public class ConsoleRelayHandler extends AbstractWebSocketHandler implements SubProtocolCapable {
    /** The console link expired, was used already, or belongs to someone else. */
    public static final int CLOSE_NOT_ALLOWED = 4401;
    /** The lab's server is gone. */
    public static final int CLOSE_NO_SERVER = 4404;
    /** The node did not accept or dropped the console connection. */
    public static final int CLOSE_UPSTREAM = 4502;

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 16 * 1024 * 1024;

    private final ConsoleSessions consoleSessions;
    private final RemoteServerService remoteServerService;
    private final ProxmoxConsoleConnector connector;
    private final Map<String, Relay> relays = new ConcurrentHashMap<>();

    public ConsoleRelayHandler(ConsoleSessions consoleSessions, RemoteServerService remoteServerService,
                               ProxmoxConsoleConnector connector) {
        this.consoleSessions = consoleSessions;
        this.remoteServerService = remoteServerService;
        this.connector = connector;
    }

    @Override
    public List<String> getSubProtocols() {
        return List.of("binary");
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws IOException {
        String userId = session.getPrincipal() instanceof JwtAuthenticationToken token ? token.getName() : null;
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String consoleId = path.substring(path.lastIndexOf('/') + 1);

        Optional<ConsoleSessions.Pending> pending = consoleSessions.claim(consoleId, userId);
        if (pending.isEmpty()) {
            session.close(new CloseStatus(CLOSE_NOT_ALLOWED, "This console link expired or is not yours. Open the console again."));
            return;
        }
        RemoteServer server;
        try {
            server = remoteServerService.findServerById(pending.get().serverId());
        } catch (RuntimeException e) {
            session.close(new CloseStatus(CLOSE_NO_SERVER, "The lab's server no longer exists."));
            return;
        }

        Relay relay = new Relay(new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES),
                server.getName());
        relays.put(session.getId(), relay);
        log.info("CONSOLE {} opened VM {} on {}", userId, pending.get().vmid(), server.getName());
        connector.connect(server, pending.get().vmid(), pending.get().ticket(), relay)
                .whenComplete((upstream, error) -> {
                    if (error != null) {
                        log.warn("CONSOLE Could not reach VM {} on {}: {}", pending.get().vmid(), server.getName(),
                                error.getMessage());
                        relay.closeBrowser(CLOSE_UPSTREAM, server.getName() + " did not accept the console connection.");
                        relay.upstream.completeExceptionally(error);
                    } else {
                        relay.upstream.complete(upstream);
                    }
                });
    }

    @Override
    protected void handleBinaryMessage(@NonNull WebSocketSession session, @NonNull BinaryMessage message) {
        Relay relay = relays.get(session.getId());
        if (relay != null) {
            ByteBuffer payload = message.getPayload();
            byte[] copy = new byte[payload.remaining()];
            payload.get(copy);
            relay.toUpstream(copy);
        }
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        handleBinaryMessage(session, new BinaryMessage(message.getPayload().getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        log.debug("CONSOLE transport error on {}: {}", session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        Relay relay = relays.remove(session.getId());
        if (relay != null) {
            relay.closeUpstream();
        }
    }

    int openRelays() {
        return relays.size();
    }

    /** One browser console and its node connection. Sends to the node go one at a time, in order. */
    static final class Relay implements WebSocket.Listener {
        final CompletableFuture<WebSocket> upstream = new CompletableFuture<>();
        private final WebSocketSession browser;
        private final String serverName;
        private CompletableFuture<?> sends = CompletableFuture.completedFuture(null);
        private volatile boolean closed;

        Relay(WebSocketSession browser, String serverName) {
            this.browser = browser;
            this.serverName = serverName;
        }

        synchronized void toUpstream(byte[] bytes) {
            if (closed) {
                return;
            }
            sends = sends.thenCompose(ignored -> upstream)
                    .thenCompose(socket -> socket.sendBinary(ByteBuffer.wrap(bytes), true))
                    .exceptionally(error -> {
                        closeBrowser(CLOSE_UPSTREAM, "The console connection to " + serverName + " dropped.");
                        return null;
                    });
        }

        void closeUpstream() {
            closed = true;
            upstream.thenAccept(socket -> socket.sendClose(WebSocket.NORMAL_CLOSURE, "").exceptionally(error -> {
                socket.abort();
                return null;
            }));
        }

        void closeBrowser(int code, String reason) {
            closed = true;
            try {
                if (browser.isOpen()) {
                    browser.close(new CloseStatus(code, reason));
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("CONSOLE could not close browser socket: {}", e.getMessage());
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            try {
                if (browser.isOpen()) {
                    browser.sendMessage(new BinaryMessage(bytes));
                }
            } catch (IOException | IllegalStateException e) {
                webSocket.abort();
                return null;
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            return onBinary(webSocket, StandardCharsets.UTF_8.encode(data.toString()), last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeBrowser(CloseStatus.NORMAL.getCode(), "The VM closed the console.");
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("CONSOLE connection to {} failed: {}", serverName, error.getMessage());
            closeBrowser(CLOSE_UPSTREAM, "The console connection to " + serverName + " dropped.");
        }
    }
}
