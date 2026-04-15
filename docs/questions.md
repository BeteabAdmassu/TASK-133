# Business Logic Questions & Decisions

## Decisions Log

1. **Encryption Key Storage on Windows**
   - **Question**: Where is the AES-256 encryption key for sensitive fields (staffId, residentId, address) stored? The prompt says "encrypted-at-rest" but does not specify key management.
   - **My Understanding**: The app is fully offline on a single Windows 11 machine with no key management server. The key must survive application restarts but remain inaccessible to other OS users.
   - **Decision**: Use the Windows DPAPI (Data Protection API) via Java's `KeyStore` with the `Windows-MY` provider. A random 256-bit key is generated on first run, wrapped with DPAPI, and stored in the OS keystore under the application's identity. The application refuses to start if it cannot access the keystore. This ties the key to the Windows user account that installed/runs the application.
   - **Impact**: If the OS user account is deleted or the database is moved to another machine, all encrypted fields become unreadable. An operator procedure to export/re-encrypt before migration would be required. If the evaluator intended a simpler approach (e.g., a passphrase-derived key entered at startup), the key derivation and storage logic would change but the encryption algorithm stays the same.

2. **"One Active Pickup Point Per Community Per Day" -- What Constitutes "Active" and What Is "Per Day"**
   - **Question**: Does "active" mean status=ACTIVE (not paused, not inactive), or does it mean any pickup point that is open/serving on that day? Does "per day" mean the calendar date of an attempted status change, or of actual service delivery?
   - **My Understanding**: The simplest enforceable interpretation is: at most one pickup point per community may have `status='ACTIVE'` at any given moment. Paused points do not count because they are temporarily out of service. "Per day" aligns with the calendar date when the activation is attempted.
   - **Decision**: Enforce in business logic: when a request to set a pickup point to ACTIVE is received, the system queries for any other pickup point in the same community with `status='ACTIVE'` (regardless of date). If found, reject with 409. "PAUSED" points do not block activation. The "per day" phrasing is interpreted as "at any given time" since the system has no concept of time-slot reservations.
   - **Impact**: If the intent is that a community can have one active point per calendar date but can switch daily (activate A on Monday, deactivate it, activate B on Tuesday), the logic would instead query only active points whose `updated_at` date matches today's date. That would require a more complex query and a scheduled de-activation task.

3. **Complete Bed State Machine -- Transitions Not Explicitly Listed**
   - **Question**: The prompt explicitly states "bed can't move from Occupied directly to OutOfService without a recorded checkout" but does not define all other allowed transitions. For example: can a bed go from RESERVED to CLEANING? Can MAINTENANCE go directly to AVAILABLE?
   - **My Understanding**: The prompt's explicit example implies a safety concern: a bed with a resident should not be marked out of service without a checkout event. The remaining transitions should follow operational common sense for hospital bed management.
   - **Decision**: Defined the full transition table in Core Requirement 34 based on operational logic:
     - AVAILABLE is the hub state; beds return here after CLEANING or MAINTENANCE
     - OCCUPIED -> CLEANING (room turnover after checkout, but checkout must happen first -- OCCUPIED -> AVAILABLE -> CLEANING is also valid)
     - MAINTENANCE -> OUT_OF_SERVICE (escalation within the maintenance track)
     - OUT_OF_SERVICE -> MAINTENANCE (begin repair before returning to service)
     - Direct CLEANING -> OUT_OF_SERVICE is not allowed (must go through MAINTENANCE)
   - **Impact**: If the hospital has different operational protocols (e.g., beds can go directly from CLEANING to OUT_OF_SERVICE), the transition table in `BedStateMachine.java` is the single place to change it. All other code uses the state machine, so no other changes are needed.

4. **Appeal Filing -- Who Can File, and Against Which Scorecard States**
   - **Question**: The prompt says appeals must be filed within 7 calendar days but does not specify: (a) who can file -- only the evaluatee or also the evaluator? (b) can an appeal be filed against a scorecard that is already APPEALED, RECUSED, or only SUBMITTED/APPROVED?
   - **My Understanding**: Appeals are a mechanism for the person being evaluated to contest the outcome. Evaluators would not appeal their own scoring. Appeals make sense only once a scorecard is submitted (the evaluation is complete enough to contest).
   - **Decision**: (a) Only the evaluatee (`scorecards.evaluatee_id`) may file an appeal. (b) Appeals can only be filed against scorecards in `SUBMITTED` or `APPROVED` status. Filing against a `RECUSED` scorecard is rejected (reason: no evaluation was completed). Only one open appeal per scorecard is allowed at a time (duplicate check on status IN ('PENDING','UNDER_REVIEW')).
   - **Impact**: If the evaluator should also be able to contest a peer's self-evaluation, the `filed_by` permission check would need to expand. If appeals against APPROVED scorecards should be blocked (only SUBMITTED), one line in `AppealService` changes.

5. **Archival Age Calculation -- Cycle end_date vs. created_at, and What "Older Than 24 Months" Means**
   - **Question**: Monthly archival targets "historical cycles older than 24 months." Does "older than 24 months" mean the cycle's `end_date` is more than 24 months ago, or its `created_at`? And is 24 months a rolling window from the archival run date, or a fixed cutoff?
   - **My Understanding**: An evaluation cycle is "historical" when it has concluded, so the relevant timestamp is `end_date`. The 24-month window is a rolling retention period calculated from the date the archival job runs.
   - **Decision**: Archival targets cycles where `status = 'CLOSED'` AND `end_date < date('now', '-24 months')`. The archival job does not touch DRAFT or ACTIVE cycles, and it only archives CLOSED cycles (not already-ARCHIVED ones). After copying to `evaluation_cycles_archive`, the cycle status is set to ARCHIVED and the original is deleted.
   - **Impact**: If the intent is 24 months from creation date, the WHERE clause changes from `end_date` to `created_at`. If cycles in ACTIVE status that simply haven't been closed should also be archived, the status filter would change -- but that seems operationally unsafe.

