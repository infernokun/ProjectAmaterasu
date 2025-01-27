package com.infernokun.amaterasu.config;

import com.infernokun.amaterasu.logger.AmaterasuLogger;
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
                .allowedOriginPatterns("*")  // Allow all origins
                .allowedMethods("*")         // Allow all HTTP methods
                .allowedHeaders("*")         // Allow all headers
                .allowCredentials(false);    // Do not require credentials
    }

    @Bean
    public FilterRegistrationBean<AmaterasuLogger> loggingFilter() {
        FilterRegistrationBean<AmaterasuLogger> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new AmaterasuLogger());
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }
}
