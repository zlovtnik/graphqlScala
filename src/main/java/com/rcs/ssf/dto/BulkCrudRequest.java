package com.rcs.ssf.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request for bulk CRUD operations (multi-row insert/update/delete).
 * Supports progress tracking, dry-run preview, and comprehensive audit logging.
 */
public class BulkCrudRequest {
    @NotBlank
    private String tableName;

    @NotNull
    private DynamicCrudRequest.Operation operation;

    @NotEmpty
    @Valid
    private List<BulkRow> rows;

    private boolean dryRun = false;

    private boolean skipOnError = false;

    private Integer batchSize = 100;

    private String metadata;

    // Getters and setters
    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public DynamicCrudRequest.Operation getOperation() {
        return operation;
    }

    public void setOperation(DynamicCrudRequest.Operation operation) {
        this.operation = operation;
    }

    public List<BulkRow> getRows() {
        return rows;
    }

    public void setRows(List<BulkRow> rows) {
        this.rows = rows;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isSkipOnError() {
        return skipOnError;
    }

    public void setSkipOnError(boolean skipOnError) {
        this.skipOnError = skipOnError;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    /**
     * Represents a single row in a bulk operation.
     */
    public static class BulkRow {
        @Valid
        private List<DynamicCrudRequest.ColumnValue> columns;

        @Valid
        private List<DynamicCrudRequest.Filter> filters;

        public List<DynamicCrudRequest.ColumnValue> getColumns() {
            return columns;
        }

        public void setColumns(List<DynamicCrudRequest.ColumnValue> columns) {
            this.columns = columns;
        }

        public List<DynamicCrudRequest.Filter> getFilters() {
            return filters;
        }

        public void setFilters(List<DynamicCrudRequest.Filter> filters) {
            this.filters = filters;
        }
    }
}
