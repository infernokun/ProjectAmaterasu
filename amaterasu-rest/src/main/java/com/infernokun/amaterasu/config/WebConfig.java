package com.infernokun.amaterasu.config;

import com.infernokun.amaterasu.logger.AmaterasuLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AllowedOrigins allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.array())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(!allowedOrigins.allowsAny());
    }

    @Bean
    public FilterRegistrationBean<AmaterasuLogger> loggingFilter() {
        FilterRegistrationBean<AmaterasuLogger> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new AmaterasuLogger());
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }
}
