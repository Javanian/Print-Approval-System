# HTTP API

All paths begin `/api`. GET `/csrf` returns `{ "token": "..." }` and establishes the session. Send `X-CSRF-TOKEN` and the same session cookie on every mutation. Fetch a fresh token after authentication. JSON is used except login and upload.

| Method and path                     | Input / result                                                   |
| ----------------------------------- | ---------------------------------------------------------------- |
| POST `/login`                       | URL-encoded `username=admin&password=...`; 204 or 401            |
| POST `/logout`                      | Ends session; 204                                                |
| GET `/admin/jobs`                   | Job list with latest state                                       |
| POST `/admin/jobs`                  | `{title}`; returns `{id}`                                        |
| GET `/admin/jobs/{id}/versions`     | Version history, latest first; no token or image bytes           |
| POST `/admin/jobs/{id}/versions`    | Multipart `specs` and `image`; returns `{id}`                    |
| GET `/admin/versions/{id}/image`    | Authenticated normalized PNG                                     |
| POST `/admin/versions/{id}/share`   | Rotates link; returns `{token}` once                             |
| DELETE `/admin/versions/{id}/share` | Revokes link                                                     |
| GET `/proof/{token}`                | Public version details and decision                              |
| GET `/proof/{token}/image`          | Private link's normalized PNG                                    |
| POST `/proof/{token}/decision`      | `{version:UUID, action:"APPROVED" or "REVISION", name, comment}` |

Every `/admin/**` endpoint requires the ADMIN role. Title/name max 120 characters; specifications/comments max 2000. Revision requires a nonblank comment. 400 means invalid data, 401 unauthenticated, 403 unauthorized or missing CSRF, 404 unavailable link/resource, 409 stale/already-decided state, and 413 excessive multipart size. The UI preserves input on failure and provides retry. Approval retry is idempotent for the same version, action, trimmed name and trimmed comment. Job creation and upload are not idempotent; after ambiguous network failures inspect history before resubmitting.
