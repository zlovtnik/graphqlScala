package com.rcs.ssf.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rcs.ssf.dto.*;
import com.rcs.ssf.dto.BulkCrudResponse.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for handling JSON/CSV import and export operations with validation,
 * data transformation, and roundtrip support.
 * Integrates with BulkCrudService for actual data persistence.
 */
@Slf4j
@Service
public class ImportExportService {

    private final BulkCrudService bulkCrudService;
    private final DynamicCrudService dynamicCrudService;
    private final ObjectMapper objectMapper;
    private final String requiredRole;
    private final Set<String> allowedTables;

    public ImportExportService(BulkCrudService bulkCrudService,
                               DynamicCrudService dynamicCrudService,
                               @Value("${security.dynamicCrud.requiredRole:ROLE_ADMIN}") String requiredRole,
                               @Value("${importExport.allowedTables:audit_login_attempts,audit_sessions,audit_dynamic_crud,audit_error_log}") String allowedTablesStr) {
        this.bulkCrudService = bulkCrudService;
        this.dynamicCrudService = dynamicCrudService;
        this.objectMapper = new ObjectMapper();
        this.requiredRole = requiredRole;
        this.allowedTables = Arrays.stream(allowedTablesStr.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    /**
     * Imports data from JSON or CSV format with validation, optional dry-run, and conversion to bulk operations.
     * Supports flexible column mapping and error handling strategies.
     *
     * @param request Import request with format, data, and operation details
     * @return Response with validation results and operation status
     */
    public BulkCrudResponse importData(ImportRequest request) {
        assertAuthorizedForDynamicCrud();
        validateImportRequest(request);

        log.info("Starting import operation: format={}, table={}, dryRun={}, rows=~{}",
                request.getFormat(), request.getTableName(), request.isDryRun(),
                estimateRowCount(request.getData()));

        try {
            List<BulkCrudRequest.BulkRow> rows = parseData(request);
            
            // Apply column mapping if provided
            if (request.getColumnMapping() != null && !request.getColumnMapping().isEmpty()) {
                rows = applyColumnMapping(rows, request.getColumnMapping());
            }

            BulkCrudRequest bulkRequest = new BulkCrudRequest();
            bulkRequest.setTableName(request.getTableName());
            bulkRequest.setOperation(request.getOperation());
            bulkRequest.setRows(rows);
            bulkRequest.setDryRun(request.isDryRun());
            bulkRequest.setSkipOnError(request.isSkipOnError());
            bulkRequest.setBatchSize(100);
            bulkRequest.setMetadata("import_" + request.getFormat().name().toLowerCase() 
                    + "_" + UUID.randomUUID().toString());

            return bulkCrudService.executeBulkOperation(bulkRequest);
        } catch (Exception ex) {
            log.error("Import failed for table '{}'", request.getTableName(), ex);
            return new BulkCrudResponse(0, 0, 1, 0, Status.IMPORT_FAILED, 
                    List.of(new BulkCrudResponse.RowError(0, ex.getMessage(), "IMPORT_ERROR")), 0L);
        }
    }

    /**
     * Exports table data in specified format (CSV, JSON, JSONL) for roundtrip operations.
     * Respects column filtering and authentication-based access control.
     *
     * @param request Export request with format and table specifications
     * @return Formatted export data ready for download or reimport
     */
    public ExportResult exportData(ExportRequest request) {
        assertAuthorizedForDynamicCrud();
        validateExportRequest(request);

        log.info("Starting export operation: format={}, table={}", request.getFormat(), request.getTableName());

        try {
            // Use DynamicCrudService to fetch data with filters
            DynamicCrudRequest selectRequest = new DynamicCrudRequest();
            selectRequest.setTableName(request.getTableName());
            selectRequest.setOperation(DynamicCrudRequest.Operation.SELECT);
            selectRequest.setFilters(request.getFilters());

            DynamicCrudResponseDto response = dynamicCrudService.executeSelect(selectRequest);
            
            // Filter columns if specified
            List<String> columnsToExport = request.getColumns();
            if (columnsToExport == null || columnsToExport.isEmpty()) {
                columnsToExport = response.getColumns().stream()
                        .map(DynamicCrudResponseDto.ColumnMeta::getName)
                        .collect(Collectors.toList());
            }

            String formattedData = formatExport(
                    response.getRows(),
                    columnsToExport,
                    request.getFormat(),
                    request.isIncludeHeaders()
            );

            String fileName = request.getFileName() != null 
                    ? request.getFileName()
                    : request.getTableName() + "_export." + getFileExtension(request.getFormat());

            return new ExportResult(fileName, formattedData, request.getFormat().name());
        } catch (Exception ex) {
            log.error("Export failed for table '{}'", request.getTableName(), ex);
            throw new RuntimeException("Export failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Validates import request parameters and data format.
     */
    private void validateImportRequest(ImportRequest request) {
        if (request.getTableName() == null || request.getTableName().isBlank()) {
            throw new IllegalArgumentException("Table name is required");
        }
        if (!allowedTables.contains(request.getTableName().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Table not allowed: " + request.getTableName());
        }
        if (request.getFormat() == null) {
            throw new IllegalArgumentException("Format is required");
        }
        if (request.getData() == null || request.getData().isBlank()) {
            throw new IllegalArgumentException("Data is required");
        }
        if (request.getOperation() == null) {
            throw new IllegalArgumentException("Operation is required");
        }
    }

    /**
     * Validates export request parameters.
     */
    private void validateExportRequest(ExportRequest request) {
        if (request.getTableName() == null || request.getTableName().isBlank()) {
            throw new IllegalArgumentException("Table name is required");
        }
        if (!allowedTables.contains(request.getTableName().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Table not allowed: " + request.getTableName());
        }
        if (request.getFormat() == null) {
            throw new IllegalArgumentException("Format is required");
        }
    }

    private List<BulkCrudRequest.BulkRow> parseData(ImportRequest request) throws IOException {
        return switch (request.getFormat()) {
            case CSV -> parseCSV(request.getData(), request.getColumnMapping());
            case JSON -> parseJSON(request.getData(), request.getColumnMapping());
        };
    }

    /**
     * Parses CSV data with optional header row.
     */
    private List<BulkCrudRequest.BulkRow> parseCSV(String csvData, Map<String, String> columnMapping) {
        List<BulkCrudRequest.BulkRow> rows = new ArrayList<>();
        String[] lines = csvData.split("\\r?\\n|\\r");

        if (lines.length == 0) {
            return rows;
        }

        String headerLine = lines[0];
        String[] headers = parseCSVLine(headerLine);

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] values = parseCSVLine(line);
            BulkCrudRequest.BulkRow row = new BulkCrudRequest.BulkRow();
            List<DynamicCrudRequest.ColumnValue> columns = new ArrayList<>();

            for (int j = 0; j < Math.min(headers.length, values.length); j++) {
                String originalName = headers[j];
                String columnName = columnMapping != null && columnMapping.containsKey(originalName) 
                        ? columnMapping.get(originalName) 
                        : originalName;
                DynamicCrudRequest.ColumnValue col = new DynamicCrudRequest.ColumnValue();
                col.setName(columnName);
                col.setValue(values[j].isEmpty() ? null : values[j]);
                columns.add(col);
            }

            row.setColumns(columns);
            rows.add(row);
        }

        return rows;
    }

    /**
     * Parses a CSV line respecting quoted fields.
     */
    private String[] parseCSVLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        values.add(current.toString().trim());
        return values.toArray(new String[0]);
    }

    /**
     * Parses JSON array format data.
     */
    private List<BulkCrudRequest.BulkRow> parseJSON(String jsonData, Map<String, String> columnMapping) throws IOException {
        List<BulkCrudRequest.BulkRow> rows = new ArrayList<>();
        JsonNode root = objectMapper.readTree(jsonData);

        if (!root.isArray()) {
            throw new IllegalArgumentException("JSON data must be an array of objects");
        }

        for (JsonNode item : root) {
            if (!item.isObject()) {
                continue;
            }

            BulkCrudRequest.BulkRow row = new BulkCrudRequest.BulkRow();
            List<DynamicCrudRequest.ColumnValue> columns = new ArrayList<>();

            Iterator<String> fieldNames = item.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                String columnName = columnMapping != null && columnMapping.containsKey(fieldName)
                        ? columnMapping.get(fieldName)
                        : fieldName;

                DynamicCrudRequest.ColumnValue col = new DynamicCrudRequest.ColumnValue();
                col.setName(columnName);
                
                JsonNode value = item.get(fieldName);
                col.setValue(value.isNull() ? null : value.asText());
                columns.add(col);
            }

            row.setColumns(columns);
            rows.add(row);
        }

        return rows;
    }

    /**
     * Applies column mapping transformation to rows.
     */
    private List<BulkCrudRequest.BulkRow> applyColumnMapping(List<BulkCrudRequest.BulkRow> rows,
                                                             Map<String, String> mapping) {
        return rows.stream()
                .map(row -> {
                    List<DynamicCrudRequest.ColumnValue> newColumns = new ArrayList<>();
                    List<DynamicCrudRequest.ColumnValue> oldColumns = row.getColumns();
                    
                    for (DynamicCrudRequest.ColumnValue old : oldColumns) {
                        String newName = mapping.getOrDefault(old.getName(), old.getName());
                        DynamicCrudRequest.ColumnValue mapped = new DynamicCrudRequest.ColumnValue();
                        mapped.setName(newName);
                        mapped.setValue(old.getValue());
                        newColumns.add(mapped);
                    }
                    
                    BulkCrudRequest.BulkRow newRow = new BulkCrudRequest.BulkRow();
                    newRow.setColumns(newColumns);
                    newRow.setFilters(row.getFilters());
                    return newRow;
                })
                .collect(Collectors.toList());
    }

    /**
     * Formats row data into specified export format.
     */
    private String formatExport(List<Map<String, Object>> rows, List<String> columns,
                                ExportRequest.ExportFormat format, boolean includeHeaders) {
        return switch (format) {
            case CSV -> formatAsCSV(rows, columns, includeHeaders);
            case JSON -> formatAsJSON(rows, columns);
            case JSONL -> formatAsJSONL(rows, columns);
        };
    }

    /**
     * Formats data as CSV with proper escaping.
     */
    private String formatAsCSV(List<Map<String, Object>> rows, List<String> columns, boolean includeHeaders) {
        StringBuilder csv = new StringBuilder();

        if (includeHeaders) {
            String headerLine = columns.stream()
                    .map(this::escapeCsvValue)
                    .collect(Collectors.joining(","));
            csv.append(headerLine).append("\n");
        }

        for (Map<String, Object> row : rows) {
            String valueLine = columns.stream()
                    .map(col -> escapeCsvValue(String.valueOf(row.getOrDefault(col, ""))))
                    .collect(Collectors.joining(","));
            csv.append(valueLine).append("\n");
        }

        return csv.toString();
    }

    /**
     * Formats data as JSON array.
     */
    private String formatAsJSON(List<Map<String, Object>> rows, List<String> columns) {
        try {
            List<Map<String, Object>> filtered = rows.stream()
                    .map(row -> {
                        Map<String, Object> filteredRow = new LinkedHashMap<>();
                        for (String col : columns) {
                            filteredRow.put(col, row.get(col));
                        }
                        return filteredRow;
                    })
                    .collect(Collectors.toList());

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(filtered);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to format JSON", ex);
        }
    }

    /**
     * Formats data as JSONL (JSON Lines - one object per line).
     */
    private String formatAsJSONL(List<Map<String, Object>> rows, List<String> columns) {
        try {
            StringBuilder jsonl = new StringBuilder();
            for (Map<String, Object> row : rows) {
                Map<String, Object> filteredRow = new LinkedHashMap<>();
                for (String col : columns) {
                    filteredRow.put(col, row.get(col));
                }
                jsonl.append(objectMapper.writeValueAsString(filteredRow)).append("\n");
            }
            return jsonl.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to format JSONL", ex);
        }
    }

    /**
     * Escapes CSV values for proper formatting.
     */
    private String escapeCsvValue(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Gets file extension for export format.
     */
    private String getFileExtension(ExportRequest.ExportFormat format) {
        return switch (format) {
            case CSV -> "csv";
            case JSON -> "json";
            case JSONL -> "jsonl";
        };
    }

    /**
     * Estimates row count from data string for logging.
     * Note: This is an estimate and may overcount for CSV data with embedded newlines in quoted fields.
     */
    private int estimateRowCount(String data) {
        return Math.max(1, data.split("\n").length - 1);
    }

    /**
     * Checks authorization for import/export operations.
     */
    private void assertAuthorizedForDynamicCrud() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("Import/Export operations require authentication");
        }

        boolean hasRequiredRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(requiredRole::equals);

        if (!hasRequiredRole) {
            throw new AccessDeniedException("Import/Export operations require administrative privileges");
        }
    }

    /**
     * Result wrapper for export operations.
     */
    public static class ExportResult {
        private final String fileName;
        private final String data;
        private final String format;

        public ExportResult(String fileName, String data, String format) {
            this.fileName = fileName;
            this.data = data;
            this.format = format;
        }

        public String getFileName() { return fileName; }
        public String getData() { return data; }
        public String getFormat() { return format; }
    }
}
