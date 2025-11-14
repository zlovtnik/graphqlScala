package com.rcs.ssf.service;

import com.rcs.ssf.SecurityProperties;
import com.rcs.ssf.entity.User;
import com.rcs.ssf.repository.UserRepository;
import com.rcs.ssf.security.AuthenticatedUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;

    public CustomUserDetailsService(UserRepository userRepository, SecurityProperties securityProperties) {
        this.userRepository = userRepository;
        this.securityProperties = securityProperties;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        // Fetch user's roles and convert to GrantedAuthority instances
        List<GrantedAuthority> authorities = getUserAuthorities(user);
        
        // Make authorities immutable to prevent post-authentication mutation
        List<GrantedAuthority> immutableAuthorities = List.copyOf(authorities);
        
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                immutableAuthorities
        );
    }
    
    /**
     * Fetches the user's roles and converts them to GrantedAuthority instances.
     * 
     * Implements least-privilege principle: Users receive only the roles explicitly assigned to them.
     * If the user has no roles, an empty authority list is returned (no implicit authorities).
     * 
     * Legacy Backward Compatibility:
     * If app.security.enableDefaultUserRole=true is set in configuration, users with no explicit roles
     * will receive ROLE_USER as a fallback (only for systems requiring backward compatibility).
     * Default behavior (enableDefaultUserRole=false) returns empty list for users with no roles.
     * 
     * Once the User entity is extended with roles/role relationships, this method will:
     * - Fetch roles from user.getRoles() if available
     * - Map each role to a GrantedAuthority with "ROLE_" prefix
     * - Still respect the enableDefaultUserRole flag for any remaining empty cases
     *
     * @param user the user entity
     * @return list of GrantedAuthority instances for the user (may be empty if no roles assigned and flag disabled)
     */
    private List<GrantedAuthority> getUserAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        
        // TODO: Once User entity has roles field, replace this with:
        // if (user.getRoles() != null && !user.getRoles().isEmpty()) {
        //     authorities = user.getRoles().stream()
        //             .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
        //             .collect(Collectors.toList());
        // }
        
        // Apply least-privilege principle: only grant implicit default role if explicitly enabled
        if (authorities.isEmpty() && securityProperties.isEnableDefaultUserRole()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        
        return authorities;
    }
}
