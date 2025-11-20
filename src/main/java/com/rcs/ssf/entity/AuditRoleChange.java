package com.rcs.ssf.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * AuditRoleChange entity for auditing all role-related changes.
 * Tracks who granted/revoked roles and when.
 */
@Table("audit_role_changes")
@Data
@NoArgsConstructor
public class AuditRoleChange {
    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("role_name")
    @NotBlank(message = "Role name is required")
    @Size(max = 50, message = "Role name must not exceed 50 characters")
    private String roleName;

    @Column("action")
    @NotBlank(message = "Action is required")
    @Size(max = 20, message = "Action must not exceed 20 characters")
    private String action; // GRANT, REVOKE, EXPIRE

    @Column("performed_by")
    private Long performedBy;

    @Column("reason")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;

    @Column("ip_address")
    @Size(max = 45, message = "IP address must not exceed 45 characters")
    private String ipAddress;

    @Column("created_at")
    private Instant createdAt;

    @PersistenceCreator
    public AuditRoleChange(Long id, Long userId, String roleName, String action, 
                          Long performedBy, String reason, String ipAddress, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.roleName = roleName;
        this.action = action;
        this.performedBy = performedBy;
        this.reason = reason;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
    }

    public AuditRoleChange(Long userId, String roleName, String action, 
                          Long performedBy, String ipAddress) {
        this.userId = userId;
        this.roleName = roleName;
        this.action = action;
        this.performedBy = performedBy;
        this.ipAddress = ipAddress;
        this.createdAt = Instant.now();
    }

    public AuditRoleChange(Long userId, String roleName, String action, 
                          Long performedBy, String reason, String ipAddress) {
        this.userId = userId;
        this.roleName = roleName;
        this.action = action;
        this.performedBy = performedBy;
        this.reason = reason;
        this.ipAddress = ipAddress;
        this.createdAt = Instant.now();
    }
}
