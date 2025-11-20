package com.rcs.ssf.service;

import com.rcs.ssf.entity.AuditRoleChange;
import com.rcs.ssf.entity.Role;
import com.rcs.ssf.entity.UserRole;
import com.rcs.ssf.repository.AuditRoleChangeRepository;
import com.rcs.ssf.repository.RoleRepository;
import com.rcs.ssf.repository.UserRoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Service for managing user roles and permissions.
 * Provides role assignment, revocation, and audit tracking.
 * Uses caching for performance and reactive programming for scalability.
 */
@Slf4j
@Service
@Transactional
public class RoleService {
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditRoleChangeRepository auditRoleChangeRepository;

    public RoleService(RoleRepository roleRepository,
                      UserRoleRepository userRoleRepository,
                      AuditRoleChangeRepository auditRoleChangeRepository) {
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditRoleChangeRepository = auditRoleChangeRepository;
    }

    /**
     * Grant a role to a user
     * @param userId the user to grant the role to
     * @param roleName the role name to grant
     * @param grantedBy the ID of the user performing the grant
     * @param expiresAt optional expiration timestamp
     * @param ipAddress the IP address of the requester for audit purposes
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @CacheEvict(value = "userRoles", key = "#userId")
    public Mono<UserRole> grantRole(Long userId, String roleName, Long grantedBy, 
                                    Instant expiresAt, String ipAddress) {
        log.info("Granting role {} to user {} by user {}", roleName, userId, grantedBy);
        
        return roleRepository.findByName(roleName)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Role not found: " + roleName)))
            .flatMap(role -> createUserRoleAssignment(userId, role.getId(), grantedBy, expiresAt))
            .flatMap(userRole -> auditRoleChange(userId, roleName, "GRANT", grantedBy, null, ipAddress)
                .then(Mono.just(userRole)))
            .doOnError(err -> log.error("Error granting role {} to user {}: {}", roleName, userId, err.getMessage()));
    }

    /**
     * Revoke a role from a user
     * @param userId the user to revoke the role from
     * @param roleName the role name to revoke
     * @param revokedBy the ID of the user performing the revocation
     * @param reason the reason for revocation
     * @param ipAddress the IP address of the requester for audit purposes
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @CacheEvict(value = "userRoles", key = "#userId")
    public Mono<Void> revokeRole(Long userId, String roleName, Long revokedBy, 
                                 String reason, String ipAddress) {
        log.info("Revoking role {} from user {} by user {}", roleName, userId, revokedBy);
        
        return roleRepository.findByName(roleName)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Role not found: " + roleName)))
            .flatMap(role -> userRoleRepository.deleteByUserIdAndRoleId(userId, role.getId()))
            .flatMap(v -> auditRoleChange(userId, roleName, "REVOKE", revokedBy, reason, ipAddress))
            .doOnError(err -> log.error("Error revoking role {} from user {}: {}", roleName, userId, err.getMessage()));
    }

    /**
     * Get all active roles for a user (cached)
     * @param userId the user ID
     * @return a set of active role names
     */
    @Cacheable(value = "userRoles", key = "#userId")
    public Mono<Set<String>> getUserRoles(Long userId) {
        return userRoleRepository.findActiveRolesByUserId(userId)
            .flatMap(userRole -> {
                @SuppressWarnings("null")
                Long roleId = userRole.getRoleId();
                return roleRepository.findById(roleId)
                    .map(Role::getName);
            })
            .collect(HashSet<String>::new, Set::add)
            .map(set -> (Set<String>) set)
            .switchIfEmpty(Mono.just(new HashSet<>()))
            .doOnSuccess(roles -> log.debug("User {} has roles: {}", userId, roles));
    }

    /**
     * Get all role types for a user (including expired ones)
     * @param userId the user ID
     * @return a flux of user role entities
     */
    public Flux<UserRole> getAllUserRoles(Long userId) {
        return userRoleRepository.findAllRolesByUserId(userId);
    }

    /**
     * Get all active roles for a user as full Role entities
     * @param userId the user ID
     * @return a flux of full role entities
     */
    public Flux<Role> getUserRolesAsEntities(Long userId) {
        return getUserRoles(userId)
            .flatMapMany(roleNames -> Flux.fromIterable(roleNames)
                .flatMap(roleRepository::findByName))
            .doOnNext(role -> log.debug("Retrieved role entity: {}", role.getName()));
    }

    /**
     * Check if a user has a specific role
     * @param userId the user ID
     * @param roleName the role name to check
     * @return true if user has the role and it's active, false otherwise
     */
    public Mono<Boolean> hasRole(Long userId, String roleName) {
        return userRoleRepository.userHasRole(userId, roleName)
            .doOnSuccess(hasRole -> log.debug("User {} has role {}: {}", userId, roleName, hasRole));
    }

    /**
     * Check if a user has all specified roles
     * @param userId the user ID
     * @param roles the roles to check
     * @return true if user has all roles
     */
    public Mono<Boolean> hasAllRoles(Long userId, Set<String> roles) {
        return Flux.fromIterable(roles)
            .flatMap(role -> hasRole(userId, role))
            .all(exists -> exists)
            .defaultIfEmpty(true);
    }

    /**
     * Check if a user has any of the specified roles
     * @param userId the user ID
     * @param roles the roles to check
     * @return true if user has at least one role
     */
    public Mono<Boolean> hasAnyRole(Long userId, Set<String> roles) {
        return Flux.fromIterable(roles)
            .flatMap(role -> hasRole(userId, role))
            .any(exists -> exists)
            .defaultIfEmpty(false);
    }

    /**
     * Get all available roles
     * @return a flux of all roles
     */
    @Cacheable(value = "allRoles")
    public Flux<Role> getAllRoles() {
        return roleRepository.findAll()
            .doOnComplete(() -> log.debug("Retrieved all available roles"));
    }

    /**
     * Get audit log entries for a user
     * @param userId optional user ID to filter by
     * @param offset pagination offset
     * @param limit pagination limit
     * @return a flux of audit entries
     */
    public Flux<AuditRoleChange> getAuditLog(Long userId, int offset, int limit) {
        if (userId != null) {
            return auditRoleChangeRepository.findByUserId(userId, offset, limit);
        }
        return auditRoleChangeRepository.findAllAuditEntries(offset, limit);
    }

    /**
     * Expire roles that have passed their expiration time
     * Called by scheduled task
     */
    @Transactional
    public Mono<Void> expireRoles() {
        log.info("Executing role expiration task");
        
        return userRoleRepository.findExpiredRoles()
            .flatMap(expiredUserRole -> {
                @SuppressWarnings("null")
                Long roleId = expiredUserRole.getRoleId();
                @SuppressWarnings("null")
                Long expiredId = expiredUserRole.getId();
                @SuppressWarnings("null")
                Mono<Role> roleMono = roleRepository.findById(roleId);
                return roleMono
                    .flatMap(role -> {
                        log.debug("Expiring role {} for user {}", role.getName(), expiredUserRole.getUserId());
                        @SuppressWarnings("null")
                        Mono<Void> deleteMono = userRoleRepository.deleteById(expiredId);
                        return deleteMono
                            .flatMap(v -> auditRoleChange(expiredUserRole.getUserId(), role.getName(), 
                                                         "EXPIRE", expiredUserRole.getGrantedBy(), 
                                                         "Role expired", null));
                    });
            })
            .then()
            .doOnError(err -> log.error("Error expiring roles: {}", err.getMessage()));
    }

    /**
     * Create a user-role assignment
     * @param userId the user ID
     * @param roleId the role ID
     * @param grantedBy the user granting the role
     * @param expiresAt optional expiration time
     * @return the created user role
     */
    private Mono<UserRole> createUserRoleAssignment(Long userId, Long roleId, 
                                                     Long grantedBy, Instant expiresAt) {
        UserRole userRole = new UserRole(userId, roleId, grantedBy, expiresAt);
        return userRoleRepository.save(userRole);
    }

    /**
     * Create an audit log entry for role changes
     * @param userId the affected user
     * @param roleName the role name
     * @param action the action (GRANT, REVOKE, EXPIRE)
     * @param performedBy the user performing the action
     * @param reason optional reason
     * @param ipAddress the IP address
     * @return a mono representing the completion
     */
    private Mono<Void> auditRoleChange(Long userId, String roleName, String action,
                                       Long performedBy, String reason, String ipAddress) {
        AuditRoleChange audit = new AuditRoleChange(userId, roleName, action, 
                                                    performedBy, reason, ipAddress);
        return auditRoleChangeRepository.save(audit)
            .then()
            .doOnSuccess(v -> log.debug("Audit log created for role {} action on user {}", 
                                       roleName, userId));
    }
}
