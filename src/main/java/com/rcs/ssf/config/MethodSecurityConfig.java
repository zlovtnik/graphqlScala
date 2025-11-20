package com.rcs.ssf.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Method-Level Security Configuration for ORACLE.
 *
 * Enables @PreAuthorize, @Secured, and @PostAuthorize annotations to enforce
 * authorization rules on service methods and GraphQL resolvers.
 *
 * Wire the RoleHierarchy bean into the MethodSecurityExpressionHandler so that
 * role hierarchy checks (e.g., SUPER_ADMIN > ADMIN > USER) are honored in
 * method-level authorization annotations.
 *
 * Example usage in service methods:
 * {@code
 *   @PreAuthorize("hasRole('ADMIN')")
 *   public User updateUser(User user) { ... }
 *
 *   @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
 *   public List<User> listUsers() { ... }
 *
 *   @Secured("ROLE_SUPER_ADMIN")
 *   public void deleteAllUsers() { ... }
 * }
 *
 * The RoleHierarchy ensures that a SUPER_ADMIN can perform any action
 * permitted to ADMIN or USER roles without explicit role duplication.
 */
@Configuration
@EnableMethodSecurity(
    prePostEnabled = true,      // Enable @PreAuthorize and @PostAuthorize
    securedEnabled = true       // Enable @Secured annotation
)
public class MethodSecurityConfig {

    /**
     * Expose the MethodSecurityExpressionHandler bean with RoleHierarchy injected.
     *
     * This bean is automatically picked up by @EnableMethodSecurity and used
     * to evaluate @PreAuthorize, @PostAuthorize, and @Secured expressions.
     *
     * @param roleHierarchy The RoleHierarchy bean from SecurityConfig
     * @return Configured MethodSecurityExpressionHandler
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }
}
