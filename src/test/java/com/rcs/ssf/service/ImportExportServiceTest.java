package com.rcs.ssf.service;

import com.rcs.ssf.dto.*;
import com.rcs.ssf.dto.BulkCrudResponse.Status;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("ImportExportService Tests")
public class ImportExportServiceTest {

    @Mock
    private BulkCrudService bulkCrudService;

    @Mock
    private DynamicCrudService dynamicCrudService;

    private ImportExportService importExportService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        importExportService = new ImportExportService(bulkCrudService, dynamicCrudService, "ROLE_ADMIN", "audit_login_attempts,audit_sessions,audit_dynamic_crud,audit_error_log");
        setupSecurityContext();
    }

    private void setupSecurityContext() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        Authentication auth = new UsernamePasswordAuthenticationToken("testuser", "password", authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Should parse CSV data correctly")
    public void testCSVParsing() {
        // Given
        ImportRequest request = new ImportRequest();
        request.setTableName("audit_login_attempts");
        request.setFormat(ImportRequest.ImportFormat.CSV);
        request.setOperation(DynamicCrudRequest.Operation.INSERT);
        request.setData("user_id,status\n1,success\n2,failed");
        request.setDryRun(true);

        BulkCrudResponse mockResponse = new BulkCrudResponse(2, 0, 0, 0, Status.DRY_RUN_PREVIEW, List.of(), 0L);
        when(bulkCrudService.executeBulkOperation(any())).thenReturn(mockResponse);

        // When
        BulkCrudResponse response = importExportService.importData(request);

        // Then
        assertNotNull(response);
        assertEquals(Status.DRY_RUN_PREVIEW, response.getStatus());
    }

    @Test
    @DisplayName("Should parse JSON array data correctly")
    public void testJSONParsing() {
        // Given
        ImportRequest request = new ImportRequest();
        request.setTableName("audit_login_attempts");
        request.setFormat(ImportRequest.ImportFormat.JSON);
        request.setOperation(DynamicCrudRequest.Operation.INSERT);
        request.setData("[{\"user_id\": \"1\", \"status\": \"success\"}, {\"user_id\": \"2\", \"status\": \"failed\"}]");
        request.setDryRun(true);

        BulkCrudResponse mockResponse = new BulkCrudResponse(2, 0, 0, 0, Status.DRY_RUN_PREVIEW, List.of(), 0L);
        when(bulkCrudService.executeBulkOperation(any())).thenReturn(mockResponse);

        // When
        BulkCrudResponse response = importExportService.importData(request);

        // Then
        assertNotNull(response);
        assertEquals(Status.DRY_RUN_PREVIEW, response.getStatus());
    }

    @Test
    @DisplayName("Should validate import request")
    public void testImportValidation() {
        // Given
        ImportRequest request = new ImportRequest();
        request.setTableName("invalid_table");

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            importExportService.importData(request);
        });
    }

    @Test
    @DisplayName("Should apply column mapping during import")
    public void testColumnMapping() {
        // Given
        ImportRequest request = new ImportRequest();
        request.setTableName("audit_login_attempts");
        request.setFormat(ImportRequest.ImportFormat.CSV);
        request.setOperation(DynamicCrudRequest.Operation.INSERT);
        request.setData("col1,col2\nvalue1,value2");
        request.setColumnMapping(Map.of("col1", "user_id", "col2", "status"));
        request.setDryRun(true);

        BulkCrudResponse mockResponse = new BulkCrudResponse(1, 0, 0, 0, Status.DRY_RUN_PREVIEW, List.of(), 0L);
        when(bulkCrudService.executeBulkOperation(any())).thenReturn(mockResponse);

        // When
        BulkCrudResponse response = importExportService.importData(request);

        // Then
        assertNotNull(response);
    }

    @Test
    @DisplayName("Should export data in CSV format")
    public void testCSVExport() {
        // Given
        ExportRequest request = new ExportRequest();
        request.setTableName("audit_login_attempts");
        request.setFormat(ExportRequest.ExportFormat.CSV);
        request.setIncludeHeaders(true);

        List<Map<String, Object>> mockRows = List.of(
                Map.of("id", "1", "user_id", "user1", "status", "success"),
                Map.of("id", "2", "user_id", "user2", "status", "failed")
        );
        DynamicCrudResponseDto mockResponse = new DynamicCrudResponseDto(
                mockRows,
                2,
                List.of(
                        new DynamicCrudResponseDto.ColumnMeta("id", "NUMBER", false, true, 10, null, null, null, false, null, null, null),
                        new DynamicCrudResponseDto.ColumnMeta("user_id", "VARCHAR2", false, false, 50, null, null, null, false, null, null, null),
                        new DynamicCrudResponseDto.ColumnMeta("status", "VARCHAR2", false, false, 20, null, null, null, false, null, null, null)
                ),
                true
        );
        when(dynamicCrudService.executeSelect(any())).thenReturn(mockResponse);

        // When
        ImportExportService.ExportResult result = importExportService.exportData(request);

        // Then
        assertNotNull(result);
        assertNotNull(result.getData());
        assertTrue(result.getData().contains("id,user_id,status"));
    }

    @Test
    @DisplayName("Should export data in JSON format")
    public void testJSONExport() {
        // Given
        ExportRequest request = new ExportRequest();
        request.setTableName("audit_login_attempts");
        request.setFormat(ExportRequest.ExportFormat.JSON);

        List<Map<String, Object>> mockRows = List.of(
                Map.of("id", "1", "status", "success")
        );
        DynamicCrudResponseDto mockResponse = new DynamicCrudResponseDto(
                mockRows,
                1,
                List.of(
                        new DynamicCrudResponseDto.ColumnMeta("id", "NUMBER", false, true, 10, null, null, null, false, null, null, null),
                        new DynamicCrudResponseDto.ColumnMeta("status", "VARCHAR2", false, false, 20, null, null, null, false, null, null, null)
                ),
                true
        );
        when(dynamicCrudService.executeSelect(any())).thenReturn(mockResponse);

        // When
        ImportExportService.ExportResult result = importExportService.exportData(request);

        // Then
        assertNotNull(result);
        assertNotNull(result.getData());
        assertTrue(result.getData().contains("["));
    }

    @Test
    @DisplayName("Should export data in JSONL format")
    public void testJSONLExport() {
        // Given
        ExportRequest request = new ExportRequest();
        request.setTableName("audit_login_attempts");
        request.setFormat(ExportRequest.ExportFormat.JSONL);

        List<Map<String, Object>> mockRows = List.of(
                Map.of("id", "1", "status", "success"),
                Map.of("id", "2", "status", "failed")
        );
        DynamicCrudResponseDto mockResponse = new DynamicCrudResponseDto(
                mockRows,
                2,
                List.of(
                        new DynamicCrudResponseDto.ColumnMeta("id", "NUMBER", false, true, 10, null, null, null, false, null, null, null),
                        new DynamicCrudResponseDto.ColumnMeta("status", "VARCHAR2", false, false, 20, null, null, null, false, null, null, null)
                ),
                true
        );
        when(dynamicCrudService.executeSelect(any())).thenReturn(mockResponse);

        // When
        ImportExportService.ExportResult result = importExportService.exportData(request);

        // Then
        assertNotNull(result);
        assertNotNull(result.getData());
        assertTrue(result.getData().contains("\n"));
    }

    @Test
    @DisplayName("Should handle CSV with quoted fields")
    public void testCSVWithQuotedFields() {
        // Given
        ImportRequest request = new ImportRequest();
        request.setTableName("audit_login_attempts");
        request.setFormat(ImportRequest.ImportFormat.CSV);
        request.setOperation(DynamicCrudRequest.Operation.INSERT);
        request.setData("user_id,message\n1,\"Contains, comma\"\n2,\"Contains \"\" quote\"");
        request.setDryRun(true);

        BulkCrudResponse mockResponse = new BulkCrudResponse(2, 0, 0, 0, Status.DRY_RUN_PREVIEW, List.of(), 0L);
        when(bulkCrudService.executeBulkOperation(any())).thenReturn(mockResponse);

        // When
        BulkCrudResponse response = importExportService.importData(request);

        // Then
        assertNotNull(response);
    }

    @Test
    @DisplayName("Should validate export request")
    public void testExportValidation() {
        // Given
        ExportRequest request = new ExportRequest();
        request.setTableName("invalid_table");

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            importExportService.exportData(request);
        });
    }

    @Test
    @DisplayName("Should handle column filtering in export")
    public void testColumnFilteringInExport() {
        // Given
        ExportRequest request = new ExportRequest();
        request.setTableName("audit_login_attempts");
        request.setFormat(ExportRequest.ExportFormat.CSV);
        request.setColumns(List.of("id", "status")); // Only these columns

        List<Map<String, Object>> mockRows = List.of(
                Map.of("id", "1", "user_id", "user1", "status", "success")
        );
        DynamicCrudResponseDto mockResponse = new DynamicCrudResponseDto(
                mockRows,
                1,
                List.of(
                        new DynamicCrudResponseDto.ColumnMeta("id", "NUMBER", false, true, 10, null, null, null, false, null, null, null),
                        new DynamicCrudResponseDto.ColumnMeta("status", "VARCHAR2", false, false, 20, null, null, null, false, null, null, null)
                ),
                true
        );
        when(dynamicCrudService.executeSelect(any())).thenReturn(mockResponse);

        // When
        ImportExportService.ExportResult result = importExportService.exportData(request);

        // Then
        assertNotNull(result);
        // user_id should not be in the export
        assertFalse(result.getData().contains("user_id"));
    }
}
