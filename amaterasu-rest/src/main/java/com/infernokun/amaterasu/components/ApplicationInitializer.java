package com.infernokun.amaterasu.components;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.services.entity.UserService;
import com.infernokun.amaterasu.models.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationInitializer implements ApplicationRunner {
    private final UserService userService;
    private final AmaterasuConfig amaterasuConfig;
    @Override
    public void run(ApplicationArguments args) {
        try {
            User user = this.userService.findUserByUsername(amaterasuConfig.getDefaultAdminUsername());
        } catch (UsernameNotFoundException ex) {
            User admin = new User();
            admin.setRole(Role.ADMIN);
            admin.setUsername(amaterasuConfig.getDefaultAdminUsername());
            admin.setEmail(amaterasuConfig.getDefaultAdminUsername() + "@amaterasu.local");
            admin.setPassword(amaterasuConfig.getDefaultAdminPassword());

            this.userService.registerUser(admin);
        }
    }
}
