# Verification evidence

Executed on 2026-10-07 in the isolated cloud container. No Windows workstation resources were used. Source and configuration are in this repository; no unrelated repository was changed.

| Check | Actual result |
| --- | --- |
| Spring Boot / PostgreSQL integration suite | **9 tests passed**, 0 failed, 0 skipped; PostgreSQL 17.11; Java 21 JDK |
| Angular production build | **Passed**, strict template checking; approximately 173 kB initial uncompressed bundle |
| TypeScript `tsc --noEmit` | **Passed** |
| Playwright on development server | **3 scenarios passed** |
| Multi-stage production Docker build | **Passed** with cloud proxy certificates mounted as temporary build secrets |
| Playwright on production image | **3 scenarios passed** using installed Chromium 151, production static assets, Java 21.0.12.1 and PostgreSQL 17.11 |
| Docker Compose interpolation/schema | **Passed** with explicitly supplied local secrets; missing secrets fail closed |
| Git diff whitespace check | **Passed** |
| GitHub repository creation / push | **Blocked**: `gh api user` and `gh repo create Javanian/printproof --private` return `Forbidden` |
| Exact-commit GitHub Actions CI | **Not run**: no new remote repository could be created |
| Public deployment | **Not performed** |

## What the tests prove

The integration suite exercises real PostgreSQL and Flyway: immutable approval, identical-decision retries, concurrent competing decisions, concurrent upload versus approval, revision history, superseded and mismatched version rejection, revocation/rotation, administrator and file authorization, CSRF for admin/public mutations, actual password login, forged uploads, excessive dimensions and private image cache headers.

The Chromium scenarios exercise the actual Angular application: a mobile customer approves and reloads a printable record; an administrator cannot replace approved content; a customer requests changes, follows newer links in the same tab and recovers from superseded/revoked links; invalid credentials, forged uploads and an interrupted request produce recoverable errors. A fictional café menu is the checked-in test fixture. No customer data is used.

[Backend test report](evidence/backend-tests.txt) · [Mobile approval screenshot](evidence/approved-mobile.png) · [Print-layout screenshot](evidence/approved-print.png)

The local Playwright HTML report is generated at `frontend/playwright-report/index.html` and is excluded from source control. CI will upload its own report, screenshots and failure traces under the run's commit SHA.

## Defects found and fixed during QA

- A password encoder prefix mismatch prevented real login; fixed and covered by successful/failed login tests.
- Switching fragment-based review links within the same tab initially retained the previous proof; navigation now reloads the selected proof, covered by browser tests.
- Admin printing now excludes unapproved versions so the approval summary does not mix previous content with the approved proof.

## Limits and launch blockers

This is a tested narrow portfolio release, not verified production readiness. Repository publication requires authentication that can create a private repository in Javanian's account; the connected GitHub app verified that account but exposes no creation operation. Remote CI must then be checked against the pushed SHA.

Before external use, complete the runbook's TLS, rate limiting, backup/restore, retention, resource quota, dependency scanning and operational review. No paid service, production deployment, load test, security audit or real-customer usability validation was performed. Market demand remains a hypothesis.
