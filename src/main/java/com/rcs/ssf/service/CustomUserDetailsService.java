package com.rcs.ssf.service;

import com.rcs.ssf.SecurityProperties;
import com.rcs.ssf.entity.User;
import com.rcs.ssf.repository.UserRepository;
import com.rcs.ssf.repository.UserRoleRepository;
import com.rcs.ssf.security.AuthenticatedUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Reactive user details service for Spring Security.
 * 
 * This service loads user details from the database without blocking,
 * using Spring Data R2DBC reactive repositories.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;
    private final UserRoleRepository userRoleRepository;

    public CustomUserDetailsService(UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            SecurityProperties securityProperties) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.securityProperties = securityProperties;
    }

    /**
     * Find user details by username without blocking.
     *
     * @param username the username to search for
     * @return Mono of UserDetails if found, otherwise error signal
     */
    public Mono<UserDetails> findByUsername(String username) {
        return userRepository.findByUsername(username)
            .switchIfEmpty(Mono.error(() -> new UsernameNotFoundException("User not found: " + username)))
            .flatMap(user -> loadUserAuthorities(user)
                .map(authorities -> buildUserDetails(user, authorities)));
    }

    /**
     * Blocking implementation of UserDetailsService.loadUserByUsername() for Spring
     * Security.
     * 
     * This method blocks the reactive call to support synchronous servlet filter
     * usage
     * in JwtAuthenticationFilter. The blocking is necessary because servlet filters
     * operate in a blocking context.
     *
     * @param username the username to search for
     * @return UserDetails if found
     * @throws UsernameNotFoundException if user is not found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return findByUsername(username).block();
    }

    /**
     * Builds UserDetails from a User entity without blocking.
     *
     * @param user the user entity
     * @return AuthenticatedUser with authorities
     */
    private UserDetails buildUserDetails(User user, List<GrantedAuthority> authorities) {
        List<GrantedAuthority> immutableAuthorities = List.copyOf(authorities);

        return new AuthenticatedUser(
                Objects.requireNonNull(user.getId(), "User ID must not be null"),
                user.getUsername(),
                user.getPassword(),
                immutableAuthorities);
    }

    /**
     * Fetches the user's roles and converts them to GrantedAuthority instances.
     * 
     * Implements least-privilege principle: Users receive only the roles explicitly
     * assigned to them.
     * If the user has no roles, an empty authority list is returned (no implicit
     * authorities).
     * 
     * Legacy Backward Compatibility:
     * If app.security.enableDefaultUserRole=true is set in configuration, users
     * with no explicit roles
     * will receive ROLE_USER as a fallback (only for systems requiring backward
     * compatibility).
     * Default behavior (enableDefaultUserRole=false) returns empty list for users
     * with no roles.
     * 
     * Once the User entity is extended with roles/role relationships, this method
     * will:
     * - Fetch roles from user.getRoles() if available
     * - Map each role to a GrantedAuthority with "ROLE_" prefix
     * - Still respect the enableDefaultUserRole flag for any remaining empty cases
     *
     * @param user the user entity
     * @return list of GrantedAuthority instances for the user (may be empty if no
     *         roles assigned and flag disabled)
     */
    private Mono<List<GrantedAuthority>> loadUserAuthorities(User user) {
        if (user.getId() == null) {
            return Mono.just(applyDefaultRoleIfNeeded(List.of()));
        }

        return userRoleRepository.findActiveRoleNamesByUserId(user.getId())
                .map(this::normalizeRoleName)
                .filter(name -> name != null && !name.isBlank())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(ArrayList::new))
                .map(this::applyDefaultRoleIfNeeded);
    }

    private List<GrantedAuthority> applyDefaultRoleIfNeeded(List<? extends GrantedAuthority> authorities) {
        if (!authorities.isEmpty()) {
            return List.copyOf(authorities);
        }
        if (securityProperties.isEnableDefaultUserRole()) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return List.of();
    }

    private String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return null;
        }
        String trimmed = roleName.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
    }
}
