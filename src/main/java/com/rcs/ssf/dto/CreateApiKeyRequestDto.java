package com.rcs.ssf.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for API key creation requests.
 * Validates key name, expiration, and optional description.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiKeyRequestDto {
    private static final long DEFAULT_EXPIRY_DAYS = 90;
    private static final long MIN_EXPIRY_DAYS = 1;
    private static final long MAX_EXPIRY_DAYS = 3650; // ~10 years

    @NotBlank(message = "Key name must be provided")
    @Size(min = 1, max = 100, message = "Key name must be between 1 and 100 characters")
    private String keyName;

    @Min(value = MIN_EXPIRY_DAYS, message = "Expiration days must be at least 1")
    @Max(value = MAX_EXPIRY_DAYS, message = "Expiration days cannot exceed 3650 days (~10 years)")
    private Long expiresInDays; // Optional, defaults to 90 days if null

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description; // Optional description

    /**
     * Get the expiration days with default fallback.
     * Returns the configured value or DEFAULT_EXPIRY_DAYS if null.
     */
    public Long getExpiresInDaysWithDefault() {
        return expiresInDays != null ? expiresInDays : DEFAULT_EXPIRY_DAYS;
    }
}
