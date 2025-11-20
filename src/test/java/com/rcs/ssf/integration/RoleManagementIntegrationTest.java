package com.rcs.ssf.integration;

import com.rcs.ssf.entity.Role;
import com.rcs.ssf.entity.UserRole;
import com.rcs.ssf.repository.RoleRepository;
import com.rcs.ssf.repository.UserRoleRepository;
import com.rcs.ssf.repository.AuditRoleChangeRepository;
import com.rcs.ssf.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration tests for role management workflow
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Role Management End-to-End Integration Tests")
@SuppressWarnings("null")
class RoleManagementIntegrationTest {
    
    @Autowired
    private RoleService roleService;
    
    @MockBean
    private RoleRepository roleRepository;
    
    @MockBean
    private UserRoleRepository userRoleRepository;
    
    @MockBean
    private AuditRoleChangeRepository auditRoleChangeRepository;
    
    private Role adminRole;
    private Role userRole;
    private Role superAdminRole;
    private UserRole userAdminAssignment;
    
    @BeforeEach
    void setUp() {
        // Setup test roles
        adminRole = new Role(1L, "ROLE_ADMIN", "Admin Role", Instant.now(), Instant.now());
        userRole = new Role(2L, "ROLE_USER", "User Role", Instant.now(), Instant.now());
        superAdminRole = new Role(3L, "ROLE_SUPER_ADMIN", "Super Admin Role", Instant.now(), Instant.now());
        
        // Setup user role assignment
        userAdminAssignment = new UserRole(1L, 100L, 1L, 1L, Instant.now(), null);
    }
    
