package com.rcs.ssf.repository;

import com.rcs.ssf.entity.Role;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repository for Role entity using R2DBC (reactive).
 * Provides reactive database operations for roles.
 */
@Repository
public interface RoleRepository extends R2dbcRepository<Role, Long> {
    
    /**
     * Find a role by its name
     */
    Mono<Role> findByName(String name);
    
    /**
     * Check if a role exists by name
     */
    @Query("SELECT COUNT(*) > 0 FROM roles WHERE name = :name")
    Mono<Boolean> existsByName(String name);
}
