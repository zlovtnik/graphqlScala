package com.rcs.ssf.dto;

import java.util.List;

/**
 * Response for bulk CRUD operations with progress tracking and detailed error reporting.
 */
public class BulkCrudResponse {
    private int totalRows;
    private int successfulRows;
    private int failedRows;
    private int processedRows;
    private String status;
    private List<RowError> errors;
    private long durationMs;
    private BulkDryRunPreview dryRunPreview;

    public BulkCrudResponse(int totalRows, int successfulRows, int failedRows, 
                            int processedRows, String status, List<RowError> errors,
                            long durationMs) {
        this.totalRows = totalRows;
        this.successfulRows = successfulRows;
        this.failedRows = failedRows;
        this.processedRows = processedRows;
        this.status = status;
        this.errors = errors != null ? errors : List.of();
        this.durationMs = durationMs;
    }

    public BulkCrudResponse(int totalRows, int successfulRows, int failedRows,
                            int processedRows, String status, List<RowError> errors,
                            long durationMs, BulkDryRunPreview dryRunPreview) {
        this(totalRows, successfulRows, failedRows, processedRows, status, errors, durationMs);
        this.dryRunPreview = dryRunPreview;
    }

    // Getters
    public int getTotalRows() { return totalRows; }
    public int getSuccessfulRows() { return successfulRows; }
    public int getFailedRows() { return failedRows; }
    public int getProcessedRows() { return processedRows; }
    public String getStatus() { return status; }
    public List<RowError> getErrors() { return errors; }
    public long getDurationMs() { return durationMs; }
    public BulkDryRunPreview getDryRunPreview() { return dryRunPreview; }

    /**
     * Details about an error in a specific row.
     */
    public static class RowError {
        private int rowNumber;
        private String message;
        private String errorType;

        public RowError(int rowNumber, String message, String errorType) {
            this.rowNumber = rowNumber;
            this.message = message;
            this.errorType = errorType;
        }

        public int getRowNumber() { return rowNumber; }
        public String getMessage() { return message; }
        public String getErrorType() { return errorType; }
    }

    /**
     * Preview of what would be executed in a dry-run.
     */
    public static class BulkDryRunPreview {
        private int estimatedAffectedRows;
        private String executionPlan;
        private List<String> validationWarnings;

        public BulkDryRunPreview(int estimatedAffectedRows, String executionPlan, 
                                 List<String> validationWarnings) {
            this.estimatedAffectedRows = estimatedAffectedRows;
            this.executionPlan = executionPlan;
            this.validationWarnings = validationWarnings != null ? validationWarnings : List.of();
        }

        public int getEstimatedAffectedRows() { return estimatedAffectedRows; }
        public String getExecutionPlan() { return executionPlan; }
        public List<String> getValidationWarnings() { return validationWarnings; }
    }
}
