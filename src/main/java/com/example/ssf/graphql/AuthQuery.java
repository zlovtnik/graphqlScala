package com.example.ssf.graphql;

import com.example.ssf.dto.User;
import com.example.ssf.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

@Controller
public class AuthQuery {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @QueryMapping
    public Boolean validateToken(@Argument String token) {
        return jwtTokenProvider.validateToken(token);
    }

    @QueryMapping
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isAuthenticatedUser(auth)) {
            // For now, return a basic User object. In a real app, fetch from database
            return new User(null, auth.getName(), null); // id and email would come from DB
        }
        return null;
    }

    private static boolean isAuthenticatedUser(Authentication auth) {
        return auth != null && auth.isAuthenticated() && auth.getName() != null && !auth.getName().isEmpty() && !"anonymousUser".equals(auth.getName());
    }
}
