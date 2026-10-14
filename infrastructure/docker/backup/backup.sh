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

# Optional encryption-at-rest: with BACKUP_ENCRYPT_KEY (or _FILE) set, the
# plaintext dump never survives the run — it is encrypted in place and only
# the .enc file is retained. Needs the backup image (openssl); the stock
# postgres image fails here loudly instead of writing plaintext silently.
if [ -n "${BACKUP_ENCRYPT_KEY_FILE:-}" ]; then
    BACKUP_ENCRYPT_KEY="$(cat "${BACKUP_ENCRYPT_KEY_FILE}")"
fi
if [ -n "${BACKUP_ENCRYPT_KEY:-}" ]; then
    command -v openssl >/dev/null 2>&1 || {
        echo "backup FAILED: BACKUP_ENCRYPT_KEY is set but openssl is missing" >&2
        rm -f "${FILE}"
        exit 1
    }
    openssl enc -aes-256-cbc -pbkdf2 -pass env:BACKUP_ENCRYPT_KEY \
        -in "${FILE}" -out "${FILE}.enc"
    rm -f "${FILE}"
    FILE="${FILE}.enc"
    SIZE="$(wc -c <"${FILE}")"
fi

# Prune oldest beyond retention (newest N survive; never delete the just-written file).
# The glob covers plaintext .dump and encrypted .dump.enc alike.
KEPT=0
for dump in $(ls -t "${OUT_DIR}"/procureflow-*.dump* 2>/dev/null); do
    KEPT=$((KEPT + 1))
    if [ "${KEPT}" -gt "${RETENTION}" ] && [ "${dump}" != "${FILE}" ]; then
        rm -f "${dump}"
    fi
done
ls -t "${OUT_DIR}"/procureflow-*.dump* | head -n "${RETENTION}" >/dev/null

echo "backup OK: ${FILE} (${SIZE} bytes, retention ${RETENTION})"

if [ "${1:-once}" = "loop" ]; then
    INTERVAL="${BACKUP_INTERVAL_SECONDS:-86400}"
    echo "backup sidecar sleeping ${INTERVAL}s until the next dump"
    while true; do
        sleep "${INTERVAL}"
        "$0" once
    done
fi
