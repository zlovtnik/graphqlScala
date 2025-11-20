package com.rcs.ssf.graphql;

import com.rcs.ssf.dto.GrantRoleDto;
import com.rcs.ssf.dto.RevokeRoleDto;
import com.rcs.ssf.dto.RoleDto;
import com.rcs.ssf.dto.UserRoleDto;
import com.rcs.ssf.service.RoleService;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import reactor.core.publisher.Mono;

import jakarta.servlet.http.HttpServletRequest;

/**
 * GraphQL Mutation resolver for admin role operations
 */
@Slf4j
@Controller
public class AdminMutation {
    
    @Autowired
    private RoleService roleService;

    /**
     * Grant a role to a user (admin only)
     */
    @MutationMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Timed(value = "graphql.resolver.duration", percentiles = {0.5, 0.95, 0.99})
    public Mono<UserRoleDto> grantRole(@Argument GrantRoleDto input) {
        Long grantedBy = getCurrentUserId();
        String ipAddress = getClientIpAddress();
        
        if (grantedBy == null) {
            return Mono.error(new SecurityException("User not authenticated"));
        }
        
        return roleService.grantRole(
                input.getUserId(),
                input.getRoleName(),
                grantedBy,
                input.getExpiresAt(),
                ipAddress
            )
            .map(userRole -> new UserRoleDto(
                userRole.getId(),
                userRole.getUserId(),
                new RoleDto(null, input.getRoleName(), null, null, null),
                null, // grantedBy user details would be fetched separately
                userRole.getGrantedAt(),
                userRole.getExpiresAt()
            ))
            .doOnSuccess(dto -> log.info("Role {} granted to user {}", input.getRoleName(), input.getUserId()))
            .doOnError(err -> log.error("Error granting role: {}", err.getMessage()));
    }

    /**
     * Revoke a role from a user (admin only)
     */
    @MutationMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Timed(value = "graphql.resolver.duration", percentiles = {0.5, 0.95, 0.99})
    public Mono<Boolean> revokeRole(@Argument RevokeRoleDto input) {
        Long revokedBy = getCurrentUserId();
        String ipAddress = getClientIpAddress();
        
        if (revokedBy == null) {
            return Mono.error(new SecurityException("User not authenticated"));
        }
        
        return roleService.revokeRole(
                input.getUserId(),
                input.getRoleName(),
                revokedBy,
                input.getReason(),
                ipAddress
            )
            .then(Mono.just(true))
            .doOnSuccess(success -> log.info("Role {} revoked from user {}", input.getRoleName(), input.getUserId()))
            .doOnError(err -> log.error("Error revoking role: {}", err.getMessage()));
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
     * Get client IP address from request
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "unknown";
            }
            
            HttpServletRequest request = attrs.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0];
            }
            
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            
            return request.getRemoteAddr();
        } catch (Exception e) {
            log.debug("Could not determine client IP: {}", e.getMessage());
            return "unknown";
        }
    }
}
