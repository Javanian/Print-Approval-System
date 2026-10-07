# Five-minute demo

Use only a local, disposable environment. No startup seed or fixture switch exists, so production cannot silently publish demo content.

1. Sign in and create `Olive Café · takeaway menus`.
2. Enter `A5, 250 gsm uncoated, 100 copies, double-sided, matte finish` and upload a JPG/PNG you own.
3. Create a review link. Open it in a private browser window or mobile viewport.
4. Enter `Alex Customer`, request a revision with `Please correct the phone number.`
5. Upload a corrected preview as version 2 and create its link. The old decision remains visible in history.
6. Check the specifications, enter a name, check the approval confirmation and approve. Reload to demonstrate the saved record, then print the approval summary.
7. Return to the administrator: upload is unavailable for the approved job. Revoke its review link and confirm the link becomes unavailable.

Browser test data is fictional and generated only when tests are explicitly run. Use a dedicated test database and delete its volume after evaluation if desired; do not delete production volumes.

## Reproduce the screenshots

Start the production image against a **new disposable database**, install frontend dependencies, and run:

```sh
DEMO_URL=http://localhost:8085 DEMO_CONFIRM_DISPOSABLE=YES ADMIN_PASSWORD=your-local-admin-password node scripts/capture-demo.mjs
```

The script refuses non-loopback URLs and requires explicit disposable confirmation. It creates fictional café jobs through the real UI and captures desktop/mobile screens. It must not target a production or reused demo database. `CHROMIUM_PATH` optionally selects a locally installed Chromium.
