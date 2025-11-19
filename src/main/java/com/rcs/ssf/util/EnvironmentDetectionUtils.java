package com.rcs.ssf.util;

import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * Utility class for consistent environment detection across the application.
 * 
 * Provides a single source of truth for determining whether the application
 * is running in development mode, used by exception handlers and security components.
 */
public class EnvironmentDetectionUtils {

    private static final Set<String> DEV_PROFILES = Collections.unmodifiableSet(
            Set.of("dev", "development", "local", "test"));

    private EnvironmentDetectionUtils() {
        // Utility class, no instantiation
    }

    /**
     * Determines if the application is running in a development environment.
     * 
     * Returns true if:
     * - Active profiles are present AND any matches "dev", "development", "local", or "test" (case-insensitive)
     * - Active profiles are absent AND default profiles match the dev profile set
     *
     * @param environment the Spring environment to check (if null, returns false)
     * @return true if in development mode, false otherwise
     */
    public static boolean isDevelopment(Environment environment) {
        if (environment == null) {
            return false;
        }

        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            // Check default profiles if no active profiles are set
            activeProfiles = environment.getDefaultProfiles();
        }

        return Arrays.stream(activeProfiles)
                .anyMatch(profile -> DEV_PROFILES.contains(profile.toLowerCase()));
    }
}
