#!/bin/sh
# Postgres wrapper entrypoint for the prod overlay: fresh named volumes
# arrive root-owned, and the WAL archive dir must be writable by the
# postgres user before the stock entrypoint drops privileges. Then chain
# to the stock entrypoint untouched (same init, same gosu behavior).
set -eu
mkdir -p /wal
chown postgres:postgres /wal
exec /usr/local/bin/docker-entrypoint.sh "$@"