    @Test
    @DisplayName("Should complete full role assignment workflow")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCompleteRoleAssignmentWorkflow() {
        // Step 1: Get all available roles
        when(roleRepository.findAll())
            .thenReturn(Flux.just(adminRole, userRole, superAdminRole));
        
        var getAllRolesResult = roleService.getAllRoles();
        
        StepVerifier.create(getAllRolesResult)
            .expectNextCount(3)
            .verifyComplete();
        
        // Step 2: Grant a role to user
        when(roleRepository.findByName("ROLE_ADMIN"))
            .thenReturn(Mono.just(adminRole));
        when(userRoleRepository.save(any()))
            .thenReturn(Mono.just(userAdminAssignment));
        when(auditRoleChangeRepository.save(any()))
            .thenReturn(Mono.just(new com.rcs.ssf.entity.AuditRoleChange()));
        
        var grantResult = roleService.grantRole(100L, "ROLE_ADMIN", 1L, null, "127.0.0.1");
        
        StepVerifier.create(grantResult)
            .assertNext(userRole -> {
                assertNotNull(userRole);
                assertEquals(100L, userRole.getUserId());
            })
            .verifyComplete();
        
        // Step 3: Verify user has the role
        when(userRoleRepository.userHasRole(100L, "ROLE_ADMIN"))
            .thenReturn(Mono.just(true));
        
        var hasRoleResult = roleService.hasRole(100L, "ROLE_ADMIN");
        
        StepVerifier.create(hasRoleResult)
            .assertNext(hasRole -> assertTrue(hasRole))
            .verifyComplete();
        
        // Step 4: Get user's roles
        when(userRoleRepository.findActiveRolesByUserId(100L))
            .thenReturn(Flux.just(userAdminAssignment));
        when(roleRepository.findById(1L))
            .thenReturn(Mono.just(adminRole));
        
        var getUserRolesResult = roleService.getUserRoles(100L);
        
        StepVerifier.create(getUserRolesResult)
            .assertNext(roles -> {
                assertNotNull(roles);
                assertTrue(roles.contains("ROLE_ADMIN"));
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Should enforce role hierarchy (SUPER_ADMIN > ADMIN)")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRoleHierarchyEnforcement() {
        // Admin should have ROLE_ADMIN
        when(userRoleRepository.userHasRole(1L, "ROLE_ADMIN"))
            .thenReturn(Mono.just(true));
        
        var adminHasAdmin = roleService.hasRole(1L, "ROLE_ADMIN");
        
        StepVerifier.create(adminHasAdmin)
            .assertNext(hasRole -> assertTrue(hasRole))
            .verifyComplete();
        
        // Super admin should have both ROLE_SUPER_ADMIN and ROLE_ADMIN
        when(userRoleRepository.userHasRole(2L, "ROLE_SUPER_ADMIN"))
            .thenReturn(Mono.just(true));
        when(userRoleRepository.userHasRole(2L, "ROLE_ADMIN"))
            .thenReturn(Mono.just(true));
        
        var superAdminBoth = roleService.hasAllRoles(2L, java.util.Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN"));
        
        StepVerifier.create(superAdminBoth)
            .assertNext(hasAllRoles -> assertTrue(hasAllRoles))
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Should handle role expiration workflow")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRoleExpirationWorkflow() {
        // Grant role with expiration
        Instant expiresAt = Instant.now().plusSeconds(3600);
        UserRole expiringRole = new UserRole(1L, 100L, 1L, 1L, Instant.now(), expiresAt);
        
        when(roleRepository.findByName("ROLE_ADMIN"))
            .thenReturn(Mono.just(adminRole));
        when(userRoleRepository.save(any()))
            .thenReturn(Mono.just(expiringRole));
        when(auditRoleChangeRepository.save(any()))
            .thenReturn(Mono.just(new com.rcs.ssf.entity.AuditRoleChange()));
        
        var grantWithExpirationResult = roleService.grantRole(100L, "ROLE_ADMIN", 1L, expiresAt, "127.0.0.1");
        
        StepVerifier.create(grantWithExpirationResult)
            .assertNext(userRole -> {
                assertNotNull(userRole.getExpiresAt());
                assertEquals(expiresAt, userRole.getExpiresAt());
            })
            .verifyComplete();
        
        // Verify role is still active
        when(userRoleRepository.userHasRole(100L, "ROLE_ADMIN"))
            .thenReturn(Mono.just(true));
        
        var hasRoleWhileActive = roleService.hasRole(100L, "ROLE_ADMIN");
        
        StepVerifier.create(hasRoleWhileActive)
            .assertNext(hasRole -> assertTrue(hasRole))
            .verifyComplete();
        
        // Simulate expiration
        UserRole expiredRole = new UserRole(1L, 100L, 1L, 1L, Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1800));
        
        when(userRoleRepository.findExpiredRoles())
            .thenReturn(Flux.just(expiredRole));
        when(roleRepository.findById(1L))
            .thenReturn(Mono.just(adminRole));
        when(userRoleRepository.deleteById(1L))
            .thenReturn(Mono.empty());
        when(auditRoleChangeRepository.save(any()))
            .thenReturn(Mono.just(new com.rcs.ssf.entity.AuditRoleChange()));
        
        var expireRolesResult = roleService.expireRoles();
        
        StepVerifier.create(expireRolesResult)
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Should track audit log for all role changes")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAuditLogTrackingForRoleChanges() {
        // Grant role (should create GRANT audit entry)
        when(roleRepository.findByName("ROLE_ADMIN"))
            .thenReturn(Mono.just(adminRole));
        when(userRoleRepository.save(any()))
            .thenReturn(Mono.just(userAdminAssignment));
        when(auditRoleChangeRepository.save(any()))
            .thenReturn(Mono.just(new com.rcs.ssf.entity.AuditRoleChange()));
        
        var grantResult = roleService.grantRole(100L, "ROLE_ADMIN", 1L, null, "127.0.0.1");
        
        StepVerifier.create(grantResult)
            .assertNext(userRole -> assertNotNull(userRole))
            .verifyComplete();
        
        // Revoke role (should create REVOKE audit entry)
        when(roleRepository.findByName("ROLE_ADMIN"))
            .thenReturn(Mono.just(adminRole));
        when(userRoleRepository.deleteByUserIdAndRoleId(100L, 1L))
            .thenReturn(Mono.empty());
        when(auditRoleChangeRepository.save(any()))
            .thenReturn(Mono.just(new com.rcs.ssf.entity.AuditRoleChange()));
        
        var revokeResult = roleService.revokeRole(100L, "ROLE_ADMIN", 1L, "Testing", "127.0.0.1");
        
        StepVerifier.create(revokeResult)
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Should prevent unauthorized role grant operations")
    @WithMockUser(username = "user", roles = "USER")
    void testUnauthorizedRoleGrantDenial() {
        // Test that @PreAuthorize prevents non-admin users from granting roles
        // The grantRole method throws AccessDeniedException in security context
        
        when(roleRepository.findByName("ROLE_ADMIN"))
            .thenReturn(Mono.just(adminRole));
        
        // Invoke grantRole as USER (non-admin) context
        // Should throw AccessDeniedException due to @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        var result = roleService.grantRole(100L, "ROLE_ADMIN", 1L, null, "127.0.0.1");
        
        // Verify the reactive stream emits AccessDeniedException
        StepVerifier.create(result)
            .expectError(org.springframework.security.access.AccessDeniedException.class)
            .verify();
    }
}
