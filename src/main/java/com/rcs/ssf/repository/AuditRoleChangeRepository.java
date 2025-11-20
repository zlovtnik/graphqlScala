package com.rcs.ssf.repository;

import com.rcs.ssf.entity.AuditRoleChange;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Repository for AuditRoleChange entity using R2DBC (reactive).
 * Provides reactive database operations for role audit logs.
 */
@Repository
public interface AuditRoleChangeRepository extends R2dbcRepository<AuditRoleChange, Long> {
    
    /**
     * Find audit log entries for a specific user
     */
    @Query("""
        SELECT * FROM audit_role_changes 
        WHERE user_id = $1 
        ORDER BY created_at DESC
        OFFSET $2 ROWS FETCH NEXT $3 ROWS ONLY
        """)
    Flux<AuditRoleChange> findByUserId(Long userId, int offset, int limit);
    
    /**
     * Find all audit log entries with pagination
     */
    @Query("""
        SELECT * FROM audit_role_changes 
        ORDER BY created_at DESC
        OFFSET $1 ROWS FETCH NEXT $2 ROWS ONLY
        """)
    Flux<AuditRoleChange> findAllAuditEntries(int offset, int limit);
    
    /**
     * Find audit entries for a specific action
     */
    @Query("""
        SELECT * FROM audit_role_changes 
        WHERE action = $1 
        ORDER BY created_at DESC
        OFFSET $2 ROWS FETCH NEXT $3 ROWS ONLY
        """)
    Flux<AuditRoleChange> findByAction(String action, int offset, int limit);
}
