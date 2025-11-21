package com.rcs.ssf.dto;

import lombok.ToString;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for API key creation responses.
 * The raw key is only shown once at creation time.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "rawKey")
public class CreateApiKeyResponseDto {
    private Long id;
    private String keyName;
    private String rawKey; // Only shown once at creation
    private String keyPreview; // Preview shown in API keys list
    private Long expiresAt;
    private Long createdAt;
    private String warning; // "Save this key somewhere safe..."
}
