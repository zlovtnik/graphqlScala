package com.rcs.ssf.dynamic;

import java.util.Objects;

public record DynamicCrudColumnValue(String column, Object value) {

    public DynamicCrudColumnValue {
        Objects.requireNonNull(column, "column is required");
    }
}
