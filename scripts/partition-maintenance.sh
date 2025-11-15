#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
SQL_SCRIPT="${ROOT_DIR}/db/migration/R__partition_rollover.sql"
SQLPLUS_BIN=${SQLPLUS_BIN:-sqlplus}
ORACLE_CONNECT_STRING=${ORACLE_CONNECT_STRING:-//${ORACLE_HOST:-localhost}:${ORACLE_PORT:-1521}/${ORACLE_DB:-FREEPDB1}}
ORACLE_USER=${ORACLE_USER:-ssfuser}
ORACLE_PASSWORD=${ORACLE_PASSWORD:-ssfuser}
ESCAPED_ORACLE_PASSWORD=${ORACLE_PASSWORD//"/""}
METRICS_FILE=${PARTITION_METRICS_FILE:-${ROOT_DIR}/metrics/partition-maintenance.prom}
mkdir -p "$(dirname "${METRICS_FILE}")"
START_EPOCH=$(date +%s)
LOG_FILE=$(mktemp)

${SQLPLUS_BIN} -s /nolog <<SQL | tee "${LOG_FILE}".out
CONNECT ${ORACLE_USER}/"${ESCAPED_ORACLE_PASSWORD}"@${ORACLE_CONNECT_STRING}
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET DEFINE OFF FEEDBACK OFF TERMOUT ON
@@db/migration/R__partition_rollover.sql
EXIT
SQL
STATUS=${PIPESTATUS[0]}
END_EPOCH=$(date +%s)
DURATION=$((END_EPOCH - START_EPOCH))

cat >"${METRICS_FILE}" <<EOF
partition_maintenance_duration_seconds ${DURATION}
partition_maintenance_status{job="partition-rollover"} $([[ ${STATUS} -eq 0 ]] && echo 1 || echo 0)
partition_maintenance_last_run_epoch_seconds ${END_EPOCH}
EOF


# sqlplus reads credentials from stdin, so no temporary credential files are created.

if [[ ${STATUS} -ne 0 ]]; then
  echo "Partition maintenance failed" >&2
  exit ${STATUS}
fi

echo "Partition maintenance completed in ${DURATION}s"
