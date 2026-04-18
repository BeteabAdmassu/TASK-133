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

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/health` | none | uptime, db status, version |
| GET | `/api/audit-trail` | SYSTEM_ADMIN, AUDITOR | paginated |
| GET | `/api/logs` | SYSTEM_ADMIN, AUDITOR | paginated |
| GET | `/api/jobs` | SYSTEM_ADMIN | list scheduled jobs |
| GET | `/api/jobs/{id}` | SYSTEM_ADMIN | fetch one |
| POST | `/api/jobs` | SYSTEM_ADMIN | create — body below |
| PUT | `/api/jobs/{id}` | SYSTEM_ADMIN | partial update (cron/timeout/status/configJson) |
| DELETE | `/api/jobs/{id}` | SYSTEM_ADMIN | unregisters from Quartz + removes row |
| POST | `/api/jobs/{id}/pause`, `/resume` | SYSTEM_ADMIN | pause/resume trigger |

Create body:

```json
{
  "jobType":         "REPORT",                  // BACKUP | ARCHIVE | CONSISTENCY_CHECK | REPORT
  "cronExpression":  "0 5 3 * * ?",             // Quartz 7-field cron, validated server-side
  "timeoutSeconds":  600,                        // 0..86400
  "status":          "ACTIVE",                  // ACTIVE | PAUSED, default ACTIVE
  "configJson": {
    "entityType":      "COMMUNITIES",           // REPORT: required; one of the export entity types
    "format":          "EXCEL",                 // EXCEL | PDF | CSV, default EXCEL
    "destinationPath": "/var/reports",          // optional
    "filtersJson":     "{...}"                  // optional
  }
}
```

Validation errors (400 `VALIDATION_ERROR`) include
`error.fields.cronExpression`, `error.fields.jobType`,
`error.fields.status`, `error.fields.timeoutSeconds`,
`error.fields.configJson`, and `error.fields["configJson.entityType"]` /
`error.fields["configJson.format"]` when a REPORT's config is malformed.

REPORT jobs execute through the same `ExportService` pipeline as ad-hoc
exports, meaning atomic `.part` → rename, SHA-256 sidecar, and
crash-safe resume.

### Updates (offline signed `.msi` installer + rollback)

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/updates/packages` | SYSTEM_ADMIN | enumerates `${updater.dir}/incoming/*` |
| POST | `/api/updates/packages/{name}/verify` | SYSTEM_ADMIN | Ed25519 signature + installer SHA-256 check (no installer is launched) |
| POST | `/api/updates/packages/{name}/apply` | SYSTEM_ADMIN | verify → promote files → `msiexec /i` → record exit code + log |
| POST | `/api/updates/rollback` | SYSTEM_ADMIN | one-click rollback: `msiexec /x` current product → restore backup → `msiexec /i` previous |
| GET | `/api/updates/history` | SYSTEM_ADMIN, AUDITOR | recent rows from `update_history` (includes `exitCode`, `logPath`, `installerType`) |
| GET | `/api/updates/current` | SYSTEM_ADMIN, AUDITOR | currently-installed row or `null` |

#### Manifest schema (`manifest.json`)

```json
{
  "version":          "1.2.0",
  "installerType":    "MSI",
  "installerFile":    "eaglepoint-1.2.0.msi",
  "payloadFilename":  "eaglepoint-1.2.0.msi",
  "payloadSha256":    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "payloadSize":      18234880,
  "productCode":      "{12345678-1234-1234-1234-1234567890AB}",
  "upgradeCode":      "{ABCDEF01-2345-6789-ABCD-EF0123456789}",
  "installArgs":      ["INSTALLDIR=C:\\EaglePoint", "ENVIRONMENT=prod"],
  "signingKeyId":     "eaglepoint-release-2026"
}
```

Server-side validation:

- `version`, `installerFile` (when `installerType=MSI`), `payloadSha256` are required.
- `installerType` — **production strict-MSI** (default): must be `MSI`.
  The parser also recognises `NONE` (legacy payload-only packages), but
  `apply` will reject `NONE` with `MANIFEST_INVALID` unless the
  operator has explicitly opted in by setting
  `-Dupdater.allow.non.msi=true` or `UPDATER_ALLOW_NON_MSI=true` (dev/CI
  only — never set in the production container). Any other value
  (e.g. `EXE`) is rejected outright.
- `productCode` must be a Windows Installer GUID (braced or unbraced).
- `installArgs` entries must match `KEY=VALUE`, keys upper-snake-case, values must not contain
  `& | ; < > $` backtick CR LF; each entry capped at 256 chars.
