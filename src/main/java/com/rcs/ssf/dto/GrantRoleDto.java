package com.rcs.ssf.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for granting a role to a user
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrantRoleDto {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Role name is required")
    @Size(max = 50, message = "Role name must not exceed 50 characters")
    private String roleName;

    private Instant expiresAt;
}
