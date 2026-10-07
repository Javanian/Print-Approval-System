# Verification evidence

Executed on 2026-10-07 in the isolated cloud container. No Windows workstation resources were used. Source and configuration are in this repository; no unrelated repository was changed.

| Check | Actual result |
| --- | --- |
| Spring Boot / PostgreSQL integration suite | **10 integration tests + 5 safeguard tests passed**, 0 failed, 0 skipped; PostgreSQL 17.11; Java 21 JDK |
| Angular production build | **Passed**, strict template checking; approximately 186 kB initial uncompressed bundle |
| TypeScript `tsc --noEmit` | **Passed** |
| Playwright on development server | **3 scenarios passed** |
| Multi-stage production Docker build | **Passed** with cloud proxy certificates mounted as temporary build secrets |
| Playwright on packaged production assets | **4 scenarios passed** using installed Chromium 151, production static assets and PostgreSQL 17.11; final contrast patch checked on the packaged JAR |
| Docker Compose interpolation/schema | **Passed** with explicitly supplied local secrets; missing secrets fail closed |
| Git diff whitespace check | **Passed** |
| Repository | User-created public `Javanian/Print-Approval-System`; visibility preserved |
| Exact-commit GitHub Actions CI | Awaiting verification after the first push; local checks do not imply remote success |
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

This is a tested narrow portfolio release, not verified production readiness. The user created the public repository and authorized publication; its visibility is unchanged. Remote CI must be checked against the pushed SHA.

Built-in safeguards now cover bounded login/public request throttling, image/request/concurrency limits, transactional storage quotas, safe error responses and sanitized application error logs. Fifteen backend checks cover both the workflow and safeguards. The backup/restore scripts were executed against a disposable PostgreSQL database: 58 jobs, 72 versions and 16 approvals restored, with all image digests and storage counters verified; overwrite/unsafe-target refusal was checked.

Focused axe-core WCAG A/AA checks cover login, work list, version detail, mobile customer review and saved approval. Keyboard-only login and customer approval are exercised. This is not a full accessibility certification or assistive-technology audit.

The dependency scan found serious issues in the original baseline. Spring Boot was upgraded to 4.1.1 with patched Tomcat 11.0.25, Jackson 2.21.7 and Jackson 3.1.7 BOM overrides. The Angular CLI's transitive SDK was pinned to patched 1.31.0. Full npm audit now reports zero vulnerabilities. The patched production image scan reports **0 critical, 0 high, 40 medium and 4 low** findings, without suppressions. One medium base-OS package finding has a published fix; the remainder require continued base-image monitoring. See [image scan](evidence/image-scan.json) and [npm audit](evidence/npm-audit.json).

Real TLS/domain configuration, production secrets, proxy log redaction/edge limits, monitoring, physical disk capacity, encrypted backup scheduling/recovery objectives, retention/support decisions and customer validation remain operator prerequisites. No public deployment, paid service, load test or independent security audit was performed.

## Additional findings fixed

- Transactional storage reservation fails before superseding the previous proof when quota is exhausted.
- Oversized/chunked mutation requests fail with bounded fixed responses; forwarded IP headers cannot bypass application throttling.
- Production CSS loading now avoids Angular's inline onload handler, which CSP correctly blocked. Browser tests assert actual stylesheet application before exercising the workflow. Muted text colors were darkened after axe detected inadequate footer contrast.

## Visual redesign

The refreshed UI uses a charcoal header, paper-gray workspace, compact job rows, distinct status labels, restrained lime accents and a contrasting primary customer decision. Mobile review gives artwork full width and stacks specifications and decision controls. Actual desktop/mobile screenshots were captured from a separate disposable database using fictional café content. Four end-to-end scenarios pass, including all five focused axe scans and keyboard-only approval.
