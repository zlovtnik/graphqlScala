package com.example.ssf.graphql;

import com.example.ssf.entity.User;
import com.example.ssf.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
public class UserQuery {

    @Autowired
    private UserService userService;

    @QueryMapping
    public User getUserById(@Argument UUID id) {
        return userService.findById(id).orElse(null);
    }

    @QueryMapping
    public User getUserByUsername(@Argument String username) {
        return userService.findByUsername(username).orElse(null);
    }
}