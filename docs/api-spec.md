# Hospital Operations Console — Local REST API

All endpoints are exposed by the embedded Javalin server, bound to
`127.0.0.1:8080` by default (overridable via `API_PORT` /
`API_BIND`).  Requests from non-loopback addresses are rejected at the
OS / firewall level in production; CORS allows only
`http://127.0.0.1` by origin.

## Authentication

| Property | Value |
|---|---|
| Scheme | Bearer token |
| Header | `Authorization: Bearer <raw-token>` |
| Issuance | `POST /api/auth/login` with `{username, password}` |
| Storage | Server persists the SHA-256 of the token in `api_tokens`; the raw value is only returned once |
| TTL | 24 hours from issuance |
| Revocation | Login revokes any prior token for the user; logout revokes all tokens for the user; deactivating a user revokes all their tokens |

If a request arrives with a token that has been revoked, expired, or whose
user has been deactivated, the server returns `401` with
`error.code = "UNAUTHORIZED"`.

## Rate limiting

A per-user token bucket permits **60 requests / minute / user**.
Exceeding the bucket returns `429` with `error.code = "RATE_LIMIT"`.
Tests override this to a far larger bucket via the
`rate.limit.max` system property.

## Pagination

All list endpoints accept `?page=N&pageSize=M`.  Bounds are enforced
server-side and return `400` when violated:

| Parameter | Range |
|---|---|
| `page` | `>= 1` (default `1`) |
| `pageSize` | `1..500` (default `50`) |

Invalid integers or out-of-range values produce
`error.code = "VALIDATION_ERROR"` with a `fields` map pointing at the
offending parameter.

## Sorting and field selection

Endpoints that return JSON lists also support:

- `sort=<field>` (prefix with `-` for descending — e.g. `sort=-createdAt`)
- `fields=a,b,c` to project only specific fields

## Object-level authorization

The server enforces per-resource ownership checks on endpoints that
return a single user's resource:

- `GET /api/exports/{id}` — only the user who initiated the export, or a
  `SYSTEM_ADMIN`, may read the job.  Cross-user access returns `403`.
- Review approve/reject/flag-conflict endpoints enforce "only the
  assigned (or second) reviewer" — see
  [Evaluation & Review endpoints](#evaluation--review).

## Roles

| Role | Summary |
|---|---|
| `SYSTEM_ADMIN` | Full configuration and user administration |
| `OPS_MANAGER` | Service-area, pickup-point, staffing, KPI ops |
| `REVIEWER` | Peer/expert scorecard reviews, re-reviews |
| `AUDITOR` | Read-only compliance access |
| `DATA_INTEGRATOR` | Consumes local APIs for other desktop modules |

`AUDITOR` is explicitly blocked from initiating exports
(`POST /api/exports` returns `403`).

## Endpoint catalog

### Auth

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/login` | none | Body: `{username,password}` |
| POST | `/api/auth/logout` | Bearer | Revokes all tokens for the caller |

### Users

| Method | Path | Required role |
|---|---|---|
| GET | `/api/users` | SYSTEM_ADMIN |
| POST | `/api/users` | SYSTEM_ADMIN |
| GET | `/api/users/{id}` | SYSTEM_ADMIN |
| PUT | `/api/users/{id}` | SYSTEM_ADMIN |
| DELETE | `/api/users/{id}` | SYSTEM_ADMIN (deactivates + revokes tokens) |

### Communities / Service areas / Pickup points

| Method | Path | Notes |
|---|---|---|
| CRUD | `/api/communities[/{id}]` | One active pickup point per community per day is enforced at pause/resume |
| CRUD | `/api/service-areas[/{id}]` | |
| CRUD | `/api/pickup-points[/{id}]` | `POST /api/pickup-points/{id}/pause`, `/resume`, `/api/pickup-points/match` |

### Beds

| Method | Path | Notes |
|---|---|---|
| GET/POST | `/api/beds` | list / create |
| GET/PUT/DELETE | `/api/beds/{id}` | `residentId` masked for non-admin |
| POST | `/api/beds/{id}/transition` | Enforces state-machine allowed transitions |
| GET | `/api/beds/{id}/history` | Paginated |

### Route imports (offline handheld evidence)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/route-imports` | multipart file upload (`.csv` or `.json`) |
| GET | `/api/route-imports` | paginated list |
| GET | `/api/route-imports/{id}` | job status |
| GET | `/api/route-imports/{id}/checkpoints` | paginated checkpoint list with masked coordinates |

Route imports are crash-safe: an interrupted commit phase resumes at
startup by re-reading the on-disk checkpoint sidecar and skipping rows
already persisted.

### Exports

| Method | Path | Notes |
|---|---|---|
| POST | `/api/exports` | Creates an export job; `AUDITOR` is rejected |
| GET | `/api/exports/{id}` | Owner OR `SYSTEM_ADMIN` only (403 otherwise) |

Exports write to the destination folder atomically: the worker produces
a `.part` temp file and renames it in-place only when the SHA-256
sidecar has been computed.  An interrupted job is retried on startup;
the `.part` remnant is deleted before retrying so no truncated artefact
ever ships.

### KPI / Evaluation / Review

| Method | Path | Notes |
|---|---|---|
| CRUD | `/api/kpis[/{id}]`, `/api/kpi-scores` | |
| CRUD | `/api/cycles[/{id}]`, `/api/cycles/{id}/templates`, `/api/cycles/{cycleId}/templates/{templateId}/metrics` | Template weights MUST total exactly 100.0 before any scorecard is created from it |
| CRUD | `/api/scorecards[/{id}]`, `/api/scorecards/{id}/responses`, `/api/scorecards/{id}/submit`, `/api/scorecards/{id}/recuse` | |
| CRUD | `/api/reviews[/{id}]`, `/api/reviews/{id}/approve`, `/reject`, `/flag-conflict`, `/assign-second` | Second reviewer must have the REVIEWER (or SYSTEM_ADMIN) role and be distinct from the original reviewer |
| CRUD | `/api/appeals[/{id}]`, `/api/appeals/{id}/resolve`, `/reject` | Appeals must be filed within 7 calendar days |

### System / scheduled jobs

| Method | Path | Notes |
|---|---|---|
| GET | `/api/health` | uptime, db status, version |
| GET | `/api/audit-trail` | SYSTEM_ADMIN or AUDITOR |
| GET | `/api/logs` | SYSTEM_ADMIN or AUDITOR |
| GET | `/api/jobs` | SYSTEM_ADMIN — lists registered scheduled jobs (BACKUP, ARCHIVE, CONSISTENCY_CHECK, REPORT) |
| POST | `/api/jobs/{id}/pause`, `/resume` | SYSTEM_ADMIN |

`REPORT` scheduled jobs execute through the same `ExportService`
pipeline as ad-hoc exports.  Each `scheduled_jobs.config_json` for a
`REPORT` must at minimum specify `entityType`; optional keys are
`format` (`EXCEL` / `PDF` / `CSV`), `destinationPath`, and
`filtersJson`.

## Error response format

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Human-readable message",
    "fields": {"pageSize": "pageSize must be <= 500 (got 600)"}
  }
}
```

| HTTP | `error.code` | Typical cause |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Malformed body / out-of-range pagination / constraint violation |
| 401 | `UNAUTHORIZED` | Missing, invalid, expired, or revoked token; user deactivated |
| 403 | `FORBIDDEN` | Role not permitted; cross-user resource access |
| 404 | `NOT_FOUND` | Resource does not exist |
| 409 | `CONFLICT` | State-machine violation (e.g. bed OCCUPIED → OUT_OF_SERVICE direct) |
| 429 | `RATE_LIMIT` | Per-user request budget exceeded |
| 500 | `INTERNAL_ERROR` | Unhandled exception (opaque by design) |
