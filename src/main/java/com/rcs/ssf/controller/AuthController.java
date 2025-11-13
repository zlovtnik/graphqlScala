package com.rcs.ssf.controller;

import com.rcs.ssf.dto.AuthRequest;
import com.rcs.ssf.dto.AuthResponse;
import com.rcs.ssf.dto.UserDto;
import com.rcs.ssf.security.JwtTokenProvider;
import com.rcs.ssf.service.AuditService;
import com.rcs.ssf.service.UserService;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody AuthRequest loginRequest, HttpServletRequest request) {
        String username = loginRequest.getUsername();
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            username,
                            loginRequest.getPassword()
                    )
            );

            String jwt = jwtTokenProvider.generateToken(authentication);
            auditService.logLoginAttempt(username, true, ipAddress, userAgent, null);

            var user = userService.findByUsername(username).orElseThrow(() -> {
                LOGGER.error("Authenticated user missing in datastore for session start. username={}, ipAddress={}, userAgent={}",
                        username, ipAddress, userAgent);
                return new AuthenticationCredentialsNotFoundException("Authenticated user record not found for session start");
            });
            auditService.logSessionStart(user.getId().toString(), jwt, ipAddress, userAgent);
            return ResponseEntity.ok(new AuthResponse(jwt, UserDto.from(user)));
        } catch (AuthenticationException e) {
            auditService.logLoginAttempt(username, false, ipAddress, userAgent, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid username or password");
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String bearerToken) {
        try {
            if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Missing or invalid Authorization header");
            }

            String token = bearerToken.substring(7);
            boolean isValid = jwtTokenProvider.validateToken(token);

            if (isValid) {
                String username = jwtTokenProvider.getUsernameFromJwt(token);
                return ResponseEntity.ok(new TokenValidationResponse(true, username));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenValidationResponse(false, null));
            }
        } catch (JwtException | IllegalArgumentException e) {
            LOGGER.error("Token validation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Token validation failed");
        }
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

    public record TokenValidationResponse(boolean valid, String username) {}
}