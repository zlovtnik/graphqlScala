package com.rcs.ssf.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for revoking a role from a user
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevokeRoleDto {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Role name is required")
    @Size(max = 50, message = "Role name must not exceed 50 characters")
    private String roleName;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
