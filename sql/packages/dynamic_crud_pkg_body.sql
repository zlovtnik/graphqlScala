CREATE OR REPLACE PACKAGE BODY dynamic_crud_pkg AS

    TYPE t_allowed_table_nt IS TABLE OF VARCHAR2(128);
    g_allowed_tables CONSTANT t_allowed_table_nt := t_allowed_table_nt(
        'USERS',
        'AUDIT_LOGIN_ATTEMPTS',
        'AUDIT_SESSIONS'
    );

    TYPE t_column_meta_rec IS RECORD (
        data_type VARCHAR2(106),
        nullable  VARCHAR2(1)
    );

    TYPE t_column_meta_tab IS TABLE OF t_column_meta_rec INDEX BY VARCHAR2(128);

    TYPE t_bind_tab IS TABLE OF VARCHAR2(4000) INDEX BY PLS_INTEGER;

    FUNCTION normalize_identifier(p_value IN VARCHAR2, p_kind IN VARCHAR2) RETURN VARCHAR2 IS
        v_value VARCHAR2(128) := UPPER(TRIM(p_value));
    BEGIN
        IF v_value IS NULL THEN
            RAISE_APPLICATION_ERROR(-20900, 'Missing ' || p_kind);
        END IF;
        IF NOT REGEXP_LIKE(v_value, '^[A-Z][A-Z0-9_$#]{0,127}$') THEN
            RAISE_APPLICATION_ERROR(-20901, 'Invalid ' || p_kind || ' name: ' || p_value);
        END IF;
        RETURN v_value;
    END normalize_identifier;

    PROCEDURE load_column_metadata(
        p_table_name IN VARCHAR2,
        p_columns    OUT t_column_meta_tab
    ) IS
    BEGIN
        p_columns.DELETE;
        FOR rec IN (
            SELECT column_name, data_type, nullable
            FROM   user_tab_columns
            WHERE  table_name = p_table_name
        ) LOOP
            p_columns(rec.column_name) := t_column_meta_rec(rec.data_type, rec.nullable);
        END LOOP;

        IF p_columns.COUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20902, 'Unknown table: ' || p_table_name);
        END IF;
    END load_column_metadata;

    FUNCTION is_table_allowed(p_table_name IN VARCHAR2) RETURN BOOLEAN IS
        v_table VARCHAR2(128) := normalize_identifier(p_table_name, 'table');
    BEGIN
        FOR i IN g_allowed_tables.FIRST .. g_allowed_tables.LAST LOOP
            IF g_allowed_tables(i) = v_table THEN
                RETURN TRUE;
            END IF;
        END LOOP;
        RETURN FALSE;
    END is_table_allowed;

    FUNCTION normalize_table_name(p_table_name IN VARCHAR2) RETURN VARCHAR2 IS
        v_table VARCHAR2(128) := normalize_identifier(p_table_name, 'table');
    BEGIN
        RETURN v_table;
    END normalize_table_name;

    FUNCTION normalize_operator(p_operator IN VARCHAR2) RETURN VARCHAR2 IS
        v_op VARCHAR2(10) := UPPER(TRIM(p_operator));
    BEGIN
        IF v_op IN ('=', '<>', '!=', '<', '>', '<=', '>=', 'LIKE') THEN
            IF v_op = '!=' THEN
                RETURN '<>';
            END IF;
            RETURN v_op;
        END IF;
        RAISE_APPLICATION_ERROR(-20903, 'Unsupported operator: ' || p_operator);
    END normalize_operator;

    PROCEDURE record_audit(
        p_table_name   IN VARCHAR2,
        p_operation    IN VARCHAR2,
        p_audit        IN dyn_audit_ctx_rec,
        p_status       IN VARCHAR2,
        p_message      IN VARCHAR2,
        p_error_code   IN VARCHAR2,
        p_affected     IN NUMBER
    ) IS
        PRAGMA AUTONOMOUS_TRANSACTION;
        v_actor     VARCHAR2(128);
        v_trace_id  VARCHAR2(128);
        v_client_ip VARCHAR2(45);
        v_metadata  CLOB;
    BEGIN
        -- Validate operation enum: allowed values are INSERT, UPDATE, DELETE, SELECT
        IF p_operation NOT IN ('INSERT', 'UPDATE', 'DELETE', 'SELECT') THEN
            RAISE_APPLICATION_ERROR(
                -20930,
                'Invalid operation: ' || p_operation || '. Allowed values: INSERT, UPDATE, DELETE, SELECT'
            );
        END IF;

        -- Validate status enum: allowed values are SUCCESS, FAILURE, PENDING
        IF p_status NOT IN ('SUCCESS', 'FAILURE', 'PENDING') THEN
            RAISE_APPLICATION_ERROR(
                -20931,
                'Invalid status: ' || p_status || '. Allowed values: SUCCESS, FAILURE, PENDING'
            );
        END IF;

        IF p_audit IS NOT NULL THEN
            v_actor := p_audit.actor;
            v_trace_id := p_audit.trace_id;
            v_client_ip := p_audit.client_ip;
            v_metadata := p_audit.metadata;
        END IF;

        -- id auto-generated via GENERATED ALWAYS AS IDENTITY
        INSERT INTO audit_dynamic_crud (
            table_name,
            operation,
            actor,
            trace_id,
            client_ip,
            metadata,
            affected_rows,
            status,
            message,
            error_code,
            created_at
        ) VALUES (
            p_table_name,
            p_operation,
            v_actor,
            v_trace_id,
            v_client_ip,
            v_metadata,
            p_affected,
            p_status,
            SUBSTR(p_message, 1, 4000),
            p_error_code,
            SYSTIMESTAMP
        );
        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            ROLLBACK;
            RAISE;
    END record_audit;

    PROCEDURE validate_columns(
        p_columns_meta IN t_column_meta_tab,
        p_identifiers  IN dyn_column_name_nt,
        p_context      IN VARCHAR2
    ) IS
        v_name VARCHAR2(128);
    BEGIN
        IF p_identifiers IS NULL THEN
            RETURN;
        END IF;

        FOR i IN 1 .. p_identifiers.COUNT LOOP
            v_name := normalize_identifier(p_identifiers(i), p_context || ' column');
            IF NOT p_columns_meta.EXISTS(v_name) THEN
                RAISE_APPLICATION_ERROR(-20904, 'Column ' || v_name || ' not found in table');
            END IF;
        END LOOP;
    END validate_columns;

    PROCEDURE build_where_clause(
        p_filters      IN dyn_filter_nt,
        p_columns_meta IN t_column_meta_tab,
        p_clause       OUT VARCHAR2,
        p_bindings     IN OUT t_bind_tab,
        p_bind_index   IN OUT PLS_INTEGER
    ) IS
        v_parts  VARCHAR2(32767) := '';
        v_name   VARCHAR2(128);
        v_op     VARCHAR2(10);
        v_value  VARCHAR2(4000);
        v_bind   VARCHAR2(20);
    BEGIN
        IF p_filters IS NULL OR p_filters.COUNT = 0 THEN
            p_clause := NULL;
            RETURN;
        END IF;

        FOR i IN 1 .. p_filters.COUNT LOOP
            v_name := normalize_identifier(p_filters(i).column_name, 'filter');
            IF NOT p_columns_meta.EXISTS(v_name) THEN
                RAISE_APPLICATION_ERROR(-20905, 'Filter column ' || v_name || ' not found');
            END IF;
            v_op := normalize_operator(p_filters(i).operator);
            v_value := p_filters(i).value;

            IF i > 1 THEN
                v_parts := v_parts || ' AND ';
            END IF;

            p_bind_index := p_bind_index + 1;
            p_bindings(p_bind_index) := v_value;
            v_bind := ':b' || p_bind_index;

            v_parts := v_parts || '"' || v_name || '" ' || v_op || ' ' || v_bind;
        END LOOP;

        p_clause := ' WHERE ' || v_parts;
    END build_where_clause;

    PROCEDURE bind_values(
        p_cursor     IN INTEGER,
        p_bindings   IN t_bind_tab,
        p_bind_index IN PLS_INTEGER
    ) IS
    BEGIN
        FOR i IN 1 .. p_bind_index LOOP
            DBMS_SQL.BIND_VARIABLE(p_cursor, ':b' || i, p_bindings(i));
        END LOOP;
    END bind_values;

    PROCEDURE execute_dynamic_dml(
        p_sql         IN VARCHAR2,
        p_bindings    IN t_bind_tab,
        p_bind_index  IN PLS_INTEGER,
        p_rows_affected OUT NUMBER
    ) IS
        v_cursor INTEGER;
    BEGIN
        v_cursor := DBMS_SQL.OPEN_CURSOR;
        DBMS_SQL.PARSE(v_cursor, p_sql, DBMS_SQL.NATIVE);
        bind_values(v_cursor, p_bindings, p_bind_index);
        p_rows_affected := DBMS_SQL.EXECUTE(v_cursor);
        DBMS_SQL.CLOSE_CURSOR(v_cursor);
    EXCEPTION
        WHEN OTHERS THEN
            DBMS_SQL.CLOSE_CURSOR(v_cursor);
            RAISE;
    END execute_dynamic_dml;

    FUNCTION find_value_for_column(
        p_names  IN dyn_column_name_nt,
        p_values IN dyn_column_value_nt,
        p_target IN VARCHAR2
    ) RETURN VARCHAR2 IS
    BEGIN
        IF p_names IS NULL OR p_values IS NULL THEN
            RETURN NULL;
        END IF;
        FOR i IN 1 .. p_names.COUNT LOOP
            IF normalize_identifier(p_names(i), 'column') = p_target THEN
                RETURN p_values(i);
            END IF;
        END LOOP;
        RETURN NULL;
    END find_value_for_column;

    FUNCTION column_list_contains(
        p_names  IN dyn_column_name_nt,
        p_target IN VARCHAR2
    ) RETURN BOOLEAN IS
        v_target VARCHAR2(128) := normalize_identifier(p_target, 'column');
    BEGIN
        IF p_names IS NULL OR p_names.COUNT = 0 THEN
            RETURN FALSE;
        END IF;
        FOR i IN 1 .. p_names.COUNT LOOP
            IF normalize_identifier(p_names(i), 'column') = v_target THEN
                RETURN TRUE;
            END IF;
        END LOOP;
        RETURN FALSE;
    END column_list_contains;

    PROCEDURE execute_operation(
        p_table_name    IN VARCHAR2,
        p_operation     IN t_operation,
        p_column_names  IN dyn_column_name_nt,
        p_column_values IN dyn_column_value_nt,
        p_filters       IN dyn_filter_nt,
        p_audit         IN dyn_audit_ctx_rec,
        p_message       OUT VARCHAR2,
        p_generated_id  OUT VARCHAR2,
        p_affected_rows OUT NUMBER
    ) IS
        v_table_name    VARCHAR2(128);
        v_operation     VARCHAR2(10) := UPPER(TRIM(p_operation));
        v_columns_meta  t_column_meta_tab;
        v_bindings      t_bind_tab;
        v_bind_index    PLS_INTEGER := 0;
        v_sql           VARCHAR2(32767);
        v_set_clause    VARCHAR2(32767);
        v_columns_list  VARCHAR2(32767);
        v_values_list   VARCHAR2(32767);
        v_where_clause  VARCHAR2(32767);
        v_status        VARCHAR2(20) := 'SUCCESS';
        v_error_code    VARCHAR2(50);
    BEGIN
        p_message := NULL;
        p_generated_id := NULL;
        p_affected_rows := 0;

        v_table_name := normalize_table_name(p_table_name);

        -- Normalize SQL operation names to internal constants
        -- Map: INSERT -> CREATE, SELECT -> READ
        IF v_operation = 'INSERT' THEN
            v_operation := c_op_create;
        ELSIF v_operation = 'SELECT' THEN
            v_operation := c_op_read;
        END IF;

        IF NOT is_table_allowed(v_table_name) THEN
            RAISE_APPLICATION_ERROR(-20906, 'Table not allowed: ' || v_table_name);
        END IF;

        load_column_metadata(v_table_name, v_columns_meta);

        IF p_column_names IS NOT NULL AND p_column_values IS NOT NULL THEN
            IF p_column_names.COUNT != p_column_values.COUNT THEN
                RAISE_APPLICATION_ERROR(-20907, 'Column names and values count mismatch');
            END IF;
        END IF;

        validate_columns(v_columns_meta, p_column_names, 'payload');

        IF p_filters IS NOT NULL THEN
            NULL; -- validation occurs in build_where_clause
        END IF;

        CASE v_operation
            WHEN c_op_create THEN
                IF p_column_names IS NULL OR p_column_names.COUNT = 0 THEN
                    RAISE_APPLICATION_ERROR(-20908, 'CREATE requires columns');
                END IF;

                FOR i IN 1 .. p_column_names.COUNT LOOP
                    IF i > 1 THEN
                        v_columns_list := v_columns_list || ', ';
                        v_values_list := v_values_list || ', ';
                    END IF;
                    v_columns_list := v_columns_list || '"' || normalize_identifier(p_column_names(i), 'column') || '"';
                    v_bind_index := v_bind_index + 1;
                    v_bindings(v_bind_index) := p_column_values(i);
                    v_values_list := v_values_list || ':b' || v_bind_index;
                END LOOP;

                v_sql := 'INSERT INTO ' || v_table_name || ' (' || v_columns_list || ') VALUES (' || v_values_list || ')';

                execute_dynamic_dml(v_sql, v_bindings, v_bind_index, p_affected_rows);

                IF p_affected_rows = 1 THEN
                    -- For tables with GENERATED ALWAYS AS IDENTITY, don't try to extract ID from input
                    -- Check if table has an identity column before attempting ID extraction
                    DECLARE
                        v_has_identity NUMBER := 0;
                    BEGIN
                        SELECT COUNT(*) INTO v_has_identity
                        FROM user_tab_identity_cols
                        WHERE table_name = v_table_name;

                        -- Only extract ID if table doesn't have identity column
                        -- (Identity columns are auto-generated by the database)
                        IF v_has_identity = 0 THEN
                            p_generated_id := find_value_for_column(p_column_names, p_column_values, 'ID');
                        END IF;
                    END;
                END IF;
                p_message := 'INSERT SUCCESS';

            WHEN c_op_update THEN
                IF p_column_names IS NULL OR p_column_names.COUNT = 0 THEN
                    RAISE_APPLICATION_ERROR(-20909, 'UPDATE requires columns');
                END IF;

                build_where_clause(p_filters, v_columns_meta, v_where_clause, v_bindings, v_bind_index);
                IF v_where_clause IS NULL THEN
                    RAISE_APPLICATION_ERROR(-20910, 'UPDATE requires filters');
                END IF;

                FOR i IN 1 .. p_column_names.COUNT LOOP
                    IF i > 1 THEN
                        v_set_clause := v_set_clause || ', ';
                    END IF;
                    v_set_clause := v_set_clause || '"' || normalize_identifier(p_column_names(i), 'column') || '" = :b' || (v_bind_index + 1);
                    v_bind_index := v_bind_index + 1;
                    v_bindings(v_bind_index) := p_column_values(i);
                END LOOP;

                IF v_columns_meta.EXISTS('UPDATED_AT') AND NOT column_list_contains(p_column_names, 'UPDATED_AT') THEN
                    IF v_set_clause IS NOT NULL THEN
                        v_set_clause := v_set_clause || ', ';
                    END IF;
                    v_set_clause := v_set_clause || '"UPDATED_AT" = SYSTIMESTAMP';
                END IF;

                v_sql := 'UPDATE ' || v_table_name || ' SET ' || v_set_clause || v_where_clause;

                execute_dynamic_dml(v_sql, v_bindings, v_bind_index, p_affected_rows);
                p_message := 'UPDATE SUCCESS';

            WHEN c_op_delete THEN
                build_where_clause(p_filters, v_columns_meta, v_where_clause, v_bindings, v_bind_index);
                IF v_where_clause IS NULL THEN
                    RAISE_APPLICATION_ERROR(-20911, 'DELETE requires filters');
                END IF;

                v_sql := 'DELETE FROM ' || v_table_name || v_where_clause;
                execute_dynamic_dml(v_sql, v_bindings, v_bind_index, p_affected_rows);
                p_message := 'DELETE SUCCESS';

            WHEN c_op_read THEN
                RAISE_APPLICATION_ERROR(-20912, 'READ operation is not supported in execute_operation');

            ELSE
                RAISE_APPLICATION_ERROR(-20913, 'Unknown operation: ' || v_operation);
        END CASE;

        record_audit(v_table_name, v_operation, p_audit, v_status, p_message, v_error_code, p_affected_rows);

    EXCEPTION
        WHEN OTHERS THEN
            v_status := 'FAILURE';
            v_error_code := TO_CHAR(SQLCODE);
            p_message := SUBSTR(SQLERRM, 1, 4000);
            record_audit(v_table_name, v_operation, p_audit, v_status, p_message, v_error_code, p_affected_rows);
            RAISE;
    END execute_operation;

    PROCEDURE execute_bulk(
        p_table_name IN VARCHAR2,
        p_operation  IN t_operation,
        p_rows       IN dyn_row_op_nt,
        p_filters    IN dyn_filter_nt,
        p_audit      IN dyn_audit_ctx_rec,
        p_message    OUT VARCHAR2,
        p_affected   OUT NUMBER
    ) IS
        v_total NUMBER := 0;
        v_message VARCHAR2(4000);
        v_generated_id VARCHAR2(4000);
        v_affected NUMBER;
    BEGIN
        IF p_rows IS NULL OR p_rows.COUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20914, 'Bulk payload is empty');
        END IF;

        FOR i IN 1 .. p_rows.COUNT LOOP
            execute_operation(
                p_table_name,
                p_operation,
                p_rows(i).column_names,
                p_rows(i).column_values,
                CASE WHEN p_operation = c_op_delete OR p_operation = c_op_update THEN p_filters ELSE NULL END,
                p_audit,
                v_message,
                v_generated_id,
                v_affected
            );
            v_total := v_total + NVL(v_affected, 0);
        END LOOP;

        p_message := 'BULK ' || UPPER(p_operation) || ' SUCCESS';
        p_affected := v_total;
    END execute_bulk;

END dynamic_crud_pkg;
/