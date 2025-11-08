package com.example.ssf.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final PasswordEncoder passwordEncoder;
    private final String encodedAdminPassword;

    public CustomUserDetailsService(PasswordEncoder passwordEncoder, @Value("${admin.password:}") String adminPassword) {
        this.passwordEncoder = passwordEncoder;
        if (adminPassword == null || adminPassword.trim().isEmpty()) {
            throw new IllegalStateException("Admin password must be provided via admin.password property in production");
        }
        this.encodedAdminPassword = passwordEncoder.encode(adminPassword);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // For development, load default admin user
        // In production, load from database repository
        
        if ("admin".equals(username)) {
            return User.builder()
                    .username("admin")
                    .password(encodedAdminPassword)
                    .roles("ADMIN")
                    .build();
        }

        throw new UsernameNotFoundException("User not found: " + username);
    }

    // TODO: Replace with database-backed implementation
    // public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    //     return userRepository.findByUsername(username)
    //             .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    // }
}
