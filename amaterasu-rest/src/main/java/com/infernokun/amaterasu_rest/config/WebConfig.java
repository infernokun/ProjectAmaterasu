package com.infernokun.amaterasu_rest.config;

import com.infernokun.amaterasu_rest.logger.AmaterasuLogger;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedMethods("OPTIONS", "HEAD", "PUT", "POST", "DELETE", "PATH")
                .allowCredentials(true)
                .allowedHeaders("*")
                .allowedOriginPatterns("*");
    }

    @Bean
    public FilterRegistrationBean<AmaterasuLogger> loggingFilter() {
        FilterRegistrationBean<AmaterasuLogger> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new AmaterasuLogger());
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }
}
