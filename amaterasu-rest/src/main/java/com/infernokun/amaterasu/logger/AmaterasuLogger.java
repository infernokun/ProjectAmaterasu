package com.infernokun.amaterasu.logger;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class AmaterasuLogger implements Filter {

    private static final String REQUEST_ID_ATTRIBUTE = "requestId";
    private static final String START_TIME_ATTRIBUTE = "startTime";

    private final Set<String> excludedUrls = Set.of(
            "/health",
            "/actuator"
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        String requestUri = httpRequest.getRequestURI();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();

        // Set request attributes for potential use by other components
        httpRequest.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        httpRequest.setAttribute(START_TIME_ATTRIBUTE, startTime);

        // Log incoming request
        if (!isExcludedUrl(requestUri)) {
            logIncomingRequest(httpRequest, requestId);
        }

        try {
            filterChain.doFilter(servletRequest, servletResponse);

            // Log successful response
            if (!isExcludedUrl(requestUri)) {
                logResponse(httpRequest, httpResponse, requestId, startTime, null);
            }

        } catch (IOException | ServletException ex) {
            // Log error response
            if (!isExcludedUrl(requestUri)) {
                logResponse(httpRequest, httpResponse, requestId, startTime, ex);
            }
            throw ex; // Re-throw the exception
        }
    }

    private boolean isExcludedUrl(String requestUri) {
        return excludedUrls.stream().anyMatch(requestUri::startsWith);
    }

    private void logIncomingRequest(HttpServletRequest request, String requestId) {
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");

        log.info("REQUEST [{}] {} {} from {} | User-Agent: {} | Referer: {}",
                requestId,
                request.getMethod(),
                buildFullRequestUrl(request),
                clientIp,
                userAgent != null ? userAgent : "N/A",
                referer != null ? referer : "N/A"
        );
    }

    private void logResponse(HttpServletRequest request, HttpServletResponse response,
                             String requestId, long startTime, Exception exception) {

        long duration = System.currentTimeMillis() - startTime;
        String clientIp = getClientIpAddress(request);

        if (exception != null) {
            log.error("RESPONSE [{}] {} {} from {} | Status: ERROR | Duration: {}ms | Error: {}",
                    requestId,
                    request.getMethod(),
                    buildFullRequestUrl(request),
                    clientIp,
                    duration,
                    exception.getMessage()
            );
        } else {
            int status = response.getStatus();
            String logLevel = status >= 400 ? "WARN" : "INFO";

            if (status >= 400) {
                log.warn("RESPONSE [{}] {} {} from {} | Status: {} | Duration: {}ms",
                        requestId,
                        request.getMethod(),
                        buildFullRequestUrl(request),
                        clientIp,
                        status,
                        duration
                );
            } else {
                log.info("RESPONSE [{}] {} {} from {} | Status: {} | Duration: {}ms",
                        requestId,
                        request.getMethod(),
                        buildFullRequestUrl(request),
                        clientIp,
                        status,
                        duration
                );
            }
        }
    }

    private String buildFullRequestUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder();
        url.append(request.getRequestURI());

        if (request.getQueryString() != null) {
            url.append("?").append(request.getQueryString());
        }

        url.append(" ").append(request.getProtocol());
        return url.toString();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        // Check for common proxy headers
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("AmaterasuLogger initialized with excluded URLs: {}", excludedUrls);
    }

    @Override
    public void destroy() {
        log.info("AmaterasuLogger destroyed");
    }
}