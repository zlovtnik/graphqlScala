package com.rcs.ssf.repository;

import com.rcs.ssf.entity.ApiKey;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for API key lifecycle management.
 */
public interface ApiKeyRepository extends R2dbcRepository<ApiKey, Long> {
    Flux<ApiKey> findByUserId(Long userId);

    @Query("SELECT * FROM api_keys WHERE user_id = :userId AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > :currentTime)")
    Flux<ApiKey> findActiveByUserId(Long userId, Long currentTime);

    @Query("SELECT * FROM api_keys WHERE revoked_at IS NULL AND (expires_at IS NULL OR expires_at > :currentTime)")
    Flux<ApiKey> findAllActive(Long currentTime);

    Mono<ApiKey> findByKeyHash(String keyHash);

    @Query("DELETE FROM api_keys WHERE user_id = :userId AND (revoked_at IS NOT NULL OR expires_at < :cutoffTime)")
    Mono<Void> deleteExpiredKeys(Long userId, Long cutoffTime);
}
