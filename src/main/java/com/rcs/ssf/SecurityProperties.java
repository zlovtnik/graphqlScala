package com.rcs.ssf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Security configuration properties for fine-grained control over security behaviors.
 * 
 * Least-privilege principle: Unless explicitly enabled, features should default to
 * the most restrictive (secure) setting.
 */
@ConfigurationProperties(prefix = "app.security")
@Component
public class SecurityProperties {
    
    /**
     * If true, users with no explicitly assigned roles will receive ROLE_USER by default.
     * If false (default), users with no roles receive no implicit authorities (empty list).
     * 
     * Default: false (least-privilege: no implicit roles granted)
     * 
     * Set to true only if your application requires backward compatibility with systems
     * that depend on all users having at least ROLE_USER.
     */
    private boolean enableDefaultUserRole = false;

    public boolean isEnableDefaultUserRole() {
        return enableDefaultUserRole;
    }

    public void setEnableDefaultUserRole(boolean enableDefaultUserRole) {
        this.enableDefaultUserRole = enableDefaultUserRole;
    }

    @Override
    public String toString() {
        return "SecurityProperties{" +
                "enableDefaultUserRole=" + enableDefaultUserRole +
                '}';
    }
}
