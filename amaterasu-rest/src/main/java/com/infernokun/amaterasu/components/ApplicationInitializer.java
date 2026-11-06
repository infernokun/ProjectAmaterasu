package com.infernokun.amaterasu.components;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.services.entity.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationInitializer implements ApplicationRunner {
    private final UserService userService;
    private final AmaterasuConfig amaterasuConfig;

    @Override
    public void run(ApplicationArguments args) {
        String username = amaterasuConfig.getDefaultAdminUsername();
        if (userService.findByUsernameIgnoreCase(username).isPresent()) {
            return;
        }

        String password = amaterasuConfig.getDefaultAdminPassword();
        boolean generated = password == null || password.isBlank();
        if (generated) {
            byte[] bytes = new byte[18];
            new SecureRandom().nextBytes(bytes);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }

        User admin = new User();
        admin.setRole(Role.ADMIN);
        admin.setUsername(username);
        admin.setEmail(username + "@amaterasu.local");
        admin.setPassword(password);
        userService.registerUser(admin);

        if (generated) {
            log.warn("Created admin account '{}' with generated password: {} (set ADMIN_PASSWORD to choose one)",
                    username, password);
        } else {
            log.info("Created admin account '{}'", username);
        }
    }
}
