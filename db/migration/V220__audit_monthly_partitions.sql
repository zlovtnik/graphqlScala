-- Flyway-style migration: ensure monthly partitions exist for audit tables
SET SERVEROUTPUT ON
DECLARE
    TYPE t_table_list IS TABLE OF VARCHAR2(128);
    c_tables CONSTANT t_table_list := t_table_list(
        'AUDIT_DYNAMIC_CRUD',
        'AUDIT_LOGIN_ATTEMPTS',
        'AUDIT_SESSIONS',
        'AUDIT_ERROR_LOG'
    );
    c_months_ahead CONSTANT PLS_INTEGER := 3; -- create next-quarter partitions
    FUNCTION partition_name(p_month DATE) RETURN VARCHAR2 IS
    BEGIN
        RETURN 'P_' || TO_CHAR(p_month, 'YYYY_MM');
    END;
    FUNCTION partition_upper_bound(p_month DATE) RETURN VARCHAR2 IS
    BEGIN
        RETURN TO_CHAR(ADD_MONTHS(TRUNC(p_month, 'MM'), 1), 'YYYY-MM-DD');
    END;
    PROCEDURE ensure_partition(p_table VARCHAR2, p_month DATE) IS
        v_partition VARCHAR2(30) := partition_name(p_month);
        v_exists    NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM user_tab_partitions
         WHERE table_name = p_table
           AND partition_name = v_partition;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD PARTITION ' || v_partition ||
                              ' VALUES LESS THAN (TO_DATE(''' || partition_upper_bound(p_month) || ''',''YYYY-MM-DD''))';
            DBMS_OUTPUT.PUT_LINE(p_table || ': created partition ' || v_partition);
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table || ': partition ' || v_partition || ' already exists');
        END IF;
    END;
BEGIN
    FOR idx IN c_tables.FIRST .. c_tables.LAST LOOP
        FOR offset IN 0 .. c_months_ahead LOOP
            ensure_partition(c_tables(idx), ADD_MONTHS(TRUNC(SYSDATE, 'MM'), offset));
        END LOOP;
    END LOOP;
END;
/
