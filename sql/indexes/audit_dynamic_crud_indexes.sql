-- Indexes for audit_dynamic_crud table
-- These indexes support audit queries and performance monitoring

DECLARE
	PROCEDURE ensure_index(p_index_name IN VARCHAR2, p_sql IN VARCHAR2) IS
		v_exists NUMBER := 0;
	BEGIN
		SELECT COUNT(*)
			INTO v_exists
			FROM user_indexes
		 WHERE index_name = UPPER(p_index_name);

		IF v_exists = 0 THEN
			BEGIN
				EXECUTE IMMEDIATE p_sql;
				DBMS_OUTPUT.PUT_LINE('Index ' || p_index_name || ' created successfully');
			EXCEPTION
				WHEN OTHERS THEN
					DBMS_OUTPUT.PUT_LINE('Warning: Failed to create index ' || p_index_name || ': ' || SQLERRM);
			END;
		ELSE
			DBMS_OUTPUT.PUT_LINE('Index ' || p_index_name || ' already exists');
		END IF;
	END ensure_index;
BEGIN
	ensure_index('idx_audit_dynamic_crud_table_name',
							 'CREATE INDEX idx_audit_dynamic_crud_table_name ON audit_dynamic_crud(table_name)');

	ensure_index('idx_audit_dynamic_crud_operation',
							 'CREATE INDEX idx_audit_dynamic_crud_operation ON audit_dynamic_crud(operation)');

	ensure_index('idx_audit_dynamic_crud_created_at',
							 'CREATE INDEX idx_audit_dynamic_crud_created_at ON audit_dynamic_crud(created_at)');

	ensure_index('idx_audit_dynamic_crud_actor',
							 'CREATE INDEX idx_audit_dynamic_crud_actor ON audit_dynamic_crud(actor)');

	ensure_index('idx_audit_dynamic_crud_table_created',
							 'CREATE INDEX idx_audit_dynamic_crud_table_created ON audit_dynamic_crud(table_name, created_at)');

	ensure_index('idx_audit_dynamic_crud_op_created',
							 'CREATE INDEX idx_audit_dynamic_crud_op_created ON audit_dynamic_crud(operation, created_at)');

	ensure_index('idx_audit_dynamic_crud_status_created',
							 'CREATE INDEX idx_audit_dynamic_crud_status_created ON audit_dynamic_crud(status, created_at)');
END;
/