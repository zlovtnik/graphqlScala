package com.rcs.ssf.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Manages API key lifecycle for programmatic access.
 * Keys are hashed/encrypted and can be revoked by setting revokedAt.
 */
@Table("api_keys")
@Data
@NoArgsConstructor
public class ApiKey {
    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("key_name")
    private String keyName;

    @Column("key_hash")
    private String keyHash;

    @Column("key_preview")
    private String keyPreview;

    @Column("last_used_at")
    private Long lastUsedAt;

    @Column("revoked_at")
    private Long revokedAt;

    @Column("expires_at")
    private Long expiresAt;

    @Column("created_at")
    private Long createdAt;

    @Column("updated_at")
    private Long updatedAt;

    @PersistenceCreator
    public ApiKey(Long id, Long userId, String keyName, String keyHash, String keyPreview,
            Long lastUsedAt, Long revokedAt, Long expiresAt, Long createdAt, Long updatedAt) {
        this.id = id;
        this.userId = userId;
        this.keyName = keyName;
        this.keyHash = keyHash;
        this.keyPreview = keyPreview;
        this.lastUsedAt = lastUsedAt;
        this.revokedAt = revokedAt;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public ApiKey(Long userId, String keyName, String keyHash, String keyPreview, Long expiresAt) {
        this.userId = userId;
        this.keyName = keyName;
        this.keyHash = keyHash;
        this.keyPreview = keyPreview;
        this.expiresAt = expiresAt;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return System.currentTimeMillis() > expiresAt;
    }

    public boolean isActive() {
        return !isRevoked() && !isExpired();
    }
}
