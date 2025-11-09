package com.example.ssf.dynamic;

import java.util.Objects;
import java.util.Set;

public record DynamicCrudFilter(String column, String operator, Object value) {

    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "=", "!=", ">", "<", ">=", "<=", "IN", "NOT IN", "LIKE", "IS NULL", "IS NOT NULL"
    );

    public DynamicCrudFilter {
        Objects.requireNonNull(column, "column is required");
        Objects.requireNonNull(operator, "operator is required");
        if (!ALLOWED_OPERATORS.contains(operator.trim().toUpperCase())) {
            throw new IllegalArgumentException("Invalid operator: " + operator);
        }
    }
}
