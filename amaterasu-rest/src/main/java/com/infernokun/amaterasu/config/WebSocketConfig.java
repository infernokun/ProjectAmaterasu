package com.infernokun.amaterasu.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.components.LabActionGuard;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.services.entity.ctf.LabTeamService;
import com.infernokun.amaterasu.websocket.AmaterasuSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final AllowedOrigins allowedOrigins;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final LabActionGuard labActionGuard;
    private final LabTeamService labTeamService;

    @Bean
    public AmaterasuSocketHandler socketHandler() {
        return new AmaterasuSocketHandler(objectMapper, userRepository, labActionGuard, labTeamService::isMember);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // The handshake is authenticated in SecurityConfig; no listed origins means same-origin only
        var registration = registry.addHandler(socketHandler(), "/socket-handler/update");
        if (allowedOrigins.allowsAny()) {
            registration.setAllowedOriginPatterns("*");
        } else {
            registration.setAllowedOrigins(allowedOrigins.array());
        }
    }

}
