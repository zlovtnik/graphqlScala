package com.rcs.ssf.graphql;

import com.rcs.ssf.dto.AuditRoleChangeDto;
import com.rcs.ssf.dto.RoleDto;
import com.rcs.ssf.service.RoleService;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * GraphQL Query resolver for admin role operations
 */
@Slf4j
@Controller
public class AdminQuery {
    
    @Autowired
    private RoleService roleService;

    /**
     * Get user roles - authenticated users can query their own, admins can query any
     */
    @QueryMapping
    @Timed(value = "graphql.resolver.duration", percentiles = {0.5, 0.95, 0.99})
    public Mono<java.util.List<RoleDto>> userRoles(@Argument Long userId) {
        Long requestingUserId = getCurrentUserId();
        
        // If no userId provided, use current user's ID
        if (userId == null) {
            userId = requestingUserId;
        }
        
        // Non-admins can only query their own roles
        Long finalUserId = userId;
        if (!hasAdminRole() && !finalUserId.equals(requestingUserId)) {
            return Mono.error(new SecurityException("Cannot view roles for other users"));
        }
        
        return roleService.getUserRolesAsEntities(finalUserId)
            .map(role -> new RoleDto(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getCreatedAt(),
                role.getUpdatedAt()
            ))
            .collectList()
            .doOnError(err -> log.error("Error fetching user roles: {}", err.getMessage()));
    }

    /**
     * Get all available roles (admin only)
     */
    @QueryMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Timed(value = "graphql.resolver.duration", percentiles = {0.5, 0.95, 0.99})
    public Flux<RoleDto> availableRoles() {
        return roleService.getAllRoles()
            .map(role -> new RoleDto(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getCreatedAt(),
                role.getUpdatedAt()
            ))
            .doOnError(err -> log.error("Error fetching available roles: {}", err.getMessage()));
    }

    /**
     * Get audit log for role changes (admin only)
     */
    @QueryMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Timed(value = "graphql.resolver.duration", percentiles = {0.5, 0.95, 0.99})
    public Flux<AuditRoleChangeDto> roleAuditLog(
            @Argument Long userId,
            @Argument String action,
            @Argument Integer limit,
            @Argument Integer offset) {
        
        int pageLimit = limit != null ? limit : 50;
        int pageOffset = offset != null ? offset : 0;
        
        return roleService.getAuditLog(userId, pageOffset, pageLimit)
            .map(audit -> new AuditRoleChangeDto(
                audit.getId(),
                audit.getUserId(),
                "", // username will be resolved separately if needed
                audit.getRoleName(),
                audit.getAction(),
                null, // performedBy will be resolved separately
                audit.getReason(),
                audit.getIpAddress(),
                audit.getCreatedAt()
            ))
            .doOnError(err -> log.error("Error fetching role audit log: {}", err.getMessage()));
    }

    /**
     * Get current authenticated user ID
     */
    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        return null;
    }

    /**
     * Check if current user has admin role
     */
    private boolean hasAdminRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || 
                          a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }
}
