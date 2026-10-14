#!/bin/sh
# Physical base backup for point-in-time recovery: pg_basebackup (tar+gzip,
# WAL streaming) into /backups/base-<stamp>/, pruned to BACKUP_RETENTION
# newest bases. Pair with the WAL archive on /wal (prod postgres
# archive_command) — restore = latest base + WAL replay to any point.
# Connection from PGHOST/PGPORT/PGUSER/PGPASSWORD (superuser for replication).
# Usage: pitr-base.sh once.
set -eu

OUT_DIR="${BACKUP_DIR:-/backups}"
RETENTION="${BACKUP_RETENTION:-7}"
STAMP="$(date -u +%Y%m%d-%H%M%S)"
DEST="${OUT_DIR}/base-${STAMP}"

mkdir -p "${DEST}"

pg_basebackup -h "${PGHOST}" -p "${PGPORT:-5432}" -U "${PGUSER}" \
    -D "${DEST}" -Ft -z -P -X stream
echo "base backup OK: ${DEST} ($(du -sh "${DEST}" | cut -f1))"

# Prune oldest bases beyond retention, never the just-written one.
KEPT=0
for base in $(ls -dt "${OUT_DIR}"/base-* 2>/dev/null); do
    KEPT=$((KEPT + 1))
    if [ "${KEPT}" -gt "${RETENTION}" ] && [ "${base}" != "${DEST}" ]; then
        rm -rf "${base}"
    fi
done
