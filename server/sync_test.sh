#!/usr/bin/env bash
set -e
D=/var/lib/syncthing/studyclock
STAMP="$(date -u +%FT%TZ)"
sudo -n bash -c "printf 'syncthing roundtrip %s\n' '$STAMP' > $D/sync-test.txt; chown syncthing:syncthing $D/sync-test.txt"
ls -l "$D"
