package com.infernokun.amaterasu_rest.logger;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

public class AmaterasuLogger implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AmaterasuLogger.class);
    private final Set<String> excludedUrls = Set.of();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
        String requestUri = httpRequest.getRequestURI();
        try {
            filterChain.doFilter(servletRequest, servletResponse);
            if (httpResponse != null && !excludedUrls.contains(requestUri)) {
                LOGGER.info("{} \"{}\" {}", httpRequest.getRemoteAddr(), httpRequest.getMethod() + " " + httpRequest.getRequestURI() +
                        (httpRequest.getQueryString() != null ? "?" +
                                httpRequest.getQueryString() : "") + " " +
                        httpRequest.getProtocol(), httpResponse.getStatus());
            }
        } catch (IOException ex) {
            LOGGER.error("yikes: {}", httpRequest.getRequestURI());
        }
    }
}