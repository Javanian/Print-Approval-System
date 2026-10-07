# Architecture and decisions

## ADR 001 — One deployable application

Angular compiles to static files served by a Spring Boot modular backend. The controller handles HTTP validation, the service owns transactional state rules, Spring Security handles authentication/CSRF, and JDBC makes locks and queries explicit. PostgreSQL stores both metadata and normalized images; Flyway owns schema history. One database backup therefore includes every approval asset. This trades database size for simple private storage and transactional durability; use object storage only after measured need.

## ADR 002 — Immutable version content

Each upload creates a new UUID and per-job sequence number. Specifications, normalized PNG bytes and SHA-256 digest cannot be modified through the API. Approval closes the job to later uploads. Pending predecessors become SUPERSEDED; revision decisions remain in history. The database job row is locked for upload, share rotation, revocation and decision. This serializes conflicting state changes across application instances. Identical repeated decisions return the original record; conflicting retries return 409. The database administrator remains trusted; this is not cryptographic tamper evidence.

## ADR 003 — Private capability links

A 256-bit cryptographically random token is returned only at link creation. Only its SHA-256 hash is stored. The browser receives it in a URL fragment, then uses it for same-origin API requests. No third-party fonts, analytics or scripts are loaded. API and image responses use no-store, referrer policy is no-referrer, and Spring Security blocks framing. Proxies must redact `/api/proof/*` paths; links remain bearer credentials and recipients can forward them. Revocation cannot erase already downloaded images.

## ADR 004 — One shop administrator

A BCrypt-hashed in-memory admin account derives from a required environment secret. Session cookies are HTTP-only, SameSite Strict and secure by default; CSRF protects every mutation, including public decisions. No CORS policy is opened. There is no self-signup, password recovery or role management. Sessions are process-local; restart logs the admin out. This is sufficient for a narrow single-shop pilot, not multi-tenant SaaS.

## ADR 005 — Upload normalization and limits

The server checks actual decoder format (PNG/JPEG), byte size and dimensions before decoding. Maximum 5 MB input and 16 million pixels; normalized PNG output is capped at 10 MB. Metadata is stripped through decode/re-encode. Images live in private bytea columns and require administrator authorization or a valid bearer link. SVG/PDF and filename-driven filesystem storage are deliberately excluded.

## Reproducible baseline

Spring Boot 3.5.16 parent pins its managed dependencies; Angular 21.2.25 and TypeScript 5.9.3 are exact dependencies with npm lockfile. Java 21 is compatible with the [Spring Boot 3.5 requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html); Angular 21 supports Node 24 and TypeScript 5.9 per the [official compatibility matrix](https://angular.dev/reference/versions). Versions were resolved from Maven Central and npm. PostgreSQL 17.11 and the Java 21.0.12.1 runtime include immutable image digests. PostgreSQL 17.11 was verified against the [official supported-version table](https://www.postgresql.org/support/versioning/). All application build/runtime container bases are pinned by digest. GitHub Actions references use major-version tags; pin audited action SHAs before a hardened production release.
