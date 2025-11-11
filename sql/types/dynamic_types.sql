-- Types for dynamic CRUD operations (used by dynamic_crud_pkg)
CREATE OR REPLACE TYPE dyn_column_name_nt AS TABLE OF VARCHAR2(128);
/

CREATE OR REPLACE TYPE dyn_column_value_nt AS TABLE OF VARCHAR2(4000);
/

CREATE OR REPLACE TYPE dyn_filter_rec AS OBJECT (
    column_name VARCHAR2(128),
    operator    VARCHAR2(10),
    value       VARCHAR2(4000)
);
/

CREATE OR REPLACE TYPE dyn_filter_nt AS TABLE OF dyn_filter_rec;
/

CREATE OR REPLACE TYPE dyn_row_op_rec AS OBJECT (
    column_names  dyn_column_name_nt,
    column_values dyn_column_value_nt
);
/

CREATE OR REPLACE TYPE dyn_row_op_nt AS TABLE OF dyn_row_op_rec;
/

CREATE OR REPLACE TYPE dyn_audit_ctx_rec AS OBJECT (
    actor     VARCHAR2(128),
    trace_id  VARCHAR2(128),
    client_ip VARCHAR2(45),
    metadata  CLOB
);
/