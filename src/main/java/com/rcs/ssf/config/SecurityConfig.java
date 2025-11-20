package com.rcs.ssf.config;

import com.rcs.ssf.http.filter.RegistrationRateLimitingFilter;
import com.rcs.ssf.security.CspHeaderFilter;
import com.rcs.ssf.security.GraphQLRequestLoggingFilter;
import com.rcs.ssf.security.JwtAuthenticationFilter;
import com.rcs.ssf.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security Configuration for JWT-based authentication.
 *
 * Authentication Flow:
 * 1. JwtAuthenticationFilter (servlet filter) - extracts JWT from Authorization
 * header
 * and populates SecurityContext if token is valid
 * 2. SecurityFilterChain - enforces authorization rules on HTTP endpoints
 * 3. GraphQLAuthorizationInstrumentation - enforces authentication for GraphQL
 * operations
 * 4. GraphQLSecurityHandler - translates Spring Security exceptions to GraphQL
 * errors
 *
 * Protected Endpoints (HTTP-level):
 * - Most endpoints except listed public endpoints below
 *
 * Public Endpoints (HTTP-level):
 * - POST /api/auth/** - login to get JWT token
 * - POST /api/users - user creation (rate limited by
 * RegistrationRateLimitingFilter)
 * - /actuator/health, /actuator/prometheus - readiness and Prometheus scrape
 * targets
 * - POST /graphql - GraphQL endpoint (operation-level authorization enforced by
 * GraphQLAuthorizationInstrumentation; GET is not exposed)
 * - GET /graphql - WebSocket upgrade requests for subscriptions (protocol
 * handshake)
 *
 * Authenticated Endpoints (HTTP-level):
 * - /api/dashboard/** - dashboard statistics (requires valid JWT token)
 * - /graphiql/** - GraphQL IDE (requires authenticated operators in production)
 * - All remaining /actuator/** endpoints
 *
 * WebSocket Security (wss://):
 * - WebSocket connections tunnel through the HTTP upgrade protocol
 * - GET /graphql upgrade requests are permitted at the HTTP level
 * - GraphQL operation-level authentication is enforced by
 * GraphQLAuthorizationInstrumentation for each GraphQL message
 * - JWT tokens are passed as connection parameters or in message headers
 *
 * Note: /graphql does not require authentication at the HTTP layer; instead,
 * authentication
 * and authorization are enforced by GraphQLAuthorizationInstrumentation for
 * each GraphQL
 * operation. This allows public mutations (login, createUser) while denying
 * access to
 * authenticated-only queries and mutations without valid JWT tokens.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final List<String> corsAllowedOriginPatterns;

    public SecurityConfig(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String corsAllowedOrigins) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.corsAllowedOriginPatterns = parseAllowedOrigins(corsAllowedOrigins);
    }

    @Bean
    public CspHeaderFilter cspHeaderFilter() {
        return new CspHeaderFilter();
    }

    /**
     * Define role hierarchy: ROLE_SUPER_ADMIN > ROLE_ADMIN >
     * ROLE_MFA_ADMIN/ROLE_USER
     *
     * This bean is injected into MethodSecurityExpressionHandler (via MethodSecurityConfig)
     * so that @PreAuthorize, @Secured, and @PostAuthorize annotations honor the hierarchy.
     *
     * Example: A user with ROLE_SUPER_ADMIN can perform actions restricted to ROLE_ADMIN
     * or ROLE_USER without explicit role duplication in authorization rules.
     *
     * Also used for expression-based HTTP security checks if switched to expression-based rules.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("SUPER_ADMIN").implies("ADMIN")
                .role("ADMIN").implies("MFA_ADMIN")
                .role("ADMIN").implies("USER")
                .build();
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
        AuthenticationManagerBuilder authenticationManagerBuilder = http
                .getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder);
        return authenticationManagerBuilder.build();
    }

    /**
     * Configures HTTP security with JWT authentication and CSP headers.
     *
     * Filter order (from first to last):
     * 1. GraphQLRequestLoggingFilter (highest precedence) - logs GraphQL requests
     * early
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
    public SecurityFilterChain filterChain(HttpSecurity http, CspHeaderFilter cspHeaderFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter, GraphQLRequestLoggingFilter graphQLRequestLoggingFilter,
            RegistrationRateLimitingFilter registrationRateLimitingFilter, RoleHierarchy roleHierarchy)
            throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // WebSocket subscriptions on /graphql-ws (GET for upgrade, OPTIONS for
                        // preflight)
                        // MUST be first to ensure proper priority matching
                        .requestMatchers(HttpMethod.GET, "/graphql-ws").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/graphql-ws").permitAll()
                        // Public endpoints for authentication
                        .requestMatchers("/api/auth/**").permitAll()
                        // Allow user creation for bootstrap
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        // Dashboard endpoints require authentication
                        .requestMatchers("/api/dashboard/**").authenticated()
                        // GraphQL IDE requires authentication in production
                        .requestMatchers(HttpMethod.GET, "/graphiql/**").authenticated()
                        // Limit GraphQL HTTP exposure to POST requests; instrumentation enforces public
                        // vs protected operations
                        .requestMatchers(HttpMethod.POST, "/graphql").permitAll()
                        // Health and Prometheus metrics remain publicly accessible
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        // Remaining actuator endpoints require authentication
                        .requestMatchers("/actuator/**").authenticated()
                        // All other endpoints require authentication
                        .anyRequest().authenticated())
                // GraphQL request logging filter (highest precedence)
                .addFilterBefore(registrationRateLimitingFilter, SecurityContextHolderFilter.class)
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
        if (!corsAllowedOriginPatterns.isEmpty()) {
            configuration.setAllowedOriginPatterns(corsAllowedOriginPatterns);
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> parseAllowedOrigins(String origins) {
        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toList());
    }
}
