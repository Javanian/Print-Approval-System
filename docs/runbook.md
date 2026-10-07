# Runbook

## Before an external pilot

No production deployment is included. Configure TLS at an approved reverse proxy, set `COOKIE_SECURE=true`, keep the database on a private network and bind the app behind the proxy. Enforce request/login rate limits, redact capability URLs and cookies from access logs, set resource/storage quotas, patch images/dependencies and scan them, establish a retention policy and support contact, and verify backup restoration. Conduct accessibility and real-user review. No security audit, load test, multi-shop isolation, high availability, or legal certification has been completed.

Secrets are provided only through the deployment environment. Rotate `ADMIN_PASSWORD` and restart to rotate admin access; a restart invalidates existing sessions. Rotate database credentials separately. Avoid environment dumps in support logs. Do not use the illustrative local/CI passwords externally.

## Backups and restoration

Use an operator-controlled encrypted destination. `docker compose exec -T db pg_dump -U postgres -Fc printproof > backup.dump` includes images and approval history. Restore into a separate empty database with `pg_restore`; verify a job, image hash and approved record before considering recovery complete. Backups are sensitive customer data. No destructive restore command is automated.

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
