package com.rcs.ssf.graphql;

import com.rcs.ssf.dto.UserDto;
import com.rcs.ssf.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

@Controller
public class AuthQuery {

    @Autowired
    private UserService userService;

    @QueryMapping
    public UserDto getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isAuthenticatedUser(auth)) {
            // Fetch user from database using username
            return userService.findByUsername(auth.getName())
                    .map(u -> new UserDto(u.getId(), u.getUsername(), u.getEmail()))
                    .orElse(null);
        }
        return null;
    }

    private static boolean isAuthenticatedUser(Authentication auth) {
        return auth != null && auth.isAuthenticated() && auth.getName() != null && !auth.getName().isEmpty() && !"anonymousUser".equals(auth.getName());
    }
}
