package com.rcs.ssf.dynamic;

import java.util.List;
import java.util.Objects;

public record DynamicCrudRow(List<DynamicCrudColumnValue> columns, List<DynamicCrudFilter> filters) {

    public DynamicCrudRow {
        Objects.requireNonNull(columns, "columns is required");
        Objects.requireNonNull(filters, "filters is required");
    }
}
