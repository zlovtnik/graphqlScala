package com.rcs.ssf.controller;

import com.rcs.ssf.dto.DynamicCrudRequest;
import com.rcs.ssf.dto.DynamicCrudResponseDto;
import com.rcs.ssf.service.DynamicCrudService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dynamic-crud")
@PreAuthorize("isAuthenticated()")
public class DynamicCrudController {

    private final DynamicCrudService dynamicCrudService;

    public DynamicCrudController(DynamicCrudService dynamicCrudService) {
        this.dynamicCrudService = dynamicCrudService;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> executeOperation(@RequestBody DynamicCrudRequest request) {
        if ("SELECT".equals(request.getOperation())) {
            DynamicCrudResponseDto response = dynamicCrudService.executeSelect(request);
            return ResponseEntity.ok(response);
        } else {
            // Handle mutations
            dynamicCrudService.executeMutation(request);
            return ResponseEntity.ok().build();
        }
    }

    @GetMapping("/tables")
    public ResponseEntity<String[]> getAvailableTables() {
        return ResponseEntity.ok(dynamicCrudService.getAvailableTables());
    }
}