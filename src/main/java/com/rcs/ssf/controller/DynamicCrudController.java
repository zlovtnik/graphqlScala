package com.rcs.ssf.controller;

import jakarta.validation.Valid;
import com.rcs.ssf.dto.*;
import com.rcs.ssf.service.BulkCrudService;
import com.rcs.ssf.service.DynamicCrudService;
import com.rcs.ssf.service.ImportExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/dynamic-crud")
@PreAuthorize("isAuthenticated()")
public class DynamicCrudController {

    private final DynamicCrudService dynamicCrudService;
    private final BulkCrudService bulkCrudService;
    private final ImportExportService importExportService;

    public DynamicCrudController(DynamicCrudService dynamicCrudService,
                                 BulkCrudService bulkCrudService,
                                 ImportExportService importExportService) {
        this.dynamicCrudService = dynamicCrudService;
        this.bulkCrudService = bulkCrudService;
        this.importExportService = importExportService;
    }

    @PostMapping("/execute")
    public ResponseEntity<DynamicCrudResponseDto> executeOperation(@Valid @RequestBody DynamicCrudRequest request) {
        if (request.getOperation() == DynamicCrudRequest.Operation.SELECT) {
            DynamicCrudResponseDto response = dynamicCrudService.executeSelect(request);
            return ResponseEntity.ok(response);
        } else {
            // Handle mutations
            DynamicCrudResponseDto mutationResponse = dynamicCrudService.executeMutation(request);
            return ResponseEntity.ok(mutationResponse);
        }
    }

    @PostMapping("/bulk")
    public ResponseEntity<BulkCrudResponse> executeBulkOperation(@Valid @RequestBody BulkCrudRequest request) {
        BulkCrudResponse response = bulkCrudService.executeBulkOperation(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/import")
    public ResponseEntity<BulkCrudResponse> importData(@Valid @RequestBody ImportRequest request) {
        BulkCrudResponse response = importExportService.importData(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportData(@Valid @RequestBody ExportRequest request) {
        ImportExportService.ExportResult result = importExportService.exportData(request);
        
        String contentType = switch (result.getFormat()) {
            case "CSV" -> "text/csv; charset=UTF-8";
            case "JSON" -> "application/json; charset=UTF-8";
            case "JSONL" -> "application/x-ndjson; charset=UTF-8";
            default -> "text/plain; charset=UTF-8";
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + result.getFileName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(result.getData().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/tables")
    public ResponseEntity<List<String>> getAvailableTables() {
        String[] tablesArray = dynamicCrudService.getAvailableTables();
        List<String> tablesList = Arrays.asList(tablesArray);
        return ResponseEntity.ok(tablesList);
    }

    @GetMapping("/schema/{table}")
    public ResponseEntity<DynamicCrudResponseDto.SchemaMetadata> getTableSchema(@PathVariable String table) {
        DynamicCrudResponseDto.SchemaMetadata schema = dynamicCrudService.getTableSchema(table);
        return ResponseEntity.ok(schema);
    }
}