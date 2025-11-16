package com.rcs.ssf.graphql;

import com.rcs.ssf.dto.AuthResponse;
import com.rcs.ssf.security.AuthenticatedUser;
import com.rcs.ssf.security.JwtTokenProvider;
import com.rcs.ssf.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import graphql.GraphqlErrorException;

import java.util.Map;

@Controller
public class AuthMutation {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthMutation.class);

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuditService auditService;

    @Autowired(required = false)
    private HttpServletRequest httpServletRequest;

    @MutationMapping
    public AuthResponse login(@Argument String username, @Argument String password) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        
        // Use autowired HttpServletRequest (safe for synchronous HTTP mutations)
        String ipAddress = (httpServletRequest != null) ? getClientIpAddress(httpServletRequest) : "unknown";
        String userAgent = (httpServletRequest != null) ? httpServletRequest.getHeader("User-Agent") : "unknown";
        
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            String token = jwtTokenProvider.generateToken(authentication);
            auditService.logLoginAttempt(username, true, ipAddress, userAgent, null);

            AuthenticatedUser principal = extractAuthenticatedUser(authentication, username, ipAddress, userAgent);
            auditService.logSessionStart(principal.getId().toString(), token, ipAddress, userAgent);

            return new AuthResponse(token);
        } catch (org.springframework.security.core.AuthenticationException e) {
            auditService.logLoginAttempt(username, false, ipAddress, userAgent, e.getMessage());
            throw GraphqlErrorException.newErrorException()
                    .message("Authentication failed")
                    .extensions(Map.of("reason", "INVALID_CREDENTIALS"))
                    .cause(e)
                    .build();
        }
    }

    @MutationMapping
    public boolean logout() {
        // Token invalidation is handled client-side by removing it
        // For server-side blacklisting, implement a token blacklist service
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

