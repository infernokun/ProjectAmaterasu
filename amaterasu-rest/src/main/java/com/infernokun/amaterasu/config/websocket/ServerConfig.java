package com.infernokun.amaterasu.config.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// doc: https://docs.spring.io/spring-framework/reference/web/websocket/server.html

@Configuration
@EnableWebSocket
@EnableScheduling
public abstract class ServerConfig implements WebSocketConfigurer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConfig.class);
}