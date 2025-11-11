package com.rcs.ssf.config;

import org.springframework.boot.context.event.ApplicationContextInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Application startup listener that performs pre-flight environment validation.
 * Executes before Spring initializes beans to catch missing credentials early.
 */
@Component
public class EnvironmentValidationListener implements ApplicationListener<ApplicationContextInitializedEvent> {

    private static final EnvironmentValidator validator = new EnvironmentValidator();

    @Override
    public void onApplicationEvent(@NonNull ApplicationContextInitializedEvent event) {
        validator.validateRequiredEnvironmentVariables();
    }
}
