SET SERVEROUTPUT ON
SET LINESIZE 200
SET PAGESIZE 200

PROMPT Running EXPLAIN PLAN harness for partition pruning validation

VARIABLE v_query CLOB

DECLARE
    TYPE t_query_list IS TABLE OF VARCHAR2(4000);
    c_queries CONSTANT t_query_list := t_query_list(
        'SELECT COUNT(*) FROM audit_dynamic_crud WHERE created_at BETWEEN :start_ts AND :end_ts',
        'SELECT username, COUNT(*) FROM audit_login_attempts WHERE created_at >= :start_ts GROUP BY username',
        'SELECT user_id, COUNT(*) FROM audit_sessions WHERE created_at >= :start_ts GROUP BY user_id',
        'SELECT error_code, COUNT(*) FROM audit_error_log WHERE created_at BETWEEN :start_ts AND :end_ts GROUP BY error_code'
    );
    v_idx NUMBER := 0;
BEGIN
    FOR i IN c_queries.FIRST .. c_queries.LAST LOOP
        v_idx := v_idx + 1;
        DBMS_OUTPUT.PUT_LINE('PLAN ' || v_idx || ': ' || c_queries(i));
        EXECUTE IMMEDIATE 'EXPLAIN PLAN SET STATEMENT_ID = ''partition_' || v_idx || ''' FOR ' || c_queries(i)
            USING (SYSTIMESTAMP - INTERVAL '30' DAY), SYSTIMESTAMP;
    END LOOP;
END;
/

SELECT statement_id, plan_table_output
FROM   TABLE(DBMS_XPLAN.display(NULL, NULL, 'BASIC PROJECTION PARTITION'))
WHERE  statement_id LIKE 'PARTITION_%'
ORDER BY statement_id;
