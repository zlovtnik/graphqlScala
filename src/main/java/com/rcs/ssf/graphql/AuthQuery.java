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
                    .map(UserDto::from)
                    .orElse(null);
        }
        return null;
    }

    private static boolean isAuthenticatedUser(Authentication auth) {
        if (auth == null) {
            return false;
        }
        
        // Check if authentication is marked as authenticated
        boolean authenticated = auth.isAuthenticated();
        if (!authenticated) {
            return false;
        }
        
        // Check if username is present and not null
        String username = auth.getName();
        boolean hasValidName = username != null && !username.isEmpty();
        if (!hasValidName) {
            return false;
        }
        
        // Check if user is not the anonymous placeholder
        boolean notAnonymous = !"anonymousUser".equals(username);
        
        return notAnonymous;
    }
}
