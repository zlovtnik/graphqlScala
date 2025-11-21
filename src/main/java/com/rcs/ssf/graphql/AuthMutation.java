package com.rcs.ssf.graphql;

import com.rcs.ssf.dto.AuthResponse;
import com.rcs.ssf.entity.User;
import com.rcs.ssf.exception.UsernameAlreadyExistsException;
import com.rcs.ssf.exception.EmailAlreadyExistsException;
import com.rcs.ssf.security.AuthenticatedUser;
import com.rcs.ssf.security.JwtTokenProvider;
import com.rcs.ssf.service.AuditService;
import com.rcs.ssf.service.UserService;
import com.rcs.ssf.metrics.ComplianceMetricsService;
import graphql.GraphqlErrorException;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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
    private final ObjectProvider<HttpServletResponse> responseProvider;
    private final Environment environment;
    private final ComplianceMetricsService complianceMetricsService;
    private final UserService userService;
    private final long jwtExpirationInMs;

    public AuthMutation(AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            AuditService auditService,
            ObjectProvider<HttpServletRequest> requestProvider,
            ObjectProvider<HttpServletResponse> responseProvider,
            Environment environment,
            ComplianceMetricsService complianceMetricsService,
            UserService userService,
            @Value("${app.jwt.expiration:86400000}") long jwtExpirationInMs) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditService = auditService;
        this.requestProvider = requestProvider;
        this.responseProvider = responseProvider;
        this.environment = environment;
        this.complianceMetricsService = complianceMetricsService;
        this.userService = userService;
        this.jwtExpirationInMs = jwtExpirationInMs;
    }

    @MutationMapping
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 }, extraTags = { "operation", "login" })
    public AuthResponse login(@Argument String username, @Argument String password) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Password must not be blank");
        }

        // Lazily resolve HttpServletRequest; works in HTTP contexts
        // In production environments, missing HttpServletRequest is an error condition
        // that should not be silently ignored
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

        // For non-production/test environments, log a warning but allow fallback to
        // "unknown" values
        if (request == null) {
            LOGGER.warn("HttpServletRequest not available (non-production environment). " +
                    "Audit logs will use 'unknown' for ipAddress and userAgent.");
        }

        String ipAddress = (request != null) ? getClientIpAddress(request) : "unknown";
        String userAgent = (request != null) ? request.getHeader("User-Agent") : "unknown";

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            String token = jwtTokenProvider.generateToken(authentication);
            auditService.logLoginAttempt(username, true, ipAddress, userAgent, null);

            AuthenticatedUser principal = extractAuthenticatedUser(authentication, username, ipAddress, userAgent);
            auditService.logSessionStart(principal.getId().toString(), token, ipAddress, userAgent);

            complianceMetricsService.incrementSuccessfulLoginAttempts();

            // Set JWT token as httpOnly cookie for production environments
            HttpServletResponse response = responseProvider.getIfAvailable();
            if (response != null) {
                setAuthTokenCookie(response, token);
            }

            return new AuthResponse(token);
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            // User not found in database - use generic error message to prevent username
            // enumeration
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
            // Bad credentials or other authentication failure - use generic error message
            // to prevent username enumeration
            LOGGER.warn("Login attempt failed: authentication error. username={}, ipAddress={}, userAgent={}, error={}",
                    username, ipAddress, userAgent, e.getMessage());
            auditService.logLoginAttempt(username, false, ipAddress, userAgent, 
                    AuditService.truncateFailureReason(e.getMessage()));
            complianceMetricsService.incrementFailedLoginAttempts("INVALID_CREDENTIALS");
            throw GraphqlErrorException.newErrorException()
                    .message("Invalid username or password")
                    .extensions(Map.of("reason", "INVALID_CREDENTIALS"))
                    .cause(e)
                    .build();
        }
    }

    @MutationMapping
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 }, extraTags = { "operation",
            "register" })
    public AuthResponse register(@Argument String username, @Argument String email, @Argument String password) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Password must not be blank");
        }

        // Lazily resolve HttpServletRequest; works in HTTP contexts
        // In production environments, missing HttpServletRequest is an error condition
        // that should not be silently ignored
        HttpServletRequest request = requestProvider.getIfAvailable();

        // Determine if running in production environment
        boolean isProduction = isProductionEnvironment();

        if (request == null && isProduction) {
            // In production, fail fast when HTTP context is missing
            LOGGER.error("Registration attempt without HttpServletRequest context. " +
                    "This indicates a misconfiguration or non-HTTP invocation in production.");
            throw new IllegalStateException("Registration context not available. " +
                    "Registration must be invoked through HTTP endpoint: /graphql");
        }

        // For non-production/test environments, log a warning but allow fallback to
        // "unknown" values
        if (request == null) {
            LOGGER.warn("HttpServletRequest not available (non-production environment). " +
                    "Audit logs will use 'unknown' for ipAddress and userAgent.");
        }

        String ipAddress = (request != null) ? getClientIpAddress(request) : "unknown";
        String userAgent = (request != null) ? request.getHeader("User-Agent") : "unknown";

        try {
            // Create user
            userService.createUser(new User(username, password, email));

            // Authenticate the newly created user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));

            // Generate token
            String token = jwtTokenProvider.generateToken(authentication);

            // Log registration
            auditService.logRegistrationAttempt(username, true, ipAddress, userAgent, null);

            AuthenticatedUser principal = extractAuthenticatedUser(authentication, username, ipAddress, userAgent);
            auditService.logSessionStart(principal.getId().toString(), token, ipAddress, userAgent);

            complianceMetricsService.incrementSuccessfulRegistrationAttempts();

            // Set JWT token as httpOnly cookie for production environments
            HttpServletResponse response = responseProvider.getIfAvailable();
            if (response != null) {
                setAuthTokenCookie(response, token);
            }

            return new AuthResponse(token);
        } catch (UsernameAlreadyExistsException e) {
            // Handle duplicate username error
            String reason = "USERNAME_ALREADY_EXISTS";
            LOGGER.warn("Registration attempt failed: username already exists. ipAddress={}, userAgent={}",
                    ipAddress, userAgent);
            auditService.logRegistrationAttempt(username, false, ipAddress, userAgent, reason);
            complianceMetricsService.incrementFailedLoginAttempts(reason);

            throw GraphqlErrorException.newErrorException()
                    .message("Registration failed: username already exists")
                    .extensions(Map.of("reason", reason))
                    .cause(e)
                    .build();
        } catch (EmailAlreadyExistsException e) {
            // Handle duplicate email error
            String reason = "EMAIL_ALREADY_EXISTS";
            LOGGER.warn("Registration attempt failed: email already exists. ipAddress={}, userAgent={}",
                    ipAddress, userAgent);
            auditService.logRegistrationAttempt(username, false, ipAddress, userAgent, reason);
            complianceMetricsService.incrementFailedLoginAttempts(reason);

            throw GraphqlErrorException.newErrorException()
                    .message("Registration failed: email already exists")
                    .extensions(Map.of("reason", reason))
                    .cause(e)
                    .build();
        } catch (IllegalArgumentException e) {
            // Handle other validation errors from user creation
            String reason = "INVALID_INPUT";
            LOGGER.warn("Registration attempt failed: validation error. ipAddress={}, userAgent={}",
                    ipAddress, userAgent);
            auditService.logRegistrationAttempt(username, false, ipAddress, userAgent, e.getMessage());
            complianceMetricsService.incrementFailedLoginAttempts(reason);

            throw GraphqlErrorException.newErrorException()
                    .message("Registration failed: invalid input")
                    .extensions(Map.of("reason", reason))
                    .cause(e)
                    .build();
        } catch (Exception e) {
            // Handle other errors
            String reason = "INTERNAL_ERROR";
            LOGGER.error("Registration failed. ipAddress={}, userAgent={}",
                    ipAddress, userAgent, e);
            auditService.logRegistrationAttempt(username, false, ipAddress, userAgent, e.getMessage());
            complianceMetricsService.incrementFailedLoginAttempts(reason);

            throw GraphqlErrorException.newErrorException()
                    .message("Registration failed")
                    .extensions(Map.of("reason", "INTERNAL_ERROR"))
                    .cause(e)
                    .build();
        }
    }

    @MutationMapping
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 }, extraTags = { "operation",
            "logout" })
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
     * Checks the active Spring profiles to identify production (e.g., "prod",
     * "production").
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

    private AuthenticatedUser extractAuthenticatedUser(Authentication authentication, String username, String ipAddress,
            String userAgent) {
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

    /**
     * Sets the JWT token as an httpOnly cookie in the response.
     * The cookie is:
     * - httpOnly: Not accessible from JavaScript (prevents XSS attacks)
     * - Secure: Only sent over HTTPS in production
     * - SameSite: Strict to prevent CSRF attacks
     * 
     * @param response The HTTP response to set the cookie on
     * @param token    The JWT token to store
     */
    private void setAuthTokenCookie(HttpServletResponse response, String token) {
        // Calculate max age in seconds (jwtExpirationInMs is in milliseconds)
        int maxAgeSeconds = (int) (jwtExpirationInMs / 1000);

        // Build the Set-Cookie header value
        StringBuilder cookieValue = new StringBuilder();
        cookieValue.append("auth-token=").append(token);
        cookieValue.append("; Max-Age=").append(maxAgeSeconds);
        cookieValue.append("; Path=/");
        cookieValue.append("; HttpOnly");
        cookieValue.append("; SameSite=Strict");

        // Only set Secure flag in production (when scheme is https)
        // For development, we allow http
        if ("https".equalsIgnoreCase(System.getenv("SCHEME")) ||
                "true".equalsIgnoreCase(System.getenv("SECURE_COOKIES"))) {
            cookieValue.append("; Secure");
        }

        response.addHeader("Set-Cookie", cookieValue.toString());
        LOGGER.debug("JWT token set as httpOnly cookie with max age {} seconds", maxAgeSeconds);
    }
}
