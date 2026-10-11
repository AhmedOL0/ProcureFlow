#!/bin/sh
# ProcureFlow database backup: one pg_dump (custom format) per run into
# /backups, then prune to BACKUP_RETENTION newest dumps.
# Runs inside the postgres:16-alpine image, so pg_dump is always the exact
# server version. Connection comes from PGHOST/PGPORT/PGDATABASE/PGUSER/
# PGPASSWORD. Usage: backup.sh once | loop.
set -eu

OUT_DIR="${BACKUP_DIR:-/backups}"
RETENTION="${BACKUP_RETENTION:-7}"
STAMP="$(date -u +%Y%m%d-%H%M%S)"
FILE="${OUT_DIR}/procureflow-${STAMP}.dump"

mkdir -p "${OUT_DIR}"

pg_dump -Fc -f "${FILE}"
SIZE="$(wc -c <"${FILE}")"
if [ "${SIZE}" -lt 1024 ]; then
    echo "backup FAILED: dump suspiciously small (${SIZE} bytes): ${FILE}" >&2
    rm -f "${FILE}"
    exit 1
fi

# Prune oldest beyond retention (newest N survive; never delete the just-written file).
KEPT=0
for dump in $(ls -t "${OUT_DIR}"/procureflow-*.dump 2>/dev/null); do
    KEPT=$((KEPT + 1))
    if [ "${KEPT}" -gt "${RETENTION}" ] && [ "${dump}" != "${FILE}" ]; then
        rm -f "${dump}"
    fi
done
ls -t "${OUT_DIR}"/procureflow-*.dump | head -n "${RETENTION}" >/dev/null

echo "backup OK: ${FILE} (${SIZE} bytes, retention ${RETENTION})"

if [ "${1:-once}" = "loop" ]; then
    INTERVAL="${BACKUP_INTERVAL_SECONDS:-86400}"
    echo "backup sidecar sleeping ${INTERVAL}s until the next dump"
    while true; do
        sleep "${INTERVAL}"
        "$0" once
    done
fi
