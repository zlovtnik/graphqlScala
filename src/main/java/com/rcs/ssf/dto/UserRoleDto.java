package com.rcs.ssf.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for user role assignment details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleDto {
    private Long id;
    private Long userId;
    private RoleDto role;
    private UserDto grantedBy;
    private Instant grantedAt;
    private Instant expiresAt;

    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return !isExpired();
    }
}
