package com.infernokun.amaterasu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Origins other than the API's own that may call it from a browser, from ALLOWED_ORIGINS. Empty by
 * default: the web app is served through nginx on the same origin and needs none.
 */
@Component
public class AllowedOrigins {
    private final List<String> origins;

    public AllowedOrigins(@Value("${amaterasu.allowedOrigins:}") String[] configured) {
        this.origins = Arrays.stream(configured).map(String::trim).filter(origin -> !origin.isEmpty()).toList();
    }

    public List<String> list() {
        return origins;
    }

    public String[] array() {
        return origins.toArray(String[]::new);
    }

    public boolean isEmpty() {
        return origins.isEmpty();
    }

    /** A wildcard can't be combined with credentials, so the refresh cookie only works with listed origins. */
    public boolean allowsAny() {
        return origins.contains("*");
    }
}
