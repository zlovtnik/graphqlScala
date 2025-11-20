package com.rcs.ssf.service;

import com.rcs.ssf.entity.AuditRoleChange;
import com.rcs.ssf.entity.Role;
import com.rcs.ssf.entity.UserRole;
import com.rcs.ssf.repository.AuditRoleChangeRepository;
import com.rcs.ssf.repository.RoleRepository;
import com.rcs.ssf.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.test.context.support.WithMockUser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RoleService
 */
@DisplayName("RoleService Tests")
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class RoleServiceTest {
    
    private RoleService roleService;
    
    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private UserRoleRepository userRoleRepository;
    
    @Mock
    private AuditRoleChangeRepository auditRoleChangeRepository;
    
    private Role testRole;
    private UserRole testUserRole;
    
    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleRepository, userRoleRepository, 
                                      auditRoleChangeRepository);
        
        testRole = new Role(1L, "ROLE_ADMIN", "Test Admin Role", Instant.now(), Instant.now());
        testUserRole = new UserRole(1L, 1L, 1L, 2L, Instant.now(), null);
    }
    
    @Test
    @DisplayName("Should grant role to user successfully")
    @WithMockUser(roles = "ADMIN")
    void testGrantRoleSuccess() {
        // Given
        when(roleRepository.findByName("ROLE_ADMIN"))
            .thenReturn(Mono.just(testRole));
        when(userRoleRepository.save(any()))
            .thenReturn(Mono.just(testUserRole));
        when(auditRoleChangeRepository.save(any()))
            .thenReturn(Mono.just(new AuditRoleChange()));
        
        // When
        var result = roleService.grantRole(1L, "ROLE_ADMIN", 2L, null, "127.0.0.1");
        
        // Then
        StepVerifier.create(result)
            .assertNext(userRole -> {
                assertNotNull(userRole);
                assertEquals(1L, userRole.getUserId());
            })
            .verifyComplete();
        
        verify(roleRepository).findByName("ROLE_ADMIN");
        verify(userRoleRepository).save(any());
        verify(auditRoleChangeRepository).save(any());
    }
    
    @Test
    @DisplayName("Should revoke role from user successfully")
    @WithMockUser(roles = "ADMIN")
    void testRevokeRoleSuccess() {
        // Given
        when(roleRepository.findByName("ROLE_ADMIN"))
            .thenReturn(Mono.just(testRole));
        when(userRoleRepository.deleteByUserIdAndRoleId(1L, 1L))
            .thenReturn(Mono.empty());
        when(auditRoleChangeRepository.save(any()))
            .thenReturn(Mono.just(new AuditRoleChange()));
        
        // When
        var result = roleService.revokeRole(1L, "ROLE_ADMIN", 2L, "Testing", "127.0.0.1");
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(roleRepository).findByName("ROLE_ADMIN");
        verify(userRoleRepository).deleteByUserIdAndRoleId(1L, 1L);
        verify(auditRoleChangeRepository).save(any());
    }
    
    @Test
    @DisplayName("Should get user roles successfully")
    void testGetUserRolesSuccess() {
        // Given
        when(userRoleRepository.findActiveRolesByUserId(1L))
            .thenReturn(Flux.just(testUserRole));
        when(roleRepository.findById(1L))
            .thenReturn(Mono.just(testRole));
        
        // When
        var result = roleService.getUserRoles(1L);
        
        // Then
        StepVerifier.create(result)
            .assertNext(roles -> {
                assertNotNull(roles);
                assertFalse(roles.isEmpty());
                assertTrue(roles.contains("ROLE_ADMIN"));
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Should check if user has role")
    void testHasRoleSuccess() {
        // Given
        when(userRoleRepository.userHasRole(1L, "ROLE_ADMIN"))
            .thenReturn(Mono.just(true));
        
        // When
        var result = roleService.hasRole(1L, "ROLE_ADMIN");
        
        // Then
        StepVerifier.create(result)
            .assertNext(hasRole -> assertTrue(hasRole))
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Should get all available roles")
    void testGetAllRolesSuccess() {
        // Given
        when(roleRepository.findAll())
            .thenReturn(Flux.just(testRole));
        
        // When
        var result = roleService.getAllRoles();
        
        // Then
        StepVerifier.create(result)
            .assertNext(role -> {
                assertNotNull(role);
                assertEquals("ROLE_ADMIN", role.getName());
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Should expire roles with past expiration date")
    void testExpireRolesSuccess() {
        // Given
        UserRole expiredRole = new UserRole(2L, 1L, 1L, 2L, Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1800));
        
        when(userRoleRepository.findExpiredRoles())
            .thenReturn(Flux.just(expiredRole));
        when(roleRepository.findById(1L))
            .thenReturn(Mono.just(testRole));
        when(userRoleRepository.deleteById(2L))
            .thenReturn(Mono.empty());
        when(auditRoleChangeRepository.save(any()))
            .thenReturn(Mono.just(new AuditRoleChange()));
        
        // When
        var result = roleService.expireRoles();
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(userRoleRepository).findExpiredRoles();
        verify(roleRepository).findById(1L);
        verify(userRoleRepository).deleteById(2L);
    }
    
    @Test
    @DisplayName("Should return empty set when user has no roles")
    void testGetUserRolesEmpty() {
        // Given
        when(userRoleRepository.findActiveRolesByUserId(99L))
            .thenReturn(Flux.empty());
        
        // When
        var result = roleService.getUserRoles(99L);
        
        // Then
        StepVerifier.create(result)
            .assertNext(roles -> {
                assertNotNull(roles);
                assertTrue(roles.isEmpty());
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Should fail when granting non-existent role")
    @WithMockUser(roles = "ADMIN")
    void testGrantRoleNotFound() {
        // Given
        when(roleRepository.findByName("ROLE_NONEXISTENT"))
            .thenReturn(Mono.empty());
        
        // When
        var result = roleService.grantRole(1L, "ROLE_NONEXISTENT", 2L, null, "127.0.0.1");
        
        // Then
        StepVerifier.create(result)
            .expectErrorMatches(err -> err instanceof IllegalArgumentException)
            .verify();
    }
    
    @Test
    @DisplayName("Should check if user has all roles")
    void testHasAllRolesSuccess() {
        // Given
        when(userRoleRepository.userHasRole(1L, "ROLE_ADMIN"))
            .thenReturn(Mono.just(true));
        when(userRoleRepository.userHasRole(1L, "ROLE_USER"))
            .thenReturn(Mono.just(true));
        
        // When
        var result = roleService.hasAllRoles(1L, java.util.Set.of("ROLE_ADMIN", "ROLE_USER"));
        
        // Then
        StepVerifier.create(result)
            .assertNext(hasAllRoles -> assertTrue(hasAllRoles))
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Should check if user has any of the roles")
    void testHasAnyRoleSuccess() {
        // Given
        when(userRoleRepository.userHasRole(1L, "ROLE_ADMIN"))
            .thenReturn(Mono.just(true));
        when(userRoleRepository.userHasRole(1L, "ROLE_SUPER_ADMIN"))
            .thenReturn(Mono.just(false));
        
        // When
        var result = roleService.hasAnyRole(1L, java.util.Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN"));
        
        // Then
        StepVerifier.create(result)
            .assertNext(hasAnyRole -> assertTrue(hasAnyRole))
            .verifyComplete();
    }
}

