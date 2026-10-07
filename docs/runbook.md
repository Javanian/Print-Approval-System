# Runbook

## Before an external pilot

No production deployment is included. Configure TLS at an approved reverse proxy, set `COOKIE_SECURE=true`, keep the database on a private network and bind the app behind the proxy. The application now enforces process-local request/login limits, upload concurrency and storage quotas described below. Configure matching limits at the production edge, redact capability URLs and cookies from proxy logs, monitor capacity, establish a retention policy and support contact, and schedule encrypted backups/restoration drills. Repeat vulnerability and accessibility checks in the deployed environment and conduct real-user review. No security audit, load test, multi-shop isolation, high availability, or legal certification has been completed.

Secrets are provided only through the deployment environment. Rotate `ADMIN_PASSWORD` and restart to rotate admin access; a restart invalidates existing sessions. Rotate database credentials separately. Avoid environment dumps in support logs. Do not use the illustrative local/CI passwords externally.

## Built-in limits and their operating boundaries

- Login: 10 attempts per remote IP per fixed minute. Public proof/CSRF endpoints: 120 requests per remote IP per fixed minute. Both share a global limit of 600 accepted requests/minute. Entries are bounded and cleared each minute; 429 includes `Retry-After: 60`. Counters reset on process restart and are per instance. Behind a proxy, clients share the proxy address unless a separately reviewed trusted-proxy setup is used. Spoofed `X-Forwarded-For` is ignored.
- Upload concurrency: two requests at once per process; excess returns 503 with a five-second retry hint. JSON/form bodies are capped at 16 KiB, multipart requests at 6,000,000 bytes, image files at 5,000,000 bytes, decoded images at 16 million pixels, and normalized PNGs at 10,000,000 bytes. Chunked mutation requests receive 411; clients must send Content-Length. Tomcat connection/thread/header limits and a 20-minute session timeout further bound resources.
- Persistent storage defaults: 1 GiB of normalized image payload, 5,000 jobs, 20,000 versions total and 100 versions/job. Configure `STORAGE_MAX_BYTES`, `STORAGE_MAX_JOBS`, and `STORAGE_MAX_VERSIONS` with measured capacity. PostgreSQL serializes storage reservations transactionally; rejected uploads return 507 and do not supersede existing proofs. These are application payload limits, not physical disk/WAL/index/backup limits. Do not delete rows or edit counters manually. There is no automatic retention deletion of approval records.
- Application access logging is disabled. HTTP errors contain fixed messages; unexpected errors log only exception category/status, without request paths, exception text, SQL values, tokens, cookies or bodies. Keep upstream proxy logs redacted and restrict database logs independently.

## Backups and restoration

Use an operator-controlled encrypted destination. `docker compose exec -T db pg_dump -U postgres -Fc print-approval-system > backup.dump` includes images and approval history. Restore into a separate empty database with `pg_restore`; verify a job, image hash and approved record before considering recovery complete. Backups are sensitive customer data. No destructive restore command is automated.

Executable procedures (PostgreSQL tools run inside the selected container):

```sh
DB_CONTAINER=your-postgres-container scripts/backup.sh /secure/encrypted-volume/backup.dump
DB_CONTAINER=isolated-postgres-container scripts/restore-check.sh /secure/encrypted-volume/backup.dump print-approval-system_restore_drill_001
```

The backup script uses restrictive permissions, validates archive structure and refuses to replace a file. The restore script permits only a new `print-approval-system_restore_*` database, never drops a database, and checks every restored image digest plus job/version/byte accounting. It retains the database for inspection; operators handle deliberate cleanup separately. Encrypt the storage destination: the script does not supply encryption keys or encryption itself. `DB_USER` and `DB_NAME` optionally select the database role/source database. A disposable restore was executed successfully during cloud QA, including refusal checks; production scheduling and recovery objectives remain operator decisions.

## Failure recovery

- Unavailable review link: admin creates a replacement link for the current pending version.
- Revision: admin uploads corrected artwork/specifications and shares its new link.
- Approved error: create a new job; never edit the previous record.
- Restart: sign in again; durable proofs stay in PostgreSQL.
- Migration failure: stop rollout, inspect Flyway/database logs and restore a verified backup if necessary; do not edit an applied migration.
- Database unavailable: the UI shows a recoverable error; retry once database health is restored. After upload/create uncertainty check history before resubmission.

## CI and releases

`Verify` runs PostgreSQL integration tests, strict Angular compilation/type checks, Chromium end-to-end tests and a Docker build. Reports are attached to the exact run. On GitHub verify that the run's head SHA matches the pushed commit; local checks do not prove remote CI success.

The manual container workflow produces a downloadable image archive tagged by commit SHA. It needs read-only repository permissions and does not push a registry image or deploy. Run CI successfully on that SHA before using an archive. GitHub-hosted runner use may consume private-repository minutes; obtain approval before incurring paid usage. No paid service has been provisioned here.

## Builds behind an enterprise proxy

The Dockerfile optionally accepts BuildKit secrets named `maven_settings` (Maven proxy settings), `java_cacerts` (Java truststore), and `node_cacerts` (PEM certificate bundle). Supply these with `docker build --secret id=...,src=...`; they are mounted only during dependency installation and are not copied into the image. The cloud QA build required these certificates, standard HTTP/HTTPS proxy build arguments, and resolution of the cloud proxy host. Ordinary direct-network builds need none of these overrides. Never disable TLS certificate verification.
