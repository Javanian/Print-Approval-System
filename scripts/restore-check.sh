#!/usr/bin/env bash
set -euo pipefail
: "${DB_CONTAINER:?Set DB_CONTAINER to an isolated PostgreSQL container}"
: "${1:?Usage: scripts/restore-check.sh backup.dump print-approval-system_restore_unique_name}"
: "${2:?Supply a NEW disposable database name beginning print-approval-system_restore_}"
[[ "$2" =~ ^print-approval-system_restore_[a-z0-9_]+$ ]] || { echo 'Invalid disposable database name.' >&2; exit 1; }
# createdb refuses existing names. Never drops or replaces any database.
docker exec "$DB_CONTAINER" createdb -U "${DB_USER:-postgres}" "$2"
docker exec -i "$DB_CONTAINER" pg_restore -U "${DB_USER:-postgres}" -d "$2" --no-owner --no-acl --exit-on-error < "$1"
docker exec -i "$DB_CONTAINER" psql -X -v ON_ERROR_STOP=1 -U "${DB_USER:-postgres}" -d "$2" <<'SQL'
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM versions WHERE encode(sha256(image),'hex')<>digest) THEN
  RAISE EXCEPTION 'Image digest mismatch';
 END IF;
 IF (SELECT used_bytes FROM storage_budget WHERE id=1)<>(SELECT coalesce(sum(octet_length(image)),0) FROM versions)
 OR (SELECT job_count FROM storage_budget WHERE id=1)<>(SELECT count(*) FROM jobs)
 OR (SELECT version_count FROM storage_budget WHERE id=1)<>(SELECT count(*) FROM versions) THEN
  RAISE EXCEPTION 'Storage accounting mismatch';
 END IF;
END $$;
SELECT count(*) AS restored_jobs FROM jobs;
SELECT count(*) AS restored_versions, count(*) FILTER(WHERE state='APPROVED') AS approvals FROM versions;
SQL
echo 'Restoration and all image digests/storage counters verified. Disposable database retained for inspection.'
