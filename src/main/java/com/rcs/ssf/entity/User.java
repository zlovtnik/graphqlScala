package com.rcs.ssf.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("users")
@Data
@NoArgsConstructor
public class User {
    @Id
    private UUID id;

    @Column("username")
    @NotBlank(message = "Username is required and cannot be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @Column("password")
    @NotBlank(message = "Password is required and cannot be blank")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column("email")
    @NotBlank(message = "Email is required and cannot be blank")
    @Email(message = "Email should be valid")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    private String email;

    // Custom constructor for convenient creation
    public User(String username, String password, String email) {
        if (username == null || username.isBlank() || password == null || password.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("username, password, and email must not be null or blank");
        }
        this.username = username;
        this.password = password;
        this.email = email;
    }
}
