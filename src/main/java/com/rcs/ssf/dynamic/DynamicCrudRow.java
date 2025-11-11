package com.rcs.ssf.dynamic;

import java.util.List;
import java.util.Objects;

public record DynamicCrudRow(List<DynamicCrudColumnValue> columns) {

    public DynamicCrudRow {
        Objects.requireNonNull(columns, "columns is required");
    }
}
