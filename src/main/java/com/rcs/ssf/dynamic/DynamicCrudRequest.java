package com.rcs.ssf.dynamic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DynamicCrudRequest(
        String table,
        DynamicCrudOperation operation,
        List<DynamicCrudColumnValue> columns,
        List<DynamicCrudFilter> filters,
        DynamicCrudAuditContext auditContext,
        List<DynamicCrudRow> bulkRows
) {

    public DynamicCrudRequest {
        Objects.requireNonNull(table, "table is required");
        Objects.requireNonNull(operation, "operation is required");
    }

    public Optional<List<DynamicCrudColumnValue>> optionalColumns() {
        return Optional.ofNullable(columns);
    }

    public Optional<List<DynamicCrudFilter>> optionalFilters() {
        return Optional.ofNullable(filters);
    }

    public Optional<DynamicCrudAuditContext> optionalAuditContext() {
        return Optional.ofNullable(auditContext);
    }

    public Optional<List<DynamicCrudRow>> optionalBulkRows() {
        return Optional.ofNullable(bulkRows);
    }
}
