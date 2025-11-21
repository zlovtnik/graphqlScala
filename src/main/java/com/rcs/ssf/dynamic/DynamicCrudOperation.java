package com.rcs.ssf.dynamic;

/**
 * Supported operations in {@code dynamic_crud_pkg}.
 * Maps application-level operations to PL/SQL allowed values:
 * - CREATE → INSERT
 * - READ → SELECT
 * - UPDATE → UPDATE
 * - DELETE → DELETE
 */
public enum DynamicCrudOperation {
    CREATE,
    READ,
    UPDATE,
    DELETE;

    public String toPlsqlLiteral() {
        return switch (this) {
            case CREATE -> "INSERT";
            case READ -> "SELECT";
            case UPDATE -> "UPDATE";
            case DELETE -> "DELETE";
        };
    }
}
