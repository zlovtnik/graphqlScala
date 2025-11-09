#!/bin/bash
# Oracle Database Health Check Script

# Oracle environment variables
# ORACLE_HOME: Path to Oracle home directory (default: /opt/oracle/product/26ai/dbhomeFree)
# ORACLE_SID: Oracle System Identifier (default: FREE)
# These can be overridden via environment variables for custom installations or different Oracle versions.
export ORACLE_HOME=${ORACLE_HOME:-/opt/oracle/product/26ai/dbhomeFree}
export ORACLE_SID=${ORACLE_SID:-FREE}
export PATH=$ORACLE_HOME/bin:$PATH

# Check if database is up and accepting connections
sqlplus -s / as sysdba << EOF
   WHENEVER SQLERROR EXIT SQL.SQLCODE
   WHENEVER OSERROR EXIT FAILURE
   SET SERVEROUTPUT ON
   ALTER SESSION SET CONTAINER = FREEPDB1;
   DECLARE
     v_count NUMBER;
   BEGIN
     SELECT COUNT(*) INTO v_count FROM all_users WHERE username = 'SSFUSER';
     IF v_count > 0 THEN
       DBMS_OUTPUT.PUT_LINE('PDB is ready');
     ELSE
       RAISE_APPLICATION_ERROR(-20001, 'PDB not ready');
     END IF;
   END;
   /
   EXIT;
EOF

# Check exit code
if [ $? -eq 0 ]; then
    echo "Oracle Database is healthy"
    exit 0
else
    echo "Oracle Database is not ready"
    exit 1
fi