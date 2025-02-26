package com.infernokun.amaterasu.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "amaterasu")
public class AmaterasuConfig {
    private String applicationName;
    private String defaultAdminUsername;
    private String defaultAdminPassword;
    private String uploadDir;
    private String chatService;
    private String chatSocket;
    private String encryptionKey;
}
