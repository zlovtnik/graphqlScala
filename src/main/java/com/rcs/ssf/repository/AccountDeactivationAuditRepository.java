package com.rcs.ssf.repository;

import com.rcs.ssf.entity.AccountDeactivationAudit;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for account deactivation audit records.
 */
public interface AccountDeactivationAuditRepository extends R2dbcRepository<AccountDeactivationAudit, Long> {
    /**
     * Find the most recent deactivation audit entry for a user.
     * 
     * @param userId the user ID
     * @return Mono containing the most recent audit entry, or empty if none exists
     */
    Mono<AccountDeactivationAudit> findFirstByUserIdOrderByTimestampDesc(Long userId);
}
