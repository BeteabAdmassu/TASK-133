# Audit Report 1 — Fix Check (Static)

## 1. Purpose
- This is a follow-up static fix check against findings listed in `.tmp/audit_report-1.md`.
- Scope here is only closure status for previously reported material issues.

## 2. Baseline Findings Checked
- High: updater allowed non-MSI apply path in production.
- Medium: updater integration tests had non-deterministic/loose assertions.
- Medium: installer-failure state handling could leave ambiguous promoted filesystem state.
- Minor doc consistency notes: update route-role comment and API-spec strict-MSI wording.

## 3. Fix Status

### 3.1 High — Non-MSI production apply path
- Status: **Resolved**
- Evidence:
  - Strict-MSI default and explicit dev/CI override gate: `repo/app/src/main/java/com/eaglepoint/console/service/UpdateService.java:90`, `repo/app/src/main/java/com/eaglepoint/console/service/UpdateService.java:179`
  - Rejection of `NONE` unless override enabled: `repo/app/src/main/java/com/eaglepoint/console/service/UpdateService.java:356`
  - Rejection of unsupported installer types (e.g. `EXE`): `repo/app/src/main/java/com/eaglepoint/console/service/UpdateService.java:363`
  - Deterministic integration coverage for strict-MSI rejection: `repo/tests/java/com/eaglepoint/console/integration/UpdateInstallerApiTest.java:77`, `repo/tests/java/com/eaglepoint/console/integration/UpdateInstallerApiTest.java:94`

### 3.2 Medium — Non-deterministic updater integration tests
- Status: **Resolved**
- Evidence:
  - Shared fixture reset helper introduced: `repo/tests/java/com/eaglepoint/console/integration/BaseIntegrationTest.java:202`
  - Updater API tests now clear state per test: `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java:19`, `repo/tests/java/com/eaglepoint/console/integration/UpdateInstallerApiTest.java:33`
  - Previous ambiguous rollback assertion replaced with exact 409 expectation on clean state: `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java:66`
  - Loose “either/or” unsafe-install-args expectation replaced with exact validation assertions: `repo/tests/java/com/eaglepoint/console/integration/UpdateInstallerApiTest.java:158`

### 3.3 Medium — Ambiguous installer-failure recovery state
- Status: **Resolved**
- Evidence:
  - Auto-revert path implemented after installer failure: `repo/app/src/main/java/com/eaglepoint/console/service/UpdateService.java:425`, `repo/app/src/main/java/com/eaglepoint/console/service/UpdateService.java:807`
  - Recovery state persisted on history rows: `repo/app/src/main/java/com/eaglepoint/console/model/UpdateHistoryEntry.java:21`, `repo/app/src/main/java/com/eaglepoint/console/repository/UpdateHistoryRepository.java:36`
  - Migration for `recovery_state`: `repo/app/src/main/resources/db/migrations/V5__update_history_recovery_state.sql:14`
  - Unit tests assert `AUTO_REVERTED` behavior and restored filesystem state: `repo/tests/java/com/eaglepoint/console/unit/service/UpdateServiceInstallerTest.java:118`

### 3.4 Minor doc consistency notes
- Status: **Resolved**
- Evidence:
  - Route-role matrix in update routes comment corrected: `repo/app/src/main/java/com/eaglepoint/console/api/routes/UpdateRoutes.java:14`
  - API spec clarifies strict production MSI behavior and legacy override boundary: `docs/api-spec.md:220`

## 4. Consolidated Outcome
- Previous tracked High/Medium findings from `.tmp/audit_report-1.md` are **closed by static evidence**.
- Updated follow-up verdict for fix scope: **Pass (for tracked fixes)**.

## 5. Boundary Notes
- This is still static-only; runtime installer/UAC/OS-shell behavior requires manual verification on Windows 11.
- Untracked temporary file present (non-product): `.tmp/evaluator-prompt-iter-1.md`.
