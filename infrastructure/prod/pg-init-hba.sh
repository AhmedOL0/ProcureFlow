#!/bin/sh
# First-init hook (docker-entrypoint-initdb.d, runs once on empty pgdata):
# allow scram-sha-256 replication for the app user so pg_basebackup works
# for PITR base backups. Sourced, not executed: no exec bit needed.
# Container-network only in practice (no published DB port); the password
# travels by env, never in git.
echo "host replication procureflow all scram-sha-256" >> "$PGDATA/pg_hba.conf"
