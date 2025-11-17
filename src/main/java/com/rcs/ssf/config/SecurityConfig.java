package com.rcs.ssf.config;

import com.rcs.ssf.security.CspHeaderFilter;
import com.rcs.ssf.security.GraphQLRequestLoggingFilter;
import com.rcs.ssf.security.JwtAuthenticationFilter;
import com.rcs.ssf.security.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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
 * Protected Endpoints (HTTP-level):
 * - Most endpoints except listed public endpoints below
 *
 * Public Endpoints (HTTP-level):
 * - POST /api/auth/** - login to get JWT token
 * - POST /api/users - user creation (bootstrap)
 * - GET /graphiql - GraphQL IDE
 * - POST /graphql - HTTP-public; authorization enforced at GraphQL operation level
 *     (public mutations like login, createUser allowed; authenticated-only operations denied by GraphQLAuthorizationInstrumentation)
 * - /actuator/** - health checks and metrics
 *
 * Note: /graphql does not require authentication at the HTTP layer; instead, authentication
 * and authorization are enforced by GraphQLAuthorizationInstrumentation for each GraphQL
 * operation. This allows public mutations (login, createUser) while denying access to
 * authenticated-only queries and mutations without valid JWT tokens.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public SecurityConfig(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public CspHeaderFilter cspHeaderFilter() {
        return new CspHeaderFilter();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
    }

    @Bean
    public GraphQLRequestLoggingFilter graphQLRequestLoggingFilter() {
        return new GraphQLRequestLoggingFilter();
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
     * Configures HTTP security with JWT authentication and CSP headers.
     *
    * Filter order (from first to last):
    * 1. GraphQLRequestLoggingFilter (highest precedence) - logs GraphQL requests early
    * 2. CspHeaderFilter - Generates nonce and sets strict CSP headers
    * 3. Spring Security SecurityContextHolderFilter - central context population
    * 4. JwtAuthenticationFilter - Extracts and validates JWT tokens
     *
    * The GraphQLRequestLoggingFilter and CspHeaderFilter are added before the
    * Spring Security filter chain (SecurityContextHolderFilter) so that request
    * logging and the response nonce are available early in the request lifecycle.
    * The JwtAuthenticationFilter runs after the SecurityContextHolderFilter and
    * populates the SecurityContext for downstream filter processing.
     *
     * @param http HttpSecurity configuration
     * @return configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CspHeaderFilter cspHeaderFilter, JwtAuthenticationFilter jwtAuthenticationFilter, GraphQLRequestLoggingFilter graphQLRequestLoggingFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // Public endpoints for authentication
                        .requestMatchers("/api/auth/**").permitAll()
                        // Allow user creation for bootstrap
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        // GraphQL IDE is public (authentication enforced by GraphQLAuthorizationInstrumentation)
                        .requestMatchers("/graphiql/**").permitAll()
                        // GraphQL endpoint - permit all requests; authentication enforced by GraphQLAuthorizationInstrumentation
                        // This allows public mutations (login, logout, createUser) while denying authenticated-only operations
                        .requestMatchers("/graphql").permitAll()
                        // Health and metrics
                        .requestMatchers("/actuator/**").permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                // GraphQL request logging filter (highest precedence)
                .addFilterBefore(graphQLRequestLoggingFilter, SecurityContextHolderFilter.class)
                // CSP filter runs after GraphQL logging (generates nonce for every response)
                .addFilterBefore(cspHeaderFilter, SecurityContextHolderFilter.class)
                // JWT filter extracts token and populates SecurityContext
                .addFilterAfter(jwtAuthenticationFilter, SecurityContextHolderFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
