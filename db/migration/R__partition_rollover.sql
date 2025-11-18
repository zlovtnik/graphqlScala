-- Repeatable Flyway migration driving rolling partition maintenance.
-- Responsibilities:
--   * Create partitions 90 days in advance following P_YYYY_MM naming.
--   * Move partitions older than 12 months to an archive tablespace (without Advanced Compression on XE).
--   * Drop partitions older than 24 months to enforce retention policy.
SET SERVEROUTPUT ON
DECLARE
    TYPE t_table_list IS TABLE OF VARCHAR2(128);
    c_tables CONSTANT t_table_list := t_table_list(
        'AUDIT_DYNAMIC_CRUD',
        'AUDIT_LOGIN_ATTEMPTS',
        'AUDIT_SESSIONS',
        'AUDIT_ERROR_LOG',
        'AUDIT_GRAPHQL_COMPLEXITY',
        'AUDIT_GRAPHQL_EXECUTION_PLANS',
        'AUDIT_CIRCUIT_BREAKER_EVENTS',
        'AUDIT_HTTP_COMPRESSION',
        'AUDIT_MFA_EVENTS'
    );
    c_future_months CONSTANT PLS_INTEGER := 3;
    c_archive_threshold_months CONSTANT PLS_INTEGER := 12;
    c_retention_months CONSTANT PLS_INTEGER := 24;
    c_archive_tablespace CONSTANT VARCHAR2(64) := NVL(sys_context('USERENV', 'CLIENT_IDENTIFIER'), 'AUDIT_ARCHIVE_TS');

    FUNCTION partition_name(p_month DATE) RETURN VARCHAR2 IS
    BEGIN
        RETURN 'P_' || TO_CHAR(p_month, 'YYYY_MM');
    END;

    FUNCTION partition_month(p_partition_name VARCHAR2) RETURN DATE IS
    BEGIN
        IF REGEXP_LIKE(p_partition_name, '^P_[0-9]{4}_[0-9]{2}$') THEN
            RETURN TO_DATE(SUBSTR(p_partition_name, 3), 'YYYY_MM');
        END IF;
        RETURN NULL;
    END;

    PROCEDURE ensure_future_partitions(p_table VARCHAR2) IS
    BEGIN
        IF c_future_months <= 0 THEN
            RETURN;
        END IF;
        FOR offset IN 0 .. c_future_months - 1 LOOP
            DECLARE
                v_month DATE := ADD_MONTHS(TRUNC(SYSDATE, 'MM'), offset);
                v_part  VARCHAR2(30) := partition_name(v_month);
                v_cnt   NUMBER;
            BEGIN
                SELECT COUNT(*)
                  INTO v_cnt
                  FROM user_tab_partitions
                 WHERE table_name = p_table
                   AND partition_name = v_part;

                IF v_cnt = 0 THEN
                    EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD PARTITION ' || v_part ||
                                      ' VALUES LESS THAN (TO_DATE(''' || TO_CHAR(ADD_MONTHS(v_month, 1), 'YYYY-MM-DD') || ''',''YYYY-MM-DD''))';
                    DBMS_OUTPUT.PUT_LINE(p_table || ': created future partition ' || v_part);
                END IF;
            END;
        END LOOP;
    END;

    PROCEDURE archive_partition(p_table VARCHAR2, p_partition VARCHAR2) IS
    BEGIN
        BEGIN
            -- Oracle XE does not support Advanced Compression, so avoid COMPRESS clauses.
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' MOVE PARTITION ' || p_partition ||
                              ' TABLESPACE ' || c_archive_tablespace || ' UPDATE INDEXES';
            DBMS_OUTPUT.PUT_LINE(p_table || ': archived partition ' || p_partition || ' (moved without compression)');
        EXCEPTION
            WHEN OTHERS THEN
                DBMS_OUTPUT.PUT_LINE(p_table || ': failed to archive partition ' || p_partition || ' -> ' || SQLERRM);
        END;
    END;

    PROCEDURE drop_partition(p_table VARCHAR2, p_partition VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' DROP PARTITION ' || p_partition || ' UPDATE INDEXES';
        DBMS_OUTPUT.PUT_LINE(p_table || ': dropped partition ' || p_partition);
    EXCEPTION
        WHEN OTHERS THEN
            DBMS_OUTPUT.PUT_LINE(p_table || ': failed to drop partition ' || p_partition || ' -> ' || SQLERRM);
    END;

BEGIN
    FOR idx IN c_tables.FIRST .. c_tables.LAST LOOP
        ensure_future_partitions(c_tables(idx));

        FOR p IN (
            SELECT partition_name
            FROM   user_tab_partitions
            WHERE  table_name = c_tables(idx)
        ) LOOP
            IF partition_month(p.partition_name) IS NULL THEN
                CONTINUE;
            END IF;

            IF partition_month(p.partition_name) <= ADD_MONTHS(TRUNC(SYSDATE, 'MM'), -c_retention_months) THEN
                drop_partition(c_tables(idx), p.partition_name);
            ELSIF partition_month(p.partition_name) <= ADD_MONTHS(TRUNC(SYSDATE, 'MM'), -c_archive_threshold_months) THEN
                archive_partition(c_tables(idx), p.partition_name);
            END IF;
        END LOOP;
    END LOOP;
END;
/
