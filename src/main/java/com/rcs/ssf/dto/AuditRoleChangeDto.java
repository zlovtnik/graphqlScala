package com.rcs.ssf.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for audit role change logs
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditRoleChangeDto {
    private Long id;
    private Long userId;
    private String username;
    private String roleName;
    private String action; // GRANT, REVOKE, EXPIRE
    private UserDto performedBy;
    private String reason;
    private String ipAddress;
    private Instant createdAt;
}
