package com.infernokun.amaterasu.console;

import com.infernokun.amaterasu.config.AllowedOrigins;
import com.infernokun.amaterasu.services.entity.lab.RemoteServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** The VM console relay, next to the live update socket under /socket-handler/. */
@Configuration
@RequiredArgsConstructor
public class ConsoleSocketConfig implements WebSocketConfigurer {
    private final AllowedOrigins allowedOrigins;
    private final ConsoleSessions consoleSessions;
    private final RemoteServerService remoteServerService;
    private final ProxmoxConsoleConnector connector;

    @Bean
    public ConsoleRelayHandler consoleRelayHandler() {
        return new ConsoleRelayHandler(consoleSessions, remoteServerService, connector);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(consoleRelayHandler(), "/socket-handler/console/*");
        if (allowedOrigins.allowsAny()) {
            registration.setAllowedOriginPatterns("*");
        } else {
            registration.setAllowedOrigins(allowedOrigins.array());
        }
    }
}
