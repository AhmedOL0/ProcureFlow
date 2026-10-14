#!/bin/sh
# Edge entrypoint: ensure a certificate pair exists, then run nginx.
# First boot mints a 390-day self-signed pair (snakeoil, local only);
# replace /tls/edge.crt + /tls/edge.key with ACME-issued files and
# restart for anything shared (see infrastructure/prod/README.md).
set -eu
if [ ! -f /tls/edge.crt ] || [ ! -f /tls/edge.key ]; then
  echo "edge: minting throwaway self-signed certificate (local only)"
  openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout /tls/edge.key -out /tls/edge.crt -days 390 \
    -subj "/CN=procureflow.local" \
    -addext "subjectAltName=DNS:localhost,DNS:procureflow.local,IP:127.0.0.1" 2>/dev/null
fi
exec nginx -g "daemon off;"