6. **Re-Review Trigger and Second Reviewer Assignment Workflow**
   - **Question**: Who triggers a re-review, and who assigns the second expert reviewer? The prompt says "re-reviews require a second expert reviewer" but does not specify the initiating role or the assignment process.
   - **My Understanding**: Re-reviews are triggered by either (a) a resolved appeal that finds the original review was flawed, or (b) a direct admin action. The second reviewer must be a different person from the original reviewer.
   - **Decision**: Re-review is triggered by `POST /api/reviews/:id/assign-second` (ADMIN-only endpoint). The admin selects the second reviewer. The system validates that `reviewerId != review.reviewer_id`. A new `Review` record is created with `second_reviewer_id` set; the original review's status becomes `IN_REVIEW` again. The second reviewer completes the review through the standard review workflow.
   - **Impact**: If re-reviews should be triggered automatically when an appeal is resolved in the evaluatee's favor, that logic would be added to `AppealService.resolve()`. The endpoint and validation remain the same.

7. **Coordinate Masking Precision -- What Is "0.1-Mile Grid" in Decimal Degrees**
   - **Question**: The prompt says coordinates are "masked to a 0.1-mile grid in standard views." 0.1 miles is approximately 528 feet. In decimal degrees, this varies by latitude. The prompt does not specify the latitude range of operations.
   - **My Understanding**: For US hospital operations (approximately 25-49 degrees N latitude), 0.1 miles is approximately 0.00145 degrees in latitude and 0.00130-0.00190 degrees in longitude (varies with latitude). Using a fixed 0.00145-degree rounding constant for both axes provides a consistent and simple grid.
   - **Decision**: Implement coordinate masking as: `round(coord / 0.00145) * 0.00145` applied to both lat and lon, in `MaskingUtil.maskCoordinate(double coord)`. This gives a uniform grid of approximately 0.1 miles per cell across the expected operating latitude range. Raw coordinates are never persisted; only masked values are stored in `route_checkpoints`.
   - **Impact**: If the hospital operates outside the US or at high latitudes, the constant should be recalculated. If a true per-axis grid is required (different precision for lat vs. lon), `MaskingUtil` exposes separate `maskLat` and `maskLon` methods with distinct constants.

8. **Token Expiry Duration**
   - **Question**: The prompt specifies token-based auth for the API but does not state how long tokens are valid. Common options are 1 hour, 8 hours (one shift), 24 hours, or indefinite-until-revoked.
   - **My Understanding**: This is an offline console used by shift workers in 8+ hour sessions. A 24-hour expiry balances security (token not valid forever) with convenience (no mid-shift re-authentication purely due to token age, since inactivity lock handles session security separately).
   - **Decision**: API tokens expire 24 hours after issuance. The expiry is checked on every authenticated request. On login, any existing unexpired token for the user is revoked and a new one issued (one active token per user).
   - **Impact**: If the organization requires shorter-lived tokens (e.g., 8 hours to match shift length), the expiry constant in `TokenService` changes from `PT24H` to `PT8H`. The rate-limiting logic is unaffected.

9. **Scheduled Report Job Configuration -- What Does "User-Defined Cron Schedule" Mean for the UI**
   - **Question**: The prompt says scheduled reports run on a "user-defined cron schedule." Does this mean the UI exposes a raw cron expression input, or a simplified picker (daily/weekly/monthly at a specific time)?
   - **My Understanding**: A raw cron expression is the most flexible and precise option, but it requires operators to know cron syntax. A simplified picker (frequency + time) covers the stated use cases (nightly, monthly) without requiring cron literacy.
   - **Decision**: The Reports window provides a simplified schedule picker: frequency (Daily / Weekly / Monthly), day-of-week or day-of-month selector (for Weekly/Monthly), and a time picker (HH:MM). This is converted to a Quartz cron expression internally. The underlying `scheduled_jobs.cron_expression` field stores the full Quartz cron, so advanced users or admins can edit it directly via the Health Panel.
   - **Impact**: If operators are expected to enter arbitrary cron expressions (e.g., "every 15 minutes on weekdays"), the UI field would need to accept raw cron input with validation.

10. **Route Import File Format -- CSV vs. JSON, and Required Column Set**
    - **Question**: The prompt says "staff import checkpoint logs from handheld devices via USB" and mentions "validate file format" but does not specify whether the format is CSV, JSON, a proprietary format, or vendor-specific. Required columns are also not listed.
    - **My Understanding**: Handheld device exports are most commonly CSV (plain text, widely supported). JSON is less common for field hardware. The console should accept CSV as the primary format with a defined column set, with optional JSON support for more capable devices.
    - **Decision**: Primary format is CSV with a header row. Required columns: `checkpoint_name` (text), `expected_at` (ISO-8601 datetime), `actual_at` (ISO-8601 datetime or empty string), `lat` (decimal, -90 to 90), `lon` (decimal, -180 to 180). Optional column: `notes` (text). JSON format is supported as a secondary option: an array of objects with the same field names. The importer auto-detects format by file extension (`.csv` vs `.json`). Files with any other extension are rejected at the UI layer before API upload.
    - **Impact**: If the handheld vendor uses a different column naming convention (e.g., `latitude` instead of `lat`), a column-mapping configuration screen would be needed. If a binary or proprietary format is required, a dedicated parser plugin would be added to `RouteImportService`.
