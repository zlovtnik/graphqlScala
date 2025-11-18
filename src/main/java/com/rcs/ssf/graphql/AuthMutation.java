package com.rcs.ssf.graphql;

import com.rcs.ssf.dto.AuthResponse;
import com.rcs.ssf.security.AuthenticatedUser;
import com.rcs.ssf.security.JwtTokenProvider;
import com.rcs.ssf.service.AuditService;
import com.rcs.ssf.metrics.ComplianceMetricsService;
import graphql.GraphqlErrorException;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

@Controller
public class AuthMutation {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthMutation.class);
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;
    private final ObjectProvider<HttpServletRequest> requestProvider;
    private final Environment environment;
    private final ComplianceMetricsService complianceMetricsService;

    public AuthMutation(AuthenticationManager authenticationManager,
                        JwtTokenProvider jwtTokenProvider,
                        AuditService auditService,
                        ObjectProvider<HttpServletRequest> requestProvider,
                        Environment environment,
                        ComplianceMetricsService complianceMetricsService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditService = auditService;
        this.requestProvider = requestProvider;
        this.environment = environment;
        this.complianceMetricsService = complianceMetricsService;
    }

    @MutationMapping
    @Timed(value = "graphql.resolver.duration", percentiles = {0.5, 0.95, 0.99}, extraTags = {"operation", "login"})
    public AuthResponse login(@Argument String username, @Argument String password) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        
        // Lazily resolve HttpServletRequest; works in HTTP contexts
        // In production environments, missing HttpServletRequest is an error condition that should not be silently ignored
        HttpServletRequest request = requestProvider.getIfAvailable();
        
        // Determine if running in production environment
        boolean isProduction = isProductionEnvironment();
        
        if (request == null && isProduction) {
            // In production, fail fast when HTTP context is missing
            LOGGER.error("Login attempt without HttpServletRequest context. " +
                    "This indicates a misconfiguration or non-HTTP invocation in production.");
            throw new IllegalStateException("Authentication context not available. " +
                    "Login must be invoked through HTTP endpoint: /graphql");
        }
        
        // For non-production/test environments, log a warning but allow fallback to "unknown" values
        if (request == null) {
            LOGGER.warn("HttpServletRequest not available (non-production environment). " +
                    "Audit logs will use 'unknown' for ipAddress and userAgent.");
        }
        
        String ipAddress = (request != null) ? getClientIpAddress(request) : "unknown";
        String userAgent = (request != null) ? request.getHeader("User-Agent") : "unknown";

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            String token = jwtTokenProvider.generateToken(authentication);
            auditService.logLoginAttempt(username, true, ipAddress, userAgent, null);

            AuthenticatedUser principal = extractAuthenticatedUser(authentication, username, ipAddress, userAgent);
            auditService.logSessionStart(principal.getId().toString(), token, ipAddress, userAgent);

            complianceMetricsService.incrementSuccessfulLoginAttempts();

            return new AuthResponse(token);
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            // User not found in database - use generic error message to prevent username enumeration
            LOGGER.warn("Login attempt failed: user not found. username={}, ipAddress={}, userAgent={}", 
                    username, ipAddress, userAgent);
            auditService.logLoginAttempt(username, false, ipAddress, userAgent, "USER_NOT_FOUND");
            complianceMetricsService.incrementFailedLoginAttempts("USER_NOT_FOUND");
            throw GraphqlErrorException.newErrorException()
                    .message("Invalid username or password")
                    .extensions(Map.of("reason", "INVALID_CREDENTIALS"))
                    .cause(e)
                    .build();
        } catch (org.springframework.security.core.AuthenticationException e) {
            // Bad credentials or other authentication failure - use generic error message to prevent username enumeration
            LOGGER.warn("Login attempt failed: authentication error. username={}, ipAddress={}, userAgent={}, error={}", 
                    username, ipAddress, userAgent, e.getMessage());
            auditService.logLoginAttempt(username, false, ipAddress, userAgent, e.getMessage());
            complianceMetricsService.incrementFailedLoginAttempts("INVALID_CREDENTIALS");
            throw GraphqlErrorException.newErrorException()
                    .message("Invalid username or password")
                    .extensions(Map.of("reason", "INVALID_CREDENTIALS"))
                    .cause(e)
                    .build();
        }
    }

    @MutationMapping
    @Timed(value = "graphql.resolver.duration", percentiles = {0.5, 0.95, 0.99}, extraTags = {"operation", "logout"})
    public boolean logout() {
        // Token invalidation is handled client-side by removing it
        // For server-side blacklisting, implement a token blacklist service
        complianceMetricsService.incrementLogoutAttempts();
        return true;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * Determines if the application is running in a production environment.
     * Checks the active Spring profiles to identify production (e.g., "prod", "production").
     *
     * @return true if production profile is active, false otherwise
     */
    private boolean isProductionEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if (profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production")) {
                return true;
            }
        }
        return false;
    }

    private AuthenticatedUser extractAuthenticatedUser(Authentication authentication, String username, String ipAddress, String userAgent) {
        if (authentication == null || authentication.getPrincipal() == null) {
            LOGGER.error("Missing authentication principal for session start. username={}, ipAddress={}, userAgent={}",
                    username, ipAddress, userAgent);
            throw new IllegalStateException("Missing authentication principal");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }

        LOGGER.error("Unexpected authentication principal type: {}. username={}, ipAddress={}, userAgent={}",
                principal.getClass().getName(), username, ipAddress, userAgent);
        throw new IllegalStateException("Unexpected authentication principal type: " + principal.getClass().getName());
    }
}

