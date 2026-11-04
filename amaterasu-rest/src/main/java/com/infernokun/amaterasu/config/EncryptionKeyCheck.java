package com.infernokun.amaterasu.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Stops startup before anything connects when ENCRYPTION_KEY is weak. Stored server passwords and API
 * tokens are only as safe as this key.
 */
@Component
public class EncryptionKeyCheck implements BeanFactoryPostProcessor, EnvironmentAware {
    static final int MIN_KEY_LENGTH = 32;
    private static final Set<String> WEAK_KEYS = Set.of("secret_key", "change-me", "changeme");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        requireStrongKey(environment.getProperty("amaterasu.encryptionKey"));
    }

    static void requireStrongKey(String key) {
        if (key == null || key.length() < MIN_KEY_LENGTH || WEAK_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("ENCRYPTION_KEY must be at least " + MIN_KEY_LENGTH
                    + " characters and not a default value. Generate one with: openssl rand -base64 32");
        }
    }
}
