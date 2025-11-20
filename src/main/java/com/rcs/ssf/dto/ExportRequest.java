package com.rcs.ssf.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request for exporting table data in various formats (CSV, JSON).
 * Complements import operations for roundtrip data handling.
 */
public class ExportRequest {
    @NotBlank
    private String tableName;

    @NotNull
    private ExportFormat format;

    private List<DynamicCrudRequest.Filter> filters;

    private List<String> columns;

    private boolean includeHeaders = true;

    private String fileName;

    public enum ExportFormat {
        CSV, JSON, JSONL
    }

    // Getters and setters
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public ExportFormat getFormat() { return format; }
    public void setFormat(ExportFormat format) { this.format = format; }

    public List<DynamicCrudRequest.Filter> getFilters() { return filters; }
    public void setFilters(List<DynamicCrudRequest.Filter> filters) { this.filters = filters; }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public boolean isIncludeHeaders() { return includeHeaders; }
    public void setIncludeHeaders(boolean includeHeaders) { this.includeHeaders = includeHeaders; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}
