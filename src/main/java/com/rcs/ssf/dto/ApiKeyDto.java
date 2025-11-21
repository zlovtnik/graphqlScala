package com.rcs.ssf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for API key responses (safe to send to client).
 * Never expose full key hash, only masked representation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyDto {
    private Long id;
    private Long userId;

    @NotBlank(message = "Key name must be provided")
    private String keyName;

    private String keyPreview; // Only first/last 8 chars: sk_...abc123

    private Long lastUsedAt;
    private Long revokedAt;
    private Long expiresAt;
    private Long createdAt;
    private Long updatedAt;
    
    private String status; // ACTIVE, REVOKED, or EXPIRED (internal use only)
    
    private Boolean isActive; // true if not revoked and not expired, false otherwise
}
