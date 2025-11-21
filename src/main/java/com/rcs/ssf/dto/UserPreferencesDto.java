package com.rcs.ssf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user preference updates.
 * Supports theme, language, and notification toggle preferences.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesDto {
    private Long id;
    private Long userId;

    @NotBlank(message = "Theme must be provided")
    private String theme; // light, dark, auto

    @NotBlank(message = "Language must be provided")
    private String language; // en, es, fr, de, etc.

    private Boolean notificationEmails;
    private Boolean notificationPush;
    private Boolean notificationLoginAlerts;
    private Boolean notificationSecurityUpdates;

    private Long createdAt;
    private Long updatedAt;
}
