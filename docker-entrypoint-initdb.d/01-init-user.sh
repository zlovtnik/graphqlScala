#!/bin/bash

# Wait for Oracle to be ready
while true; do
  if $ORACLE_HOME/bin/sqlplus -version >/dev/null 2>&1; then
    break
  fi
  sleep 5
done

# Create application user and tablespace
$ORACLE_HOME/bin/sqlplus -s / as sysdba <<EOF
-- Create tablespace
CREATE TABLESPACE ssfspace DATAFILE '/opt/oracle/oradata/ssfdata.dbf' SIZE 100M AUTOEXTEND ON NEXT 10M;

-- Create user
CREATE USER ssfuser IDENTIFIED BY ${DB_USER_PASSWORD:?DB_USER_PASSWORD environment variable must be set} DEFAULT TABLESPACE ssfspace;

-- Grant privileges
GRANT CREATE SESSION TO ssfuser;
GRANT CREATE TABLE TO ssfuser;
GRANT CREATE SEQUENCE TO ssfuser;
GRANT CREATE INDEX TO ssfuser;
GRANT UNLIMITED TABLESPACE TO ssfuser;

-- Create necessary tables
exit;
EOF
