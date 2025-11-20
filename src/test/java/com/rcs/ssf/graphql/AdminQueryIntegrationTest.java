package com.rcs.ssf.graphql;

import com.rcs.ssf.dto.RoleDto;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * GraphQL Integration Tests for AdminQuery resolver
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
@DisplayName("AdminQuery GraphQL Integration Tests")
class AdminQueryIntegrationTest {

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
    @DisplayName("Should query own roles when authenticated")
    @WithMockUser(username = "testuser", roles = "USER")
    void testUserRolesQueryOwnRoles() {
        // Given
        when(userRoleRepository.findActiveRolesByUserId(1L))
            .thenReturn(Flux.just(testUserRole));
        when(roleRepository.findById(1L))
            .thenReturn(Mono.just(testRole));
        
        // When/Then
        graphQlTester.document("""
                query {
                    userRoles(userId: 1) {
                        id
                        name
                        description
                    }
                }
                """)
            .execute()
            .path("data.userRoles[0]")
            .entity(RoleDto.class)
            .satisfies(role -> {
                assertEquals("ROLE_ADMIN", role.getName(), "Role name should be ROLE_ADMIN");
                assertEquals("Admin Role", role.getDescription(), "Role description should be Admin Role");
            });
    }
    
    @Test
    @DisplayName("Should prevent non-admin from querying other user's roles")
    @WithMockUser(username = "testuser", roles = "USER")
    void testUserRolesQueryOtherUserDenied() {
        // When/Then
        graphQlTester.document("""
                query {
                    userRoles(userId: 99) {
                        id
                        name
                    }
                }
                """)
            .execute()
            .errors()
            .satisfy(errors -> {
                assertFalse(errors.isEmpty(), "Expected GraphQL errors for unauthorized user");
                assertTrue(errors.stream()
                    .anyMatch(err -> err.getMessage() != null && 
                                   (err.getMessage().contains("security") || 
                                    err.getMessage().contains("authorized"))), 
                    "Expected security or authorization error message");
            });
    }
    
    @Test
    @DisplayName("Should allow admin to query available roles")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAvailableRolesQueryAdminAccess() {
        // Given
        when(roleRepository.findAll())
            .thenReturn(Flux.just(testRole));
        
        // When/Then
        graphQlTester.document("""
                query {
                    availableRoles {
                        id
                        name
                        description
                    }
                }
                """)
            .execute()
            .path("data.availableRoles[0]")
            .entity(RoleDto.class)
            .satisfies(role -> {
                assertEquals(1L, role.getId(), "Role ID should be 1");
                assertEquals("ROLE_ADMIN", role.getName(), "Role name should be ROLE_ADMIN");
            });
    }
    
    @Test
    @DisplayName("Should deny user access to available roles")
    @WithMockUser(username = "testuser", roles = "USER")
    void testAvailableRolesQueryUserDenied() {
        // When/Then
        graphQlTester.document("""
                query {
                    availableRoles {
                        id
                        name
                    }
                }
                """)
            .execute()
            .errors()
            .satisfy(errors -> {
                assertFalse(errors.isEmpty(), "Expected GraphQL errors for unauthorized user");
                assertTrue(errors.stream()
                    .anyMatch(err -> err.getMessage() != null && 
                                   (err.getMessage().contains("security") || 
                                    err.getMessage().contains("authorized"))), 
                    "Expected security or authorization error message");
            });
    }
    
    @Test
    @DisplayName("Should query role audit log with pagination")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRoleAuditLogQueryWithPagination() {
        // Given
        when(auditRoleChangeRepository.findByUserId(anyLong(), 50, 0))
            .thenReturn(Flux.empty());
        
        // When/Then
        graphQlTester.document("""
                query {
                    roleAuditLog(userId: 1, limit: 50, offset: 0) {
                        id
                        userId
                        roleName
                        action
                    }
                }
                """)
            .execute()
            .path("data.roleAuditLog")
            .entityList(Object.class)
            .satisfies(auditLog -> {
                assertNotNull(auditLog, "Role audit log should not be null");
            });
    }
    
    @Test
    @DisplayName("Should use default pagination values when not specified")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRoleAuditLogQueryDefaultPagination() {
        // Given
        when(auditRoleChangeRepository.findByUserId(anyLong(), 50, 0))
            .thenReturn(Flux.empty());
        
        // When/Then
        graphQlTester.document("""
                query {
                    roleAuditLog(userId: 1) {
                        id
                        userId
                    }
                }
                """)
            .execute()
            .path("data.roleAuditLog")
            .entityList(Object.class)
            .satisfies(auditLog -> {
                assertNotNull(auditLog, "Role audit log should not be null");
            });
    }
    
    @Test
    @DisplayName("Should deny user access to audit log")
    @WithMockUser(username = "testuser", roles = "USER")
    void testRoleAuditLogQueryUserDenied() {
        // When/Then
        graphQlTester.document("""
                query {
                    roleAuditLog(userId: 1) {
                        id
                        roleName
                    }
                }
                """)
            .execute()
            .errors()
            .satisfy(errors -> {
                assertFalse(errors.isEmpty(), "Expected GraphQL errors for unauthorized user");
            });
    }
}
