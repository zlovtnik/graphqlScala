package com.rcs.ssf.repository;

import com.rcs.ssf.entity.UserPreferences;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for user preferences.
 * Supports Redis caching for fast preference lookups.
 */
public interface UserPreferencesRepository extends R2dbcRepository<UserPreferences, Long> {
    Mono<UserPreferences> findByUserId(Long userId);
}
