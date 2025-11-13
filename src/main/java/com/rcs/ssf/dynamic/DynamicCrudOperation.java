package com.rcs.ssf.dynamic;

/**
 * Supported operations in {@code dynamic_crud_pkg}.
 */
public enum DynamicCrudOperation {
    CREATE,
    READ,
    UPDATE,
    DELETE;

    public String toPlsqlLiteral() {
        return name();
    }
}
