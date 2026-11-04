package com.infernokun.amaterasu.config;

import com.infernokun.amaterasu.logger.AmaterasuLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

    // Comma-separated; the web app is served same-origin through nginx, so this only matters for other clients
    @Value("${amaterasu.allowedOrigins:*}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(false);
    }

    @Bean
    public FilterRegistrationBean<AmaterasuLogger> loggingFilter() {
        FilterRegistrationBean<AmaterasuLogger> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new AmaterasuLogger());
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }
}
