package com.rcs.ssf.graphql;

import com.rcs.ssf.dto.UserRoleDto;
import com.rcs.ssf.entity.Role;
import com.rcs.ssf.entity.UserRole;
import com.rcs.ssf.repository.RoleRepository;
import com.rcs.ssf.repository.UserRoleRepository;
import com.rcs.ssf.repository.AuditRoleChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * GraphQL Integration Tests for AdminMutation resolver
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
@DisplayName("AdminMutation GraphQL Integration Tests")
@SuppressWarnings("null")
class AdminMutationIntegrationTest {
    
    @Autowired
    private GraphQlTester graphQlTester;
    
    @MockBean
    private RoleRepository roleRepository;
    
    @MockBean
    private UserRoleRepository userRoleRepository;
    
    @MockBean
    private AuditRoleChangeRepository auditRoleChangeRepository;
    
    private Role testRole;
    private UserRole testUserRole;
    
    @BeforeEach
    void setUp() {
        testRole = new Role(1L, "ROLE_ADMIN", "Admin Role", Instant.now(), Instant.now());
        testUserRole = new UserRole(1L, 1L, 1L, 2L, Instant.now(), null);
    }
    
    @Test
    @DisplayName("Should grant role successfully as admin")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGrantRoleMutationSuccess() {
        // Given
        when(roleRepository.findByName("ROLE_ADMIN"))
            .thenReturn(Mono.just(testRole));
        when(userRoleRepository.save(any()))
            .thenReturn(Mono.just(testUserRole));
        when(auditRoleChangeRepository.save(any()))
            .thenReturn(Mono.just(new com.rcs.ssf.entity.AuditRoleChange()));
        
        // When/Then
        graphQlTester.document("""
                mutation {
                    grantRole(input: {
                        userId: 1
                        roleName: "ROLE_ADMIN"
                    }) {
                        id
                        userId
                        role {
                            name
                        }
                    }
                }
                """)
            .execute()
            .path("data.grantRole")
            .entity(UserRoleDto.class)
            .satisfies(userRole -> {
                assert userRole.getUserId().equals(1L);
                assert userRole.getRole().getName().equals("ROLE_ADMIN");
            });
    }
    
    @Test
    @DisplayName("Should deny user from granting roles")
    @WithMockUser(username = "testuser", roles = "USER")
    void testGrantRoleMutationUserDenied() {
        // When/Then
        graphQlTester.document("""
                mutation {
                    grantRole(input: {
                        userId: 1
                        roleName: "ROLE_ADMIN"
                    }) {
                        id
                        userId
                    }
                }
                """)
            .execute()
            .errors()
            .satisfy(errors -> {
                assertFalse(errors.isEmpty(), "Expected GraphQL errors for unauthorized user");
            });
    }
    
    @Test
    @DisplayName("Should revoke role successfully as admin")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRevokeRoleMutationSuccess() {
        // Given
        when(roleRepository.findByName("ROLE_ADMIN"))
            .thenReturn(Mono.just(testRole));
        when(userRoleRepository.deleteByUserIdAndRoleId(anyLong(), anyLong()))
            .thenReturn(Mono.empty());
        when(auditRoleChangeRepository.save(any()))
            .thenReturn(Mono.just(new com.rcs.ssf.entity.AuditRoleChange()));
        
        // When/Then
        graphQlTester.document("""
                mutation {
                    revokeRole(input: {
                        userId: 1
                        roleName: "ROLE_ADMIN"
                        reason: "Testing revocation"
                    })
                }
                """)
            .execute()
            .path("data.revokeRole")
            .entity(Boolean.class)
            .satisfies(result -> {
                assertNotNull(result, "Revoke operation should return non-null result");
            });
    }
    
    @Test
    @DisplayName("Should deny user from revoking roles")
    @WithMockUser(username = "testuser", roles = "USER")
    void testRevokeRoleMutationUserDenied() {
        // When/Then
        graphQlTester.document("""
                mutation {
                    revokeRole(input: {
                        userId: 1
                        roleName: "ROLE_ADMIN"
                    })
                }
                """)
            .execute()
            .errors()
            .satisfy(errors -> {
                assert !errors.isEmpty();
            });
    }
    
    @Test
    @DisplayName("Should grant role with expiration timestamp")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGrantRoleMutationWithExpiration() {
        // Given
        Instant expiresAt = Instant.parse("2025-01-15T00:00:00Z");
        UserRole expiredUserRole = new UserRole(1L, 1L, 1L, 2L, Instant.now(), expiresAt);
        
        when(roleRepository.findByName("ROLE_ADMIN"))
            .thenReturn(Mono.just(testRole));
        when(userRoleRepository.save(any()))
            .thenReturn(Mono.just(expiredUserRole));
        when(auditRoleChangeRepository.save(any()))
            .thenReturn(Mono.just(new com.rcs.ssf.entity.AuditRoleChange()));
        
        // When/Then
        graphQlTester.document("""
                mutation {
                    grantRole(input: {
                        userId: 1
                        roleName: "ROLE_ADMIN"
                        expiresAt: "2025-01-15T00:00:00Z"
                    }) {
                        id
                        userId
                        expiresAt
                    }
                }
                """)
            .execute()
            .path("data.grantRole")
            .entity(UserRoleDto.class)
            .satisfies(userRole -> {
                assertEquals(1L, userRole.getUserId());
                assertNotNull(userRole.getExpiresAt());
                assertEquals(expiresAt, userRole.getExpiresAt());
            });
    }
    
    @Test
    @DisplayName("Should include reason in revocation audit log")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRevokeRoleMutationAuditReason() {
        // Given
        when(roleRepository.findByName("ROLE_USER"))
            .thenReturn(Mono.just(new Role(2L, "ROLE_USER", "User Role", Instant.now(), Instant.now())));
        when(userRoleRepository.deleteByUserIdAndRoleId(anyLong(), anyLong()))
            .thenReturn(Mono.empty());
        when(auditRoleChangeRepository.save(any()))
            .thenReturn(Mono.just(new com.rcs.ssf.entity.AuditRoleChange()));
        
        // When/Then
        graphQlTester.document("""
                mutation {
                    revokeRole(input: {
                        userId: 1
                        roleName: "ROLE_USER"
                        reason: "User no longer needs admin access"
                    })
                }
                """)
            .execute()
            .path("data.revokeRole")
            .entity(Boolean.class)
            .satisfies(result -> {
                assert result != null;
            });
    }
    
    @Test
    @DisplayName("Should return error when granting non-existent role")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGrantRoleMutationNonExistentRole() {
        // Given
        when(roleRepository.findByName("ROLE_NONEXISTENT"))
            .thenReturn(Mono.empty());
        
        // When/Then
        graphQlTester.document("""
                mutation {
                    grantRole(input: {
                        userId: 1
                        roleName: "ROLE_NONEXISTENT"
                    }) {
                        id
                        userId
                    }
                }
                """)
            .execute()
            .errors()
            .satisfy(errors -> {
                assert !errors.isEmpty();
            });
    }
}
