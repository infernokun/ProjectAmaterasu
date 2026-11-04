package com.infernokun.amaterasu.components;

import com.infernokun.amaterasu.models.entities.RefreshToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

/**
 * The refresh token lives in an HttpOnly cookie scoped to the auth endpoints, so page scripts never
 * see it and the browser only sends it when signing in, refreshing or signing out.
 */
@Component
public class RefreshCookie {
    public static final String NAME = "amaterasu_refresh";

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public void write(String token, HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, RefreshToken.MAX_LIFETIME, request).toString());
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO, request).toString());
    }

    private static ResponseCookie build(String value, Duration maxAge, HttpServletRequest request) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Strict")
                .path(request.getContextPath() + "/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
