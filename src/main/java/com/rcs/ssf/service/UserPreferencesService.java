package com.rcs.ssf.service;

import com.rcs.ssf.dto.UserPreferencesDto;
import com.rcs.ssf.entity.UserPreferences;
import com.rcs.ssf.repository.UserPreferencesRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Service for managing user preferences with Redis caching.
 * Preferences are cached for approximately 60 minutes (per cache configuration) to reduce database hits.
 * 
 * Note: On cache miss, concurrent requests may attempt to create duplicate default preferences.
 * Relies on database uniqueness constraint on user_id to enforce single record per user.
 * Consider implementing idempotent upsert at repository layer if duplicates become a concern.
 */
@Slf4j
@Service
public class UserPreferencesService {
    private static final String CACHE_NAME = "user-preferences";
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(5);

    private final UserPreferencesRepository userPreferencesRepository;

    public UserPreferencesService(UserPreferencesRepository userPreferencesRepository) {
        this.userPreferencesRepository = userPreferencesRepository;
    }

    /**
     * Get user preferences with caching.
     */
    @Cacheable(value = CACHE_NAME, key = "#userId")
    public UserPreferencesDto getPreferencesByUserId(Long userId) {
        log.debug("Fetching preferences for user: {}", userId);
        Optional<UserPreferences> prefs = userPreferencesRepository
                .findByUserId(userId)
                .timeout(OPERATION_TIMEOUT)
                .blockOptional();

        if (prefs.isPresent()) {
            return mapToDto(prefs.get());
        }

        // Create default preferences if none exist
        log.debug("Creating default preferences for user: {}", userId);
        UserPreferences newPrefs = new UserPreferences(userId);
        UserPreferences saved = userPreferencesRepository
                .save(newPrefs)
                .timeout(OPERATION_TIMEOUT)
                .block();

        if (saved == null) {
            throw new IllegalStateException("Failed to create default preferences for userId: " + userId);
        }

        return mapToDto(saved);
    }

    /**
     * Update user preferences with cache eviction.
     * Uses upsert semantics: if preferences don't exist, creates default preferences first.
     * This ensures the first call to updateUserPreferences succeeds even for new accounts.
     * Sets updatedAt timestamp to track when preferences were last modified.
     */
    @CacheEvict(value = CACHE_NAME, key = "#userId")
    public UserPreferencesDto updatePreferences(Long userId, UserPreferencesDto dto) {
        log.debug("Updating preferences for user: {}", userId);

        // Upsert: fetch existing or create new default preferences
        UserPreferences existing = userPreferencesRepository
                .findByUserId(userId)
                .timeout(OPERATION_TIMEOUT)
                .blockOptional()
                .orElseGet(() -> {
                    log.debug("Creating default preferences for user: {} during update", userId);
                    return new UserPreferences(userId);
                });

        existing.setTheme(dto.getTheme() != null ? dto.getTheme() : existing.getTheme());
        existing.setLanguage(dto.getLanguage() != null ? dto.getLanguage() : existing.getLanguage());

        if (dto.getNotificationEmails() != null) {
            existing.setNotificationEmails(dto.getNotificationEmails());
        }
        if (dto.getNotificationPush() != null) {
            existing.setNotificationPush(dto.getNotificationPush());
        }
        if (dto.getNotificationLoginAlerts() != null) {
            existing.setNotificationLoginAlerts(dto.getNotificationLoginAlerts());
        }
        if (dto.getNotificationSecurityUpdates() != null) {
            existing.setNotificationSecurityUpdates(dto.getNotificationSecurityUpdates());
        }
        
        // Update the modification timestamp
        existing.setUpdatedAt(System.currentTimeMillis());
        
        UserPreferences updated = userPreferencesRepository
                .save(existing)
                .timeout(OPERATION_TIMEOUT)
                .block();
        
        if (updated == null) {
            throw new IllegalStateException("Failed to update preferences for userId: " + userId);
        }

        log.debug("Preferences updated for user: {}", userId);
        return mapToDto(updated);
    }

    /**
     * Clear cache for preferences (used when user deleted).
     */
    @CacheEvict(value = CACHE_NAME, key = "#userId")
    public void clearPreferencesCache(Long userId) {
        log.debug("Cleared preferences cache for user: {}", userId);
    }

    private UserPreferencesDto mapToDto(UserPreferences entity) {
        if (entity == null) {
            return null;
        }
        UserPreferencesDto dto = new UserPreferencesDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setTheme(entity.getTheme());
        dto.setLanguage(entity.getLanguage());
        dto.setNotificationEmails(entity.getNotificationEmails());
        dto.setNotificationPush(entity.getNotificationPush());
        dto.setNotificationLoginAlerts(entity.getNotificationLoginAlerts());
        dto.setNotificationSecurityUpdates(entity.getNotificationSecurityUpdates());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
