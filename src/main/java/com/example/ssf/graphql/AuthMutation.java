package com.example.ssf.graphql;

import com.example.ssf.dto.AuthResponse;
import com.example.ssf.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

@Controller
public class AuthMutation {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MutationMapping
    public AuthResponse login(@Argument String username, @Argument String password) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            String token = jwtTokenProvider.generateToken(authentication);
            return new AuthResponse(token);
        } catch (org.springframework.security.core.AuthenticationException e) {
            throw new RuntimeException("Authentication failed", e);
        }
    }

    @MutationMapping
    public boolean logout() {
        // Token invalidation is handled client-side by removing it
        // For server-side blacklisting, implement a token blacklist service
        return true;
    }
}
