-- Package specification for dynamic CRUD operations
CREATE OR REPLACE PACKAGE dynamic_crud_pkg AS
    -- Supported operation names
    c_op_create CONSTANT VARCHAR2(10) := 'CREATE';
    c_op_read   CONSTANT VARCHAR2(10) := 'READ';
    c_op_update CONSTANT VARCHAR2(10) := 'UPDATE';
    c_op_delete CONSTANT VARCHAR2(10) := 'DELETE';

    SUBTYPE t_operation IS VARCHAR2(10);

    -- Metadata helpers
    FUNCTION is_table_allowed(p_table_name IN VARCHAR2) RETURN BOOLEAN;
    FUNCTION normalize_table_name(p_table_name IN VARCHAR2) RETURN VARCHAR2;

    -- Execute a single-row CRUD operation
    PROCEDURE execute_operation(
        p_table_name    IN VARCHAR2,
        p_operation     IN t_operation,
        p_column_names  IN dyn_column_name_nt,
        p_column_values IN dyn_column_value_nt,
        p_filters       IN dyn_filter_nt DEFAULT NULL,
        p_audit         IN dyn_audit_ctx_rec DEFAULT NULL,
        p_message       OUT VARCHAR2,
        p_generated_id  OUT VARCHAR2,
        p_affected_rows OUT NUMBER
    );

    -- Execute a bulk CRUD operation using collection payload
    PROCEDURE execute_bulk(
        p_table_name IN VARCHAR2,
        p_operation  IN t_operation,
        p_rows       IN dyn_row_op_nt,
        p_filters    IN dyn_filter_nt DEFAULT NULL,
        p_audit      IN dyn_audit_ctx_rec DEFAULT NULL,
        p_message    OUT VARCHAR2,
        p_affected   OUT NUMBER
    );
END dynamic_crud_pkg;
/