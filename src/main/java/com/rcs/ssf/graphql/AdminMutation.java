package com.rcs.ssf.graphql;

import com.rcs.ssf.dto.GrantRoleDto;
import com.rcs.ssf.dto.RevokeRoleDto;
import com.rcs.ssf.dto.RoleDto;
import com.rcs.ssf.dto.UserDto;
import com.rcs.ssf.dto.UserRoleDto;
import com.rcs.ssf.entity.Role;
import com.rcs.ssf.entity.UserRole;
import com.rcs.ssf.repository.RoleRepository;
import com.rcs.ssf.repository.UserRepository;
import com.rcs.ssf.service.RoleService;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
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

    private final RoleService roleService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public AdminMutation(RoleService roleService,
            RoleRepository roleRepository,
            UserRepository userRepository) {
        this.roleService = roleService;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    /**
     * Grant a role to a user (admin only)
     */
    @MutationMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 })
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
                ipAddress)
                .flatMap(userRole -> resolveRoleDto(userRole, input.getRoleName())
                        .zipWith(resolveGrantedByDto(userRole.getGrantedBy()))
                        .map(tuple -> new UserRoleDto(
                                userRole.getId(),
                                userRole.getUserId(),
                                tuple.getT1(),
                                tuple.getT2(),
                                userRole.getGrantedAt(),
                                userRole.getExpiresAt())))
                .doOnSuccess(dto -> log.info("Role {} granted to user {}", input.getRoleName(), input.getUserId()))
                .doOnError(err -> log.error("Error granting role: {}", err.getMessage()));
    }

    private Mono<RoleDto> resolveRoleDto(UserRole userRole, String fallbackRoleName) {
        Long roleId = userRole.getRoleId();
        if (roleId == null) {
            return Mono.just(new RoleDto(null, fallbackRoleName, null, null, null));
        }
        return roleRepository.findById(roleId)
                .map(this::mapRoleToDto)
                .defaultIfEmpty(new RoleDto(roleId, fallbackRoleName, null, null, null));
    }

    private RoleDto mapRoleToDto(Role role) {
        if (role == null) {
            return new RoleDto(null, null, null, null, null);
        }
        return new RoleDto(role.getId(), role.getName(), role.getDescription(), role.getCreatedAt(),
                role.getUpdatedAt());
    }

    private Mono<UserDto> resolveGrantedByDto(Long grantedByUserId) {
        if (grantedByUserId == null) {
            return Mono.justOrEmpty((UserDto) null);
        }
        return userRepository.findById(grantedByUserId)
                .map(UserDto::from)
                .defaultIfEmpty(new UserDto(grantedByUserId, null, null));
    }

    /**
     * Revoke a role from a user (admin only)
     */
    @MutationMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 })
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
                ipAddress)
                .then(Mono.just(true))
                .doOnSuccess(
                        success -> log.info("Role {} revoked from user {}", input.getRoleName(), input.getUserId()))
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
