-- PL/SQL Block to Drop All Objects from Current Schema
-- This script safely removes all database objects in dependency order:
-- 1. Packages (bodies first, then specs)
-- 2. Types (with FORCE to handle dependencies)
-- 3. Tables (with CASCADE CONSTRAINTS)
-- 4. Views, Sequences, Functions, Procedures, Triggers
-- 5. Other object types
--
-- Usage: sqlplus user/password @drop_all_objects.sql
-- Or paste the entire block into SQL*Plus or SQLcl
--
-- WARNING: This will DELETE ALL OBJECTS. Use with caution!

SET SERVEROUTPUT ON
SET ECHO OFF
SET FEEDBACK OFF
DECLARE
    v_object_type   VARCHAR2(30);
    v_object_name   VARCHAR2(128);
    v_cursor_id     INTEGER;
    v_exec_result   INTEGER;
    v_error_count   INTEGER := 0;
    v_success_count INTEGER := 0;
    
    -- List of object types to drop, in dependency order
    TYPE t_object_types IS TABLE OF VARCHAR2(30);
    v_types CONSTANT t_object_types := t_object_types(
        'PACKAGE BODY',      -- Drop package bodies first
        'PACKAGE',           -- Then package specs
        'TYPE',              -- Drop types (with FORCE)
        'TABLE',             -- Drop tables (with CASCADE CONSTRAINTS)
        'VIEW',
        'SEQUENCE',
        'FUNCTION',
        'PROCEDURE',
        'TRIGGER',
        'SYNONYM',
        'MATERIALIZED VIEW',
        'DIMENSION',
        'CLUSTER'
    );
    
    PROCEDURE drop_object(p_type IN VARCHAR2, p_name IN VARCHAR2) IS
        v_sql VARCHAR2(512);
    BEGIN
        CASE p_type
            WHEN 'PACKAGE BODY' THEN
                v_sql := 'DROP PACKAGE BODY "' || p_name || '"';
            WHEN 'PACKAGE' THEN
                v_sql := 'DROP PACKAGE "' || p_name || '"';
            WHEN 'TYPE' THEN
                v_sql := 'DROP TYPE "' || p_name || '" FORCE';
            WHEN 'TABLE' THEN
                v_sql := 'DROP TABLE "' || p_name || '" CASCADE CONSTRAINTS PURGE';
            WHEN 'VIEW' THEN
                v_sql := 'DROP VIEW "' || p_name || '" CASCADE';
            WHEN 'SEQUENCE' THEN
                v_sql := 'DROP SEQUENCE "' || p_name || '"';
            WHEN 'FUNCTION' THEN
                v_sql := 'DROP FUNCTION "' || p_name || '"';
            WHEN 'PROCEDURE' THEN
                v_sql := 'DROP PROCEDURE "' || p_name || '"';
            WHEN 'TRIGGER' THEN
                v_sql := 'DROP TRIGGER "' || p_name || '"';
            WHEN 'SYNONYM' THEN
                v_sql := 'DROP SYNONYM "' || p_name || '"';
            WHEN 'MATERIALIZED VIEW' THEN
                v_sql := 'DROP MATERIALIZED VIEW "' || p_name || '"';
            WHEN 'DIMENSION' THEN
                v_sql := 'DROP DIMENSION "' || p_name || '"';
            WHEN 'CLUSTER' THEN
                v_sql := 'DROP CLUSTER "' || p_name || '" CASCADE CONSTRAINTS';
            ELSE
                RETURN;
        END CASE;
        
        EXECUTE IMMEDIATE v_sql;
        v_success_count := v_success_count + 1;
        DBMS_OUTPUT.PUT_LINE('✓ Dropped ' || p_type || ': ' || p_name);
    EXCEPTION
        WHEN OTHERS THEN
            v_error_count := v_error_count + 1;
            DBMS_OUTPUT.PUT_LINE('✗ Failed to drop ' || p_type || ' ' || p_name || ': ' || SQLERRM);
    END drop_object;

BEGIN
    DBMS_OUTPUT.PUT_LINE('========================================');
    DBMS_OUTPUT.PUT_LINE('Starting cleanup of all objects...');
    DBMS_OUTPUT.PUT_LINE('Current user: ' || USER);
    DBMS_OUTPUT.PUT_LINE('========================================');
    DBMS_OUTPUT.PUT_LINE(' ');
    
    -- Iterate through each object type
    FOR idx IN v_types.FIRST .. v_types.LAST LOOP
        v_object_type := v_types(idx);
        
        DBMS_OUTPUT.PUT_LINE('Processing ' || v_object_type || ' objects...');
        
        -- Query all objects of this type
        FOR obj_rec IN (
            SELECT object_name
            FROM user_objects
            WHERE object_type = v_object_type
            AND object_name NOT LIKE 'BIN$%'  -- Skip recycle bin objects
            ORDER BY object_name
        ) LOOP
            drop_object(v_object_type, obj_rec.object_name);
        END LOOP;
    END LOOP;
    
    DBMS_OUTPUT.PUT_LINE(' ');
    DBMS_OUTPUT.PUT_LINE('========================================');
    DBMS_OUTPUT.PUT_LINE('Cleanup Summary:');
    DBMS_OUTPUT.PUT_LINE('  Objects dropped successfully: ' || v_success_count);
    DBMS_OUTPUT.PUT_LINE('  Objects failed to drop:       ' || v_error_count);
    DBMS_OUTPUT.PUT_LINE('========================================');
    
    IF v_error_count > 0 THEN
        DBMS_OUTPUT.PUT_LINE('WARNING: Some objects could not be dropped.');
        DBMS_OUTPUT.PUT_LINE('Check the errors above and retry if necessary.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('SUCCESS: All objects have been dropped!');
    END IF;
    
    -- Verify cleanup by checking remaining objects
    DBMS_OUTPUT.PUT_LINE(' ');
    DBMS_OUTPUT.PUT_LINE('Remaining objects in schema:');
    DECLARE
        v_remaining_count INTEGER;
    BEGIN
        SELECT COUNT(*) INTO v_remaining_count FROM user_objects;
        DBMS_OUTPUT.PUT_LINE('Total remaining objects: ' || v_remaining_count);
    END;
    
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('FATAL ERROR: ' || SQLERRM);
        RAISE;
END;
/