- `installerType=MSI` without `installerFile` → `400 VALIDATION_ERROR`.

#### Response body (successful apply)

```json
{
  "update": {
    "id":              17,
    "action":          "INSTALLED",
    "status":          "SUCCESS",
    "packageName":     "eaglepoint-1.2.0",
    "toVersion":       "1.2.0",
    "fromVersion":     "1.1.0",
    "installerType":   "MSI",
    "exitCode":        0,
    "logPath":         "/app/data/updater/logs/eaglepoint-1.2.0-install-20260417-140522.log",
    "signatureStatus": "VALID",
    "installedPath":   "/app/data/updater/installed/eaglepoint-1.2.0"
  }
}
```

#### Strict-MSI enforcement

Production apply is **strict-MSI**: a manifest whose `installerType` is
anything other than `MSI` (including missing / legacy payload-only
packages) is rejected with `VALIDATION_ERROR` / `MANIFEST_INVALID`
**before any installer command runs**.  The legacy payload-only path is
only reachable with an explicit operator override:

```
-Dupdater.allow.non.msi=true
UPDATER_ALLOW_NON_MSI=true
```

The override is intended for CI / dev only.  Production `docker-compose.yml`
does not set it, so every live container starts strict.

#### Failure reason categories (returned as `VALIDATION_ERROR` / `CONFLICT`)

| Reason | When |
|---|---|
| `SIGNATURE_INVALID` | Ed25519 signature or binary SHA-256 does not match |
| `SIGNATURE_UNTRUSTED` | No trust key is loaded |
| `MANIFEST_INVALID` | Required installer field missing, bad GUID, unsafe installArg, non-MSI installerType under strict mode |
| `INSTALLER_EXECUTION_FAILED` | `msiexec` returned non-zero; log path and recoveryState recorded on the history row |
| `ROLLBACK_TARGET_MISSING` | No prior INSTALLED row, or prior backup is gone from disk |

#### Post-failure recovery state

When filesystem promotion succeeds but the installer itself fails, the
updater **auto-reverts** the filesystem: it deletes the half-installed
tree and moves the previous `installed/` directory back from
`backups/`.  The `recoveryState` column on the failed `update_history`
row (migration `V5__update_history_recovery_state.sql`) reflects the
outcome:

| Value | Meaning | Notification severity |
|---|---|---|
| `AUTO_REVERTED` | Previous payload restored — no operator action required. | `WARN` |
| `NEEDS_MANUAL_RECOVERY` | Auto-revert itself failed — operator must reconcile `installed_path` and `backup_path` manually. | `ERROR` |
| `null` | No revert attempted (success, early rejection, or rollback row). | n/a |

The `ValidationException.message` / `error.message` on the API response
surfaces the recovery state so callers can decide whether to retry:
e.g. `"INSTALLER_EXECUTION_FAILED: exit=1603 recoveryState=AUTO_REVERTED"`.

#### Trust material

Loaded (first match wins) from:

1. `UPDATER_PUBLIC_KEY` env var (base64 X.509 SPKI)
2. System property `updater.public.key`
3. `UPDATER_PUBLIC_KEY_FILE` env var / `updater.public.key.file`
4. `data/updater/trust.pem.pub`

If no key is loaded, every verify call returns
`{"signatureStatus":"UNTRUSTED","valid":false}` and `apply` refuses to
run.  Apply, rollback, and reject events write to
`update_history` (see migrations `V3__update_history.sql` +
`V4__update_history_installer_cols.sql`) and emit audit events under
`entityType=UpdatePackage` with actions `UPDATE_APPLIED`,
`UPDATE_REJECTED`, `UPDATE_FAILED`, `UPDATE_ROLLBACK`,
`UPDATE_ROLLBACK_FAILED`.

#### Installer execution

The server resolves an {@code InstallerExecutor} at startup:

- `installer.mode=test` sysprop / `UPDATER_INSTALLER_MODE=test` env var
  → deterministic in-process executor used by CI and dev hosts.
- Non-Windows OS → same deterministic executor (the docker image never
  shells out to `msiexec`).
- Otherwise → `msiexec.exe /i &lt;msi&gt; /qn /norestart /l*v &lt;log&gt;`
  for apply, `msiexec.exe /x &lt;productCode&gt;` for uninstall.

Installer logs are written to `${updater.dir}/logs/{package}-{action}-{ts}.log`.

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
