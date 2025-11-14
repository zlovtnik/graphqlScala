package com.rcs.ssf.controller;

import com.rcs.ssf.config.TestDatabaseConfig;
import com.rcs.ssf.dynamic.DynamicCrudGateway;
import com.rcs.ssf.entity.User;
import com.rcs.ssf.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.rcs.ssf.config.MinioConfig.class))
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "app.minio.url=http://localhost:9000",
    "app.minio.access-key=test-access-key",
    "app.minio.secret-key=test-secret-key",
    "app.jwt.secret=test-jwt-secret-with-sufficient-entropy-for-validation-purposes-abcdef123456"
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private MinioClient minioClient;

    @MockBean
    private DynamicCrudGateway dynamicCrudGateway;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @SuppressWarnings("null")
    void getUserById_WhenUserExists_ReturnsUser() throws Exception {
        User user = new User();
        UUID userId = UUID.randomUUID();
        user.setId(userId);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword("password123");

        when(userService.findById(userId)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/" + userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @SuppressWarnings("null")
    void getUserById_WhenUserDoesNotExist_ReturnsEmpty() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.findById(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/" + userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    @SuppressWarnings("null")
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

    @Test
    @SuppressWarnings("null")
    void updateUser_WithNullUsername_SkipsUsernameUpdate() throws Exception {
        UUID userId = UUID.randomUUID();
        User existing = new User();
        existing.setId(userId);
        existing.setUsername("olduser");
        existing.setEmail("old@example.com");
        existing.setPassword("password123");

        User updated = new User();
        updated.setId(userId);
        updated.setUsername("olduser");  // unchanged
        updated.setEmail("new@example.com");
        updated.setPassword("password123");

        when(userService.updateUser(userId, Optional.empty(), Optional.of("new@example.com"), Optional.empty()))
                .thenReturn(updated);

        UserController.UpdateUserRequest request = new UserController.UpdateUserRequest(null, "new@example.com", null);

        mockMvc.perform(put("/api/users/" + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.username").value("olduser"))
                .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    @Test
    @SuppressWarnings("null")
    void updateUser_WithNullEmail_SkipsEmailUpdate() throws Exception {
        UUID userId = UUID.randomUUID();
        User existing = new User();
        existing.setId(userId);
        existing.setUsername("olduser");
        existing.setEmail("old@example.com");
        existing.setPassword("password123");

        User updated = new User();
        updated.setId(userId);
        updated.setUsername("newuser");
        updated.setEmail("old@example.com");  // unchanged
        updated.setPassword("password123");

        when(userService.updateUser(userId, Optional.of("newuser"), Optional.empty(), Optional.empty()))
                .thenReturn(updated);

        UserController.UpdateUserRequest request = new UserController.UpdateUserRequest("newuser", null, null);

        mockMvc.perform(put("/api/users/" + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.email").value("old@example.com"));
    }

    @Test
    @SuppressWarnings("null")
    void updateUser_WithAllNullFields_NoChanges() throws Exception {
        UUID userId = UUID.randomUUID();
        User existing = new User();
        existing.setId(userId);
        existing.setUsername("olduser");
        existing.setEmail("old@example.com");
        existing.setPassword("password123");

        when(userService.updateUser(userId, Optional.empty(), Optional.empty(), Optional.empty()))
                .thenReturn(existing);

        UserController.UpdateUserRequest request = new UserController.UpdateUserRequest(null, null, null);

        mockMvc.perform(put("/api/users/" + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.username").value("olduser"))
                .andExpect(jsonPath("$.email").value("old@example.com"));
    }
}
