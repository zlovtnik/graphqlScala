package com.rcs.ssf.graphql;

import com.rcs.ssf.entity.User;
import com.rcs.ssf.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Optional;
import java.util.UUID;

@Controller
@Validated
public class UserMutation {

    @Autowired
    private UserService userService;

    @MutationMapping
    public User createUser(@Argument CreateUserInput input) {
        User user = new User();
        user.setUsername(input.username());
        user.setEmail(input.email());
        user.setPassword(input.password());
        return userService.createUser(user);
    }

    @MutationMapping
    public User updateUser(@Argument UUID id, @Argument UpdateUserInput input) {
        return userService.updateUser(id, Optional.ofNullable(input.username()), Optional.ofNullable(input.email()),
                Optional.ofNullable(input.password()));
    }

    @MutationMapping
    public Boolean deleteUser(@Argument UUID id) {
        return userService.deleteUser(id);
    }

    public record CreateUserInput(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {}
    public record UpdateUserInput(
            @Size(min = 3, max = 50) String username,
            @Email String email,
            @Size(min = 8, max = 100) String password
    ) {}
}