package com.example.ssf.controller;

import com.example.ssf.config.TestDatabaseConfig;
import com.example.ssf.entity.User;
import com.example.ssf.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestDatabaseConfig.class, UserControllerTest.NoOpSecurityConfig.class})
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.example.ssf.config.SecurityConfig.class))
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=none"
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor AUTHENTICATED_USER =
            SecurityMockMvcRequestPostProcessors.user("test-user").roles("USER");

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Configuration
    @EnableWebSecurity
    static class NoOpSecurityConfig {

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(10); // Match default production strength
        }

        @Bean
        AuthenticationManager authenticationManager(AuthenticationManagerBuilder builder, PasswordEncoder encoder) throws Exception {
            builder.inMemoryAuthentication()
                    .withUser("test-user")
                    .password(encoder.encode("test-password"))
                    .roles("USER");
            return builder.build();
        }
    }

    @Test
    void getUserById_WhenUserExists_ReturnsUser() throws Exception {
        User user = new User();
        UUID userId = UUID.randomUUID();
        user.setId(userId);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword("password123");

        when(userService.findById(userId)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/" + userId)
                .with(AUTHENTICATED_USER)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void getUserById_WhenUserDoesNotExist_ReturnsEmpty() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.findById(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/" + userId)
                .with(AUTHENTICATED_USER)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void createUser_WhenValidInput_ReturnsCreatedUser() throws Exception {
        User user = new User();
        user.setUsername("newuser");
        user.setEmail("new@example.com");
        user.setPassword("password123");

        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setUsername(user.getUsername());
        savedUser.setEmail(user.getEmail());
        savedUser.setPassword(user.getPassword());

        when(userService.createUser(any(User.class))).thenReturn(savedUser);

        UserController.CreateUserRequest request = new UserController.CreateUserRequest(
                user.getUsername(),
                user.getEmail(),
                user.getPassword()
        );

        mockMvc.perform(post("/api/users")
                .with(AUTHENTICATED_USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", containsString("/api/users/")))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.email").value("new@example.com"));
    }
}
