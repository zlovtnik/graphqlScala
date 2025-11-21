package com.rcs.ssf.dto;

import java.util.List;
import java.util.Objects;

/**
 * Response for bulk CRUD operations with progress tracking and detailed error
 * reporting.
 */
public class BulkCrudResponse {
    private final int totalRows;
    private final int successfulRows;
    private final int failedRows;
    private final int processedRows;
    private final Status status;
    private final List<RowError> errors;
    private final long durationMs;
    private BulkDryRunPreview dryRunPreview;

    public enum Status {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILURE,
        VALIDATION_FAILED,
        DRY_RUN_PREVIEW,
        IMPORT_FAILED
    }

    public BulkCrudResponse(int totalRows, int successfulRows, int failedRows,
            int processedRows, Status status, List<RowError> errors,
            long durationMs) {
        validateMetrics(totalRows, successfulRows, failedRows, processedRows, durationMs);
        this.totalRows = totalRows;
        this.successfulRows = successfulRows;
        this.failedRows = failedRows;
        this.processedRows = processedRows;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.durationMs = durationMs;
        this.dryRunPreview = null;
    }

    public BulkCrudResponse(int totalRows, int successfulRows, int failedRows,
            int processedRows, Status status, List<RowError> errors,
            long durationMs, BulkDryRunPreview dryRunPreview) {
        validateMetrics(totalRows, successfulRows, failedRows, processedRows, durationMs);
        this.totalRows = totalRows;
        this.successfulRows = successfulRows;
        this.failedRows = failedRows;
        this.processedRows = processedRows;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.durationMs = durationMs;
        this.dryRunPreview = dryRunPreview;
    }

    private void validateMetrics(int totalRows, int successfulRows, int failedRows,
            int processedRows, long durationMs) {
        if (totalRows < 0 || successfulRows < 0 || failedRows < 0 || processedRows < 0 || durationMs < 0) {
            throw new IllegalArgumentException("BulkCrudResponse metrics must be non-negative values");
        }
        if (processedRows > totalRows) {
            throw new IllegalArgumentException("processedRows must not exceed totalRows");
        }
    }

    // Getters
    public int getTotalRows() {
        return totalRows;
    }

    public int getSuccessfulRows() {
        return successfulRows;
    }

    public int getFailedRows() {
        return failedRows;
    }

    public int getProcessedRows() {
        return processedRows;
    }

    public Status getStatus() {
        return status;
    }

    public List<RowError> getErrors() {
        return errors;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public BulkDryRunPreview getDryRunPreview() {
        return dryRunPreview;
    }

    /**
     * Details about an error in a specific row.
     */
    public static class RowError {
        private final int rowNumber;
        private final String message;
        private final String errorType;

        public RowError(int rowNumber, String message, String errorType) {
            if (rowNumber < 0) {
                throw new IllegalArgumentException("rowNumber must be non-negative");
            }
            this.message = requireNonBlank(message, "message must not be blank");
            this.errorType = requireNonBlank(errorType, "errorType must not be blank");
            this.rowNumber = rowNumber;
        }

        public int getRowNumber() {
            return rowNumber;
        }

        public String getMessage() {
            return message;
        }

        public String getErrorType() {
            return errorType;
        }
    }

    /**
     * Preview of what would be executed in a dry-run.
     */
    public static class BulkDryRunPreview {
        private final int estimatedAffectedRows;
        private final String executionPlan;
        private final List<String> validationWarnings;

        public BulkDryRunPreview(int estimatedAffectedRows, String executionPlan,
                List<String> validationWarnings) {
            if (estimatedAffectedRows < 0) {
                throw new IllegalArgumentException("estimatedAffectedRows must be non-negative");
            }
            this.executionPlan = requireNonBlank(executionPlan, "executionPlan must not be blank");
            this.estimatedAffectedRows = estimatedAffectedRows;
            this.validationWarnings = validationWarnings != null ? List.copyOf(validationWarnings) : List.of();
        }

        public int getEstimatedAffectedRows() {
            return estimatedAffectedRows;
        }

        public String getExecutionPlan() {
            return executionPlan;
        }

        public List<String> getValidationWarnings() {
            return validationWarnings;
        }
    }

    private static String requireNonBlank(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
