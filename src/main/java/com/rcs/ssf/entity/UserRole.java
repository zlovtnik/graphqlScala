package com.rcs.ssf.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * UserRole entity representing the junction between users and roles.
 * Supports role expiration and audit trails via granted_by and granted_at.
 */
@Table("user_roles")
@Data
@NoArgsConstructor
public class UserRole {
    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("role_id")
    private Long roleId;

    @Column("granted_by")
    private Long grantedBy;

    @Column("granted_at")
    private Instant grantedAt;

    @Column("expires_at")
    private Instant expiresAt;

    @PersistenceCreator
    public UserRole(Long id, Long userId, Long roleId, Long grantedBy, Instant grantedAt, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.roleId = roleId;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
        this.expiresAt = expiresAt;
    }

    public UserRole(Long userId, Long roleId, Long grantedBy) {
        this.userId = userId;
        this.roleId = roleId;
        this.grantedBy = grantedBy;
        this.grantedAt = Instant.now();
    }

    public UserRole(Long userId, Long roleId, Long grantedBy, Instant expiresAt) {
        this.userId = userId;
        this.roleId = roleId;
        this.grantedBy = grantedBy;
        this.grantedAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    /**
     * Check if this role assignment has expired
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if this role assignment is currently active
     */
    public boolean isActive() {
        return !isExpired();
    }
}
