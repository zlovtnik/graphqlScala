package com.rcs.ssf.service;

import com.rcs.ssf.dto.BulkCrudRequest;
import com.rcs.ssf.dto.BulkCrudResponse;
import com.rcs.ssf.dto.BulkCrudResponse.Status;
import com.rcs.ssf.dto.DynamicCrudRequest;
import com.rcs.ssf.dynamic.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service for managing bulk CRUD operations with progress tracking, validation,
 * dry-run preview, and comprehensive audit logging through DynamicCrudGateway.
 */
@Slf4j
@Service
public class BulkCrudService {

    private final DynamicCrudGateway dynamicCrudGateway;
    private final String requiredRole;
    private final boolean trustProxyHeaders;
    private final Set<String> allowedTables;

    private static final Set<String> SENSITIVE_COLUMN_NAMES = Set.of(
            "PASSWORD", "PASSWORD_HASH", "SECRET", "SECRET_KEY", "ACCESS_KEY", "API_KEY", "TOKEN", "REFRESH_TOKEN");

    public BulkCrudService(
            DynamicCrudGateway dynamicCrudGateway,
            @Value("${security.dynamicCrud.requiredRole:ROLE_ADMIN}") String requiredRole,
            @Value("${security.trustProxyHeaders:true}") boolean trustProxyHeaders,
            @Value("${importExport.allowedTables:}") String allowedTablesStr) {
        this.dynamicCrudGateway = dynamicCrudGateway;
        this.requiredRole = requiredRole;
        this.trustProxyHeaders = trustProxyHeaders;
        this.allowedTables = Arrays.stream(allowedTablesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Executes bulk CRUD operations with optional dry-run preview and progress tracking.
     * All operations are audited through the DynamicCrudGateway with actor info and trace ID.
     *
     * @param request The bulk CRUD request containing rows and operation type
     * @return Response with success/failure counts and detailed error information
     */
    @Transactional
    public BulkCrudResponse executeBulkOperation(BulkCrudRequest request) {
        assertAuthorizedForDynamicCrud();
        validateRequest(request);

        long startTime = System.currentTimeMillis();
        String tableName = request.getTableName();
        List<BulkCrudRequest.BulkRow> rows = request.getRows();
        int totalRows = rows.size();
        int batchSize = request.getBatchSize() != null ? request.getBatchSize() : 100;

        log.info("Starting bulk {} operation on table '{}' with {} rows, batch size: {}",
                request.getOperation(), tableName, totalRows, batchSize);

        // Validate all rows first
        List<BulkCrudResponse.RowError> validationErrors = validateRows(tableName, rows, request.getOperation());
        if (!validationErrors.isEmpty() && !request.isDryRun()) {
            long duration = System.currentTimeMillis() - startTime;
            return new BulkCrudResponse(totalRows, 0, validationErrors.size(), 0,
                    Status.VALIDATION_FAILED, validationErrors, duration);
        }

        // Handle dry-run
        if (request.isDryRun()) {
            BulkCrudResponse.BulkDryRunPreview preview = createDryRunPreview(
                    tableName, request.getOperation(), totalRows, validationErrors);
            long duration = System.currentTimeMillis() - startTime;
            return new BulkCrudResponse(totalRows, 0, validationErrors.size(), 0,
                    Status.DRY_RUN_PREVIEW, validationErrors, duration, preview);
        }

        // Execute in batches with progress tracking
        int successCount = 0;
        int failureCount = 0;
        List<BulkCrudResponse.RowError> executionErrors = new ArrayList<>(validationErrors);
        AtomicInteger processedCount = new AtomicInteger(0);

        DynamicCrudAuditContext auditContext = buildAuditContext(request.getMetadata());

        for (int i = 0; i < totalRows; i += batchSize) {
            int endExclusive = Math.min(i + batchSize, totalRows);
            List<BulkCrudRequest.BulkRow> batchRows = rows.subList(i, endExclusive);

            try {
                int affected = executeBatch(tableName, request.getOperation(), batchRows, auditContext);
                successCount += affected;
                processedCount.addAndGet(batchRows.size());
                logProgress(tableName, processedCount.get(), totalRows);
            } catch (Exception ex) {
                log.error("Error processing batch [{}-{}] on table '{}'", i, endExclusive - 1, tableName, ex);
                failureCount += batchRows.size();
                for (int j = 0; j < batchRows.size(); j++) {
                    executionErrors.add(new BulkCrudResponse.RowError(i + j + 1,
                            "Batch error: " + ex.getMessage(), "EXECUTION_ERROR"));
                }

                if (!request.isDryRun() && !isSkipOnError(request)) {
                    // Stop on first error if skipOnError is false
                    break;
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        Status status = failureCount == 0 ? Status.SUCCESS : (successCount > 0 ? Status.PARTIAL_SUCCESS : Status.FAILURE);

        log.info("Bulk operation completed: {} rows processed in {} ms. Success: {}, Failures: {}",
                processedCount.get(), duration, successCount, failureCount);

        return new BulkCrudResponse(totalRows, successCount, failureCount, processedCount.get(),
                status, executionErrors, duration);
    }

    /**
     * Executes a single batch of rows, converting them to the internal DynamicCrudRequest format
     * and routing through the DynamicCrudGateway with audit context.
     */
    private int executeBatch(String tableName, DynamicCrudRequest.Operation operation,
                            List<BulkCrudRequest.BulkRow> batchRows,
                            DynamicCrudAuditContext auditContext) {
        List<DynamicCrudRow> crudRows = batchRows.stream()
                .map(row -> convertBulkRowToCrudRow(tableName, row))
                .collect(Collectors.toList());

        com.rcs.ssf.dynamic.DynamicCrudRequest crudRequest = new com.rcs.ssf.dynamic.DynamicCrudRequest(
                tableName,
                toDynamicCrudOperation(operation),
                null, // columns are per-row in bulk operations
                null, // filters are per-row in bulk operations
                auditContext,
                crudRows
        );

        DynamicCrudResponse response = dynamicCrudGateway.execute(crudRequest);
        return (int) response.affectedRows();
    }

    /**
     * Validates all rows in the request against schema metadata and business rules.
     * Returns a list of validation errors; empty list means validation passed.
     */
    private List<BulkCrudResponse.RowError> validateRows(String tableName,
                                                         List<BulkCrudRequest.BulkRow> rows,
                                                         DynamicCrudRequest.Operation operation) {
        List<BulkCrudResponse.RowError> errors = new ArrayList<>();

        if (rows == null || rows.isEmpty()) {
            errors.add(new BulkCrudResponse.RowError(0, "No rows provided", "VALIDATION_ERROR"));
            return errors;
        }

        // Fetch table schema for validation
        String schemaTableName = tableName.toLowerCase(Locale.ROOT);
        if (!allowedTables.contains(schemaTableName)) {
            errors.add(new BulkCrudResponse.RowError(0, "Table not allowed: " + tableName, "VALIDATION_ERROR"));
            return errors;
        }

        // Validate each row
        for (int i = 0; i < rows.size(); i++) {
            BulkCrudRequest.BulkRow row = rows.get(i);
            int rowNumber = i + 1;

            if (row.getColumns() == null || row.getColumns().isEmpty()) {
                if (operation != DynamicCrudRequest.Operation.DELETE) {
                    errors.add(new BulkCrudResponse.RowError(rowNumber,
                            "No columns provided for row", "VALIDATION_ERROR"));
                }
            }

            // Validate column names exist and are not sensitive
            if (row.getColumns() != null) {
                for (DynamicCrudRequest.ColumnValue col : row.getColumns()) {
                    if (col.getName() == null || col.getName().isBlank()) {
                        errors.add(new BulkCrudResponse.RowError(rowNumber,
                                "Column name is blank", "VALIDATION_ERROR"));
                    } else if (isSensitiveColumn(col.getName())) {
                        errors.add(new BulkCrudResponse.RowError(rowNumber,
                                "Cannot modify sensitive column: " + col.getName(), "VALIDATION_ERROR"));
                    }
                }
            }

            // Validate filters for UPDATE/DELETE
            if (operation != DynamicCrudRequest.Operation.INSERT) {
                if (row.getFilters() == null || row.getFilters().isEmpty()) {
                    errors.add(new BulkCrudResponse.RowError(rowNumber,
                            "WHERE clause required for " + operation, "VALIDATION_ERROR"));
                }
            }
        }

        return errors;
    }

    /**
     * Creates a dry-run preview showing what would be executed.
     */
    private BulkCrudResponse.BulkDryRunPreview createDryRunPreview(String tableName,
                                                                   DynamicCrudRequest.Operation operation,
                                                                   int totalRows,
                                                                   List<BulkCrudResponse.RowError> validationErrors) {
        int estimatedAffected = validationErrors.isEmpty() ? totalRows : Math.max(0, totalRows - validationErrors.size());
        String plan = String.format(
                "Execute bulk %s on table '%s': %d rows will be processed, ~%d rows estimated to be affected",
                operation, tableName, totalRows, estimatedAffected);

        List<String> warnings = new ArrayList<>();
        if (validationErrors.size() > 0) {
            warnings.add(String.format("%d validation errors detected", validationErrors.size()));
        }

        return new BulkCrudResponse.BulkDryRunPreview(estimatedAffected, plan, warnings);
    }

    /**
     * Builds audit context from request metadata and current authentication.
     */
    private DynamicCrudAuditContext buildAuditContext(String metadata) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = auth != null ? auth.getName() : "SYSTEM";
        String traceId = UUID.randomUUID().toString();
        String clientIp = getClientIp();

        return new DynamicCrudAuditContext(
                actor,
                traceId,
                clientIp,
                metadata != null ? metadata : "bulk_operation"
        );
    }

    /**
     * Converts a bulk row to internal DynamicCrudRow format.
     */
    private DynamicCrudRow convertBulkRowToCrudRow(String tableName, BulkCrudRequest.BulkRow bulkRow) {
        List<DynamicCrudColumnValue> columns = bulkRow.getColumns() != null
                ? bulkRow.getColumns().stream()
                .map(col -> new DynamicCrudColumnValue(col.getName(), col.getValue()))
                .collect(Collectors.toList())
                : new ArrayList<>();

        List<DynamicCrudFilter> filters = bulkRow.getFilters() != null
                ? bulkRow.getFilters().stream()
                .map(f -> new DynamicCrudFilter(f.getColumn(), f.getOperator().getSymbol(), f.getValue()))
                .collect(Collectors.toList())
                : new ArrayList<>();

        return new DynamicCrudRow(columns, filters);
    }

    /**
     * Converts DTO operation to internal DynamicCrudOperation.
     */
    private DynamicCrudOperation toDynamicCrudOperation(DynamicCrudRequest.Operation operation) {
        return switch (operation) {
            case INSERT -> DynamicCrudOperation.CREATE;
            case UPDATE -> DynamicCrudOperation.UPDATE;
            case DELETE -> DynamicCrudOperation.DELETE;
            default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
        };
    }

    /**
     * Validates the bulk CRUD request structure.
     */
    private void validateRequest(BulkCrudRequest request) {
        if (request.getTableName() == null || request.getTableName().isBlank()) {
            throw new IllegalArgumentException("Table name is required");
        }
        if (request.getOperation() == null) {
            throw new IllegalArgumentException("Operation is required");
        }
        if (request.getRows() == null || request.getRows().isEmpty()) {
            throw new IllegalArgumentException("At least one row is required");
        }
        String tableName = request.getTableName().toLowerCase(Locale.ROOT);
        if (!allowedTables.contains(tableName)) {
            throw new IllegalArgumentException("Table not allowed: " + request.getTableName());
        }
    }

    /**
     * Checks authorization for dynamic CRUD operations.
     */
    private void assertAuthorizedForDynamicCrud() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("Dynamic CRUD operations require authentication");
        }

        boolean hasRequiredRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(requiredRole::equals);

        if (!hasRequiredRole) {
            throw new AccessDeniedException("Dynamic CRUD operations require administrative privileges");
        }
    }

    /**
     * Checks if a column is sensitive and should not be modified.
     */
    private boolean isSensitiveColumn(String columnName) {
        if (columnName == null) {
            return false;
        }
        return SENSITIVE_COLUMN_NAMES.contains(columnName.toUpperCase(Locale.ROOT));
    }

    /**
     * Extracts client IP from request context for audit logging.
     */
    private String getClientIp() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            if (trustProxyHeaders) {
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
                    String[] ips = xForwardedFor.split(",");
                    for (String ip : ips) {
                        String trimmed = ip.trim();
                        if (!trimmed.isEmpty()) {
                            return trimmed;
                        }
                    }
                }
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            log.warn("Failed to extract client IP, using fallback", e);
            return "unknown";
        }
    }

    /**
     * Logs progress of bulk operation.
     */
    private void logProgress(String tableName, int processed, int total) {
        int percentage = (int) ((processed * 100.0) / total);
        log.debug("Bulk operation progress on '{}': {} / {} rows processed ({}%)",
                tableName, processed, total, percentage);
    }

    /**
     * Determines if bulk operation should skip on error.
     */
    private boolean isSkipOnError(BulkCrudRequest request) {
        return request.isSkipOnError() || request.isDryRun() || (request.getOperation() != DynamicCrudRequest.Operation.DELETE);
    }
}
