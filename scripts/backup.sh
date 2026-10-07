#!/usr/bin/env bash
set -euo pipefail
umask 077
: "${DB_CONTAINER:?Set DB_CONTAINER to the source PostgreSQL container}"
: "${1:?Usage: scripts/backup.sh /secure/path/backup.dump}"
test ! -e "$1" || { echo 'Refusing to overwrite an existing backup.' >&2; exit 1; }
task_temp=$(mktemp "${1}.partial.XXXXXX")
trap 'rm -f "$task_temp"' EXIT
docker exec "$DB_CONTAINER" pg_dump -U "${DB_USER:-postgres}" -d "${DB_NAME:-print-approval-system}" --format=custom --no-owner --no-acl > "$task_temp"
docker exec -i "$DB_CONTAINER" pg_restore --list < "$task_temp" > /dev/null
mv "$task_temp" "$1"
echo 'Backup created and archive structure verified. Keep it encrypted and access restricted.'
