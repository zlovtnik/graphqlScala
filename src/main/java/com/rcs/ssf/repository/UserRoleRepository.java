package com.rcs.ssf.repository;

import com.rcs.ssf.entity.UserRole;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository for UserRole entity using R2DBC (reactive).
 * Provides reactive database operations for user-role assignments.
 */
@Repository
public interface UserRoleRepository extends R2dbcRepository<UserRole, Long> {
    
    /**
     * Find all active roles for a user (non-expired)
     */
    @Query("""
        SELECT * FROM user_roles 
        WHERE user_id = :userId 
        AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
        ORDER BY granted_at DESC
        """)
    Flux<UserRole> findActiveRolesByUserId(Long userId);
    
    /**
     * Find all roles for a user including expired ones
     */
    @Query("SELECT * FROM user_roles WHERE user_id = :userId ORDER BY granted_at DESC")
    Flux<UserRole> findAllRolesByUserId(Long userId);
    
    /**
     * Find a specific role assignment
     */
    @Query("""
        SELECT * FROM user_roles 
        WHERE user_id = :userId AND role_id = :roleId 
        AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
        """)
    Mono<UserRole> findActiveRoleAssignment(Long userId, Long roleId);
    
    /**
     * Delete role assignment by user and role IDs
     */
    @Query("DELETE FROM user_roles WHERE user_id = :userId AND role_id = :roleId")
    Mono<Void> deleteByUserIdAndRoleId(Long userId, Long roleId);
    
    /**
     * Find all expired role assignments
     */
    @Query("SELECT * FROM user_roles WHERE expires_at <= CURRENT_TIMESTAMP")
    Flux<UserRole> findExpiredRoles();
    
    /**
     * Check if user has a specific role
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM user_roles ur
        INNER JOIN roles r ON ur.role_id = r.id
        WHERE ur.user_id = :userId 
        AND r.name = :roleName
        AND (ur.expires_at IS NULL OR ur.expires_at > CURRENT_TIMESTAMP)
        """)
    Mono<Boolean> userHasRole(Long userId, String roleName);
}
