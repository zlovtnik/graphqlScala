#!/bin/bash

# Helper script to verify database initialization
# Usage: ./verify-db.sh

set -e

echo "=== Verifying Database Initialization ==="

ORACLE_USER=${ORACLE_USER:-ssfuser}
ORACLE_PASSWORD=${ORACLE_PASSWORD:-ssfuser}
ORACLE_DB=${ORACLE_DB:-FREEPDB1}
ORACLE_USER_UPPER=$(printf '%s' "$ORACLE_USER" | tr '[:lower:]' '[:upper:]')

# Check if container is running
if ! docker ps --filter "name=oracle-free" --filter "status=running" -q | grep -q .; then
    echo "ERROR: Oracle container is not running"
    echo "Start it with: docker compose up -d"
    exit 1
fi

echo "✓ Oracle container is running"

# Check if user exists
echo ""
echo "Checking if user '$ORACLE_USER' exists..."
USER_EXISTS=$(docker exec oracle-free sqlplus -s / as sysdba <<EOF 2>/dev/null
SET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 1000
ALTER SESSION SET CONTAINER = $ORACLE_DB;
SELECT COUNT(*) FROM dba_users WHERE username = '${ORACLE_USER_UPPER}';
EXIT;
EOF
)

# Validate numeric output
if ! [[ "$USER_EXISTS" =~ ^[0-9]+$ ]]; then
  echo "ERROR: Unexpected sqlplus output: '$USER_EXISTS'"
  exit 1
fi

if [ "$USER_EXISTS" -eq 1 ]; then
    echo "✓ User '$ORACLE_USER' exists"
else
    echo "✗ User '$ORACLE_USER' does NOT exist"
    echo "  Waiting for initialization to complete..."
    exit 1
fi

# Check if users table exists
echo ""
echo "Checking if 'users' table exists..."
TABLE_EXISTS=$(docker exec oracle-free sqlplus -s $ORACLE_USER/$ORACLE_PASSWORD@$ORACLE_DB <<EOF 2>/dev/null
SET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 1000
SELECT COUNT(*) FROM user_tables WHERE table_name = 'USERS';
EXIT;
EOF
)

if [ "$TABLE_EXISTS" -eq 1 ]; then
    echo "✓ Table 'users' exists"
else
    echo "✗ Table 'users' does NOT exist"
    exit 1
fi

# Check if default admin user exists
echo ""
echo "Checking if default admin user exists..."
ADMIN_COUNT=$(docker exec oracle-free sqlplus -s $ORACLE_USER/$ORACLE_PASSWORD@$ORACLE_DB <<EOF 2>/dev/null
SET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 1000
SELECT COUNT(*) FROM users WHERE username = 'admin';
EXIT;
EOF
)

if [ "$ADMIN_COUNT" -eq 1 ]; then
    echo "✓ Default admin user exists"
    echo "  Credentials: admin / Admin@123"
else
    echo "⚠ No users found in users table"
    echo "  The table exists but is empty"
fi

# Check audit tables
echo ""
echo "Checking audit tables..."
for table in audit_login_attempts audit_sessions audit_error_log audit_dynamic_crud; do
    AUDIT_EXISTS=$(docker exec oracle-free sqlplus -s $ORACLE_USER/$ORACLE_PASSWORD@$ORACLE_DB <<EOF 2>/dev/null
SET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 1000
SELECT COUNT(*) FROM user_tables WHERE table_name = '$(echo "$table" | tr '[:lower:]' '[:upper:]')';
EXIT;
EOF
)
    if [ "$AUDIT_EXISTS" -eq 1 ]; then
        echo "✓ Table '$table' exists"
    else
        echo "⚠ Table '$table' does not exist (may be created by app)"
    fi
done

echo ""
echo "=== Database Verification Complete ==="
echo ""
echo "Summary:"
echo "- Oracle user created: ✓"
echo "- Main schema ready: ✓"
echo ""
echo "Next steps:"
echo "1. Check app logs: docker logs ssf-app"
echo "2. If app is running, access it: https://localhost:8443"
echo "3. MinIO console: http://localhost:9001"
