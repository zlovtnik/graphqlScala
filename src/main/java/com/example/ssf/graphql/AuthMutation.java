package com.example.ssf.graphql;

import com.example.ssf.dto.AuthResponse;
import com.example.ssf.security.JwtTokenProvider;
import com.example.ssf.service.AuditService;
import com.example.ssf.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class AuthMutation {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserService userService;

    @MutationMapping
    public AuthResponse login(@Argument String username, @Argument String password, HttpServletRequest request) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            String token = jwtTokenProvider.generateToken(authentication);
            auditService.logLoginAttempt(username, true, ipAddress, userAgent, null);
            // Log session start
            userService.findByUsername(username).ifPresent(user ->
                auditService.logSessionStart(user.getId().toString(), token, ipAddress, userAgent)
            );
            return new AuthResponse(token);
        } catch (org.springframework.security.core.AuthenticationException e) {
            auditService.logLoginAttempt(username, false, ipAddress, userAgent, e.getMessage());
            throw new RuntimeException("Authentication failed", e);
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
}
