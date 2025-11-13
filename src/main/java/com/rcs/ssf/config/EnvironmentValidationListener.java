package com.rcs.ssf.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Application startup listener that performs pre-flight environment validation.
 * Executes after Spring initializes beans to catch missing credentials.
 */
@Component
public class EnvironmentValidationListener implements ApplicationListener<ApplicationReadyEvent> {

    private final EnvironmentValidator validator;

    public EnvironmentValidationListener(EnvironmentValidator validator) {
        this.validator = validator;
    }

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        validator.validateRequiredEnvironmentVariables();
    }
}
