package com.rcs.ssf.service;

import com.rcs.ssf.dto.BulkCrudRequest;
import com.rcs.ssf.dto.BulkCrudResponse;
import com.rcs.ssf.dto.BulkCrudResponse.Status;
import com.rcs.ssf.dto.DynamicCrudRequest;
import com.rcs.ssf.dynamic.DynamicCrudGateway;
import com.rcs.ssf.dynamic.DynamicCrudResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("BulkCrudService Tests")
public class BulkCrudServiceTest {

    @Mock
    private DynamicCrudGateway dynamicCrudGateway;

    private BulkCrudService bulkCrudService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        bulkCrudService = new BulkCrudService(dynamicCrudGateway, "ROLE_ADMIN", true, "audit_login_attempts,audit_sessions,audit_dynamic_crud,audit_error_log");
        setupSecurityContext();
    }

    private void setupSecurityContext() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        Authentication auth = new UsernamePasswordAuthenticationToken("testuser", "password", authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Should execute bulk insert with multiple rows")
    public void testBulkInsertOperation() {
        // Given
        BulkCrudRequest request = createBulkInsertRequest(5);

        when(dynamicCrudGateway.execute(any())).thenReturn(
                new DynamicCrudResponse(5, "Successfully inserted 5 rows", null)
        );

        // When
        BulkCrudResponse response = bulkCrudService.executeBulkOperation(request);

        // Then
        assertNotNull(response);
        assertEquals(5, response.getTotalRows());
        assertEquals(Status.SUCCESS, response.getStatus());
    }

    @Test
    @DisplayName("Should validate rows before execution")
    public void testRowValidation() {
        // Given
        BulkCrudRequest request = new BulkCrudRequest();
        request.setTableName("audit_login_attempts");
        request.setOperation(DynamicCrudRequest.Operation.INSERT);
        request.setRows(List.of()); // Empty rows

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            bulkCrudService.executeBulkOperation(request);
        });
    }

    @Test
    @DisplayName("Should support dry-run preview without executing")
    public void testDryRunPreview() {
        // Given
        BulkCrudRequest request = createBulkInsertRequest(3);
        request.setDryRun(true);

        when(dynamicCrudGateway.execute(any())).thenReturn(
                new DynamicCrudResponse(3, "Dry run preview", null)
        );

        // When
        BulkCrudResponse response = bulkCrudService.executeBulkOperation(request);

        // Then
        assertNotNull(response);
        assertEquals(Status.DRY_RUN_PREVIEW, response.getStatus());
        assertNotNull(response.getDryRunPreview());
    }

    @Test
    @DisplayName("Should batch rows according to batch size")
    public void testBatchProcessing() {
        // Given
        BulkCrudRequest request = createBulkInsertRequest(250);
        request.setBatchSize(100); // 3 batches: 100, 100, 50

        when(dynamicCrudGateway.execute(any()))
                .thenReturn(new DynamicCrudResponse(100, "Batch processed", null))
                .thenReturn(new DynamicCrudResponse(100, "Batch processed", null))
                .thenReturn(new DynamicCrudResponse(50, "Batch processed", null));

        // When
        BulkCrudResponse response = bulkCrudService.executeBulkOperation(request);

        // Then
        assertNotNull(response);
        assertEquals(250, response.getTotalRows());
        assertEquals(250, response.getSuccessfulRows());
    }

    @Test
    @DisplayName("Should handle update operations with filters")
    public void testBulkUpdateWithFilters() {
        // Given
        BulkCrudRequest request = new BulkCrudRequest();
        request.setTableName("audit_login_attempts");
        request.setOperation(DynamicCrudRequest.Operation.UPDATE);

        BulkCrudRequest.BulkRow row = new BulkCrudRequest.BulkRow();
        DynamicCrudRequest.ColumnValue col = new DynamicCrudRequest.ColumnValue();
        col.setName("status");
        col.setValue("success");
        row.setColumns(List.of(col));

        DynamicCrudRequest.Filter filter = new DynamicCrudRequest.Filter();
        filter.setColumn("id");
        filter.setOperator(DynamicCrudRequest.Operator.EQ);
        filter.setValue("123");
        row.setFilters(List.of(filter));

        request.setRows(List.of(row));

        when(dynamicCrudGateway.execute(any())).thenReturn(
                new DynamicCrudResponse(1, "Row updated", null)
        );

        // When
        BulkCrudResponse response = bulkCrudService.executeBulkOperation(request);

        // Then
        assertNotNull(response);
        assertEquals(Status.SUCCESS, response.getStatus());
    }

    @Test
    @DisplayName("Should reject sensitive column modifications")
    public void testSensitiveColumnValidation() {
        // Given
        BulkCrudRequest request = new BulkCrudRequest();
        request.setTableName("audit_login_attempts");
        request.setOperation(DynamicCrudRequest.Operation.UPDATE);

        BulkCrudRequest.BulkRow row = new BulkCrudRequest.BulkRow();
        DynamicCrudRequest.ColumnValue col = new DynamicCrudRequest.ColumnValue();
        col.setName("PASSWORD"); // Sensitive column
        col.setValue("newpass");
        row.setColumns(List.of(col));
        request.setRows(List.of(row));

        // When
        BulkCrudResponse response = bulkCrudService.executeBulkOperation(request);

        // Then
        assertNotNull(response);
        assertTrue(response.getErrors().size() > 0);
    }

    @Test
    @DisplayName("Should track processing progress")
    public void testProgressTracking() {
        // Given
        BulkCrudRequest request = createBulkInsertRequest(10);
        request.setBatchSize(5);

        when(dynamicCrudGateway.execute(any()))
                .thenReturn(new DynamicCrudResponse(5, "Batch 1", null))
                .thenReturn(new DynamicCrudResponse(5, "Batch 2", null));

        // When
        BulkCrudResponse response = bulkCrudService.executeBulkOperation(request);

        // Then
        assertNotNull(response);
        assertEquals(10, response.getProcessedRows());
        assertTrue(response.getDurationMs() >= 0);
    }

    @Test
    @DisplayName("Should include audit context in operations")
    public void testAuditContextInclusion() {
        // Given
        BulkCrudRequest request = createBulkInsertRequest(1);
        request.setMetadata("test_audit_metadata");

        when(dynamicCrudGateway.execute(any())).thenAnswer(invocation -> {
            com.rcs.ssf.dynamic.DynamicCrudRequest crudRequest = 
                (com.rcs.ssf.dynamic.DynamicCrudRequest) invocation.getArgument(0);
            
            // Verify audit context is present
            assertTrue(crudRequest.optionalAuditContext().isPresent());
            return new DynamicCrudResponse(1, "Audited", null);
        });

        // When
        BulkCrudResponse response = bulkCrudService.executeBulkOperation(request);

        // Then
        assertNotNull(response);
        assertEquals(Status.SUCCESS, response.getStatus());
    }

    private BulkCrudRequest createBulkInsertRequest(int rowCount) {
        BulkCrudRequest request = new BulkCrudRequest();
        request.setTableName("audit_login_attempts");
        request.setOperation(DynamicCrudRequest.Operation.INSERT);

        List<BulkCrudRequest.BulkRow> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            BulkCrudRequest.BulkRow row = new BulkCrudRequest.BulkRow();
            DynamicCrudRequest.ColumnValue col = new DynamicCrudRequest.ColumnValue();
            col.setName("user_id");
            col.setValue(String.valueOf(i + 1));
            row.setColumns(List.of(col));
            rows.add(row);
        }

        request.setRows(rows);
        return request;
    }
}
