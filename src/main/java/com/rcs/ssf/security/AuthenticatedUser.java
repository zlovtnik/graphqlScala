package com.rcs.ssf.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public class AuthenticatedUser extends User {

    private final UUID id;

    public AuthenticatedUser(UUID id,
                             String username,
                             String password,
                             Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    public UUID getId() {
        return id;
    }
}
