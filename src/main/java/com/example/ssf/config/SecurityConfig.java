package com.example.ssf.config;

import com.example.ssf.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security Configuration for JWT-based authentication.
 *
 * Authentication Flow:
 * 1. JwtAuthenticationFilter (servlet filter) - extracts JWT from Authorization header
 *    and populates SecurityContext if token is valid
 * 2. SecurityFilterChain - enforces authorization rules on HTTP endpoints
 * 3. GraphQLAuthorizationInstrumentation - enforces authentication for GraphQL operations
 * 4. GraphQLSecurityHandler - translates Spring Security exceptions to GraphQL errors
 *
 * Protected Endpoints:
 * - POST /graphql - requires valid JWT token
 * - All other endpoints except /api/auth/**, /graphiql, /actuator/**
 *
 * Public Endpoints:
 * - POST /api/auth/login - login to get JWT token
 * - POST /api/auth/validate - validate token (can be public or protected)
 * - GET /graphiql - GraphQL IDE
 * - /actuator/** - health checks and metrics
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    public SecurityConfig(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder);
        return authenticationManagerBuilder.build();
    }

    /**
     * Configures HTTP security with JWT authentication.
     *
     * The JwtAuthenticationFilter is registered here to run before Spring Security's
     * default UsernamePasswordAuthenticationFilter, allowing JWT tokens to be extracted
     * and validated before the standard authentication flow.
     *
     * @param http HttpSecurity configuration
     * @return configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // Public endpoints for authentication
                        .requestMatchers("/api/auth/**").permitAll()
                        // GraphQL IDE is public (authentication enforced by GraphQLAuthorizationInstrumentation)
                        .requestMatchers("/graphiql").permitAll()
                        // GraphQL endpoint - authentication enforced by GraphQLAuthorizationInstrumentation
                        .requestMatchers("/graphql").permitAll()
                        // Health and metrics
                        .requestMatchers("/actuator/**").permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                // JWT filter extracts token and populates SecurityContext
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
