#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
SQL_SCRIPT="${ROOT_DIR}/db/migration/R__partition_rollover.sql"
SQLPLUS_BIN=${SQLPLUS_BIN:-sqlplus}
ORACLE_CONNECT_STRING=${ORACLE_CONNECT_STRING:-//${ORACLE_HOST:-localhost}:${ORACLE_PORT:-1521}/${ORACLE_DB:-FREEPDB1}}
ORACLE_USER=${ORACLE_USER:-APP_USER}
DEFAULT_PASSWORD_FILE="${ROOT_DIR}/.secrets/oracle-password"
PASSWORD_FILE="${ORACLE_PASSWORD_FILE:-}"
if [[ -z "${PASSWORD_FILE}" && -f "${DEFAULT_PASSWORD_FILE}" ]]; then
  PASSWORD_FILE="${DEFAULT_PASSWORD_FILE}"
fi

if [[ -z "${PASSWORD_FILE}" ]]; then
  echo "ERROR: ORACLE_PASSWORD_FILE must point to a chmod 600 file containing the database password"
  echo "       (default location: ${DEFAULT_PASSWORD_FILE})"
  exit 1
fi

if [[ ! -f "${PASSWORD_FILE}" ]]; then
  echo "ERROR: Oracle password file '${PASSWORD_FILE}' not found"
  exit 1
fi

if stat -f %OLp "${PASSWORD_FILE}" >/dev/null 2>&1; then
  PASSWORD_PERMS=$(stat -f %OLp "${PASSWORD_FILE}")
else
  PASSWORD_PERMS=$(stat -c %a "${PASSWORD_FILE}")
fi

if (( 8#${PASSWORD_PERMS} & 077 )); then
  echo "ERROR: Oracle password file permissions must be 600 or more restrictive (no group or other permissions)"
  exit 1
fi

ORACLE_PASSWORD=$(tr -d '\r\n' <"${PASSWORD_FILE}")

if [[ -z "${ORACLE_PASSWORD}" ]]; then
  echo "ERROR: Oracle password file '${PASSWORD_FILE}' is empty"
  exit 1
fi

# Reject passwords containing problematic characters (consistency with 01-init-user.sh)
if [[ "${ORACLE_PASSWORD}" == *'"'* ]]; then
  echo "ERROR: Oracle password cannot contain double quote characters"
  exit 1
fi

if [[ "${ORACLE_PASSWORD}" == *"'"* ]]; then
  echo "ERROR: Oracle password cannot contain single quote characters"
  exit 1
fi

if [[ "${ORACLE_PASSWORD}" == *"/"* || "${ORACLE_PASSWORD}" == *"@"* ]]; then
  echo "ERROR: Oracle password cannot contain '/' or '@' characters when using CONNECT syntax"
  exit 1
fi

METRICS_FILE=${PARTITION_METRICS_FILE:-${ROOT_DIR}/metrics/partition-maintenance.prom}
mkdir -p "$(dirname "${METRICS_FILE}")"
START_EPOCH=$(date +%s)
LOG_FILE=$(mktemp)

# Save debug state to restore it later (prevents logging of credentials)
SET_DEBUG_STATE=$(set +o | grep xtrace || true)
set +x

{
  printf 'CONNECT %s/%s@%s\n' "${ORACLE_USER}" "${ORACLE_PASSWORD}" "${ORACLE_CONNECT_STRING}"
  printf 'WHENEVER SQLERROR EXIT SQL.SQLCODE\n'
  printf 'SET DEFINE OFF FEEDBACK OFF TERMOUT ON\n'
  printf '@@db/migration/R__partition_rollover.sql\n'
  printf 'EXIT\n'
} | ${SQLPLUS_BIN} -s /nolog > "${LOG_FILE}".out 2>&1
STATUS=$?

# Restore debug state if it was previously enabled
if [[ "$SET_DEBUG_STATE" == *"on" ]]; then
  set -x
fi

END_EPOCH=$(date +%s)
DURATION=$((END_EPOCH - START_EPOCH))

# Critical: Clear the password from memory immediately
unset ORACLE_PASSWORD


cat >"${METRICS_FILE}" <<EOF
partition_maintenance_duration_seconds ${DURATION}
partition_maintenance_status{job="partition-rollover"} $([[ ${STATUS} -eq 0 ]] && echo 1 || echo 0)
partition_maintenance_last_run_epoch_seconds ${END_EPOCH}
EOF

if [[ ${STATUS} -ne 0 ]]; then
  echo "Partition maintenance failed" >&2
  cat "${LOG_FILE}".out 2>/dev/null | grep -v 'Connected\|User entered\|Disconnected' >&2 || true
  rm -f "${LOG_FILE}".out
  exit ${STATUS}
fi

cat "${LOG_FILE}".out 2>/dev/null | grep -v 'Connected\|User entered\|Disconnected' || true
rm -f "${LOG_FILE}".out
echo "Partition maintenance completed in ${DURATION}s"
