package com.rcs.ssf.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request for JSON/CSV data import with validation and dry-run preview.
 */
public class ImportRequest {
    @NotBlank
    private String tableName;

    @NotNull
    private ImportFormat format;

    @NotBlank
    private String data;

    @NotNull
    private DynamicCrudRequest.Operation operation;

    private boolean dryRun = true;

    private boolean skipOnError = false;

    private Map<String, String> columnMapping;

    private String metadata;

    public enum ImportFormat {
        CSV, JSON
    }

    // Getters and setters
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public ImportFormat getFormat() { return format; }
    public void setFormat(ImportFormat format) { this.format = format; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public DynamicCrudRequest.Operation getOperation() { return operation; }
    public void setOperation(DynamicCrudRequest.Operation operation) { this.operation = operation; }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public boolean isSkipOnError() { return skipOnError; }
    public void setSkipOnError(boolean skipOnError) { this.skipOnError = skipOnError; }

    public Map<String, String> getColumnMapping() { return columnMapping; }
    public void setColumnMapping(Map<String, String> columnMapping) { this.columnMapping = columnMapping; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
