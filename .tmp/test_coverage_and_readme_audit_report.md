# Test Coverage Audit

## Project Type Detection
- Declared in README: **desktop** (`repo/README.md:3`).
- Inference check confirms desktop JavaFX app with embedded API.

## Backend Endpoint Inventory
- Total unique endpoints (`METHOD + PATH`): **108**.
- Source of truth: `repo/app/src/main/java/com/eaglepoint/console/api/routes/*Routes.java`.
- Route registration entrypoint: `repo/app/src/main/java/com/eaglepoint/console/api/ApiServer.java:116`.

## API Test Mapping Table
| Endpoint | Covered | Test type | Test files | Evidence |
|---|---|---|---|---|
| `POST /api/auth/login` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/AuthApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/BaseIntegrationTest.java` | `repo/tests/java/com/eaglepoint/console/integration/AuthApiTest.java:37` |
| `POST /api/auth/logout` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/AuthApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java` | `repo/tests/java/com/eaglepoint/console/integration/AuthApiTest.java:137` |
| `GET /api/auth/me` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/AuthApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java` | `repo/tests/java/com/eaglepoint/console/integration/AuthApiTest.java:90` |
| `GET /api/bed-buildings` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:223` |
| `POST /api/bed-buildings` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:31` |
| `GET /api/bed-buildings/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:243` |
| `PUT /api/bed-buildings/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:267` |
| `DELETE /api/bed-buildings/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:301` |
| `GET /api/rooms` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:314` |
| `POST /api/rooms` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:47` |
| `GET /api/rooms/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:337` |
| `PUT /api/rooms/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:360` |
| `DELETE /api/rooms/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:385` |
| `GET /api/beds` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:143` |
| `POST /api/beds` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:65` |
| `GET /api/beds/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:165` |
| `PUT /api/beds/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:408` |
| `DELETE /api/beds/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:522` |
| `POST /api/beds/{id}/transition` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:92` |
| `GET /api/beds/{id}/history` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:192` |
| `GET /api/communities` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/DeactivatedUserAuthApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java:43` |
| `POST /api/communities` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/ExportContentApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java:17` |
| `GET /api/communities/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java` | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java:71` |
| `PUT /api/communities/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java:102` |
| `DELETE /api/communities/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java:121` |
| `GET /api/cycles` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:34` |
| `POST /api/cycles` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:23` |
| `GET /api/cycles/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:97` |
| `PUT /api/cycles/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:85` |
| `DELETE /api/cycles/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:95` |
| `POST /api/cycles/{id}/activate` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:471` |
| `POST /api/cycles/{id}/close` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:476` |
| `GET /api/cycles/{cycleId}/templates` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:144` |
| `POST /api/cycles/{cycleId}/templates` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:117` |
| `POST /api/cycles/{cycleId}/templates/{templateId}/metrics` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:170` |
| `GET /api/scorecards` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:251` |
| `POST /api/scorecards` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:193` |
| `GET /api/scorecards/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:269` |
| `PUT /api/scorecards/{id}/responses` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:211` |
| `POST /api/scorecards/{id}/submit` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:219` |
| `POST /api/scorecards/{id}/recuse` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:241` |
| `GET /api/reviews` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:491` |
| `POST /api/reviews` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:283` |
| `GET /api/reviews/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:508` |
| `POST /api/reviews/{id}/approve` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:292` |
| `POST /api/reviews/{id}/reject` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:311` |
| `POST /api/reviews/{id}/flag-conflict` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:437` |
| `POST /api/reviews/{id}/assign-second` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:336` |
| `GET /api/appeals` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:608` |
| `POST /api/appeals` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:630` |
| `GET /api/appeals/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:653` |
| `POST /api/appeals/{id}/resolve` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:661` |
| `POST /api/appeals/{id}/reject` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:678` |
| `POST /api/exports` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ExportApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/ExportContentApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ExportApiTest.java:20` |
| `GET /api/exports/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ExportApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/ExportContentApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ExportApiTest.java:60` |
| `GET /api/geozones` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/QueryShapingApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java:14` |
| `POST /api/geozones` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java:38` |
| `GET /api/geozones/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java:69` |
| `PUT /api/geozones/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java:86` |
| `DELETE /api/geozones/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java:100` |
| `GET /api/kpis` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/QueryShapingApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java:13` |
| `POST /api/kpis` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/QueryShapingApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java:38` |
| `GET /api/kpis/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java:84` |
| `PUT /api/kpis/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java:99` |
| `GET /api/kpi-scores` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java:130` |
| `POST /api/kpi-scores` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java:120` |
| `GET /api/leader-assignments` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java:42` |
| `POST /api/leader-assignments` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java:62` |
| `PUT /api/leader-assignments/{id}/end` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java:90` |
| `GET /api/pickup-points` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:154` |
| `POST /api/pickup-points` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ExportContentApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ExportContentApiTest.java:100` |
| `GET /api/pickup-points/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/PickupPointDayRuleApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:144` |
| `PUT /api/pickup-points/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:186` |
| `DELETE /api/pickup-points/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:217` |
| `POST /api/pickup-points/{id}/pause` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/PickupPointDayRuleApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:106` |
| `POST /api/pickup-points/{id}/resume` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/PickupPointDayRuleApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:115` |
| `POST /api/pickup-points/match` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/PickupPointOverrideMatchApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:234` |
| `POST /api/pickup-points/{id}/override` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointOverrideApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/PickupPointOverrideMatchApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointOverrideApiTest.java:51` |
| `GET /api/route-imports` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/RouteImportApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/RouteImportApiTest.java:20` |
| `POST /api/route-imports` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/RouteDeviationApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/RouteImportApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/RouteDeviationApiTest.java:119` |
| `GET /api/route-imports/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/RouteDeviationApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/RouteImportApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/RouteDeviationApiTest.java:128` |
| `GET /api/route-imports/{id}/checkpoints` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/RouteDeviationApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/RouteImportApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/RouteDeviationApiTest.java:39` |
| `GET /api/service-areas` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/QueryShapingApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/QueryShapingApiTest.java:157` |
| `POST /api/service-areas` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/QueryShapingApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java:27` |
| `GET /api/service-areas/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java:104` |
| `PUT /api/service-areas/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java:132` |
| `DELETE /api/service-areas/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java:146` |
| `GET /api/health` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/AuthApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java` | `repo/tests/java/com/eaglepoint/console/integration/AuthApiTest.java:155` |
| `GET /api/audit-trail` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java`, `repo/tests/java/com/eaglepoint/console/integration/PickupPointOverrideApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java:196` |
| `GET /api/logs` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointOverrideMatchApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/SystemAdminApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointOverrideMatchApiTest.java:238` |
| `GET /api/jobs` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/SystemAdminApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java:19` |
| `GET /api/jobs/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java:58` |
| `POST /api/jobs` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java:40` |
| `PUT /api/jobs/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java:127` |
| `DELETE /api/jobs/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java:166` |
| `POST /api/jobs/{id}/pause` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/SystemAdminApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java:147` |
| `POST /api/jobs/{id}/resume` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/SystemAdminApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ScheduledJobsApiTest.java:149` |
| `GET /api/updates/packages` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/UpdateInstallerApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java:26` |
| `POST /api/updates/packages/{name}/verify` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java:47` |
| `POST /api/updates/packages/{name}/apply` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/UpdateInstallerApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java:58` |
| `POST /api/updates/rollback` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/UpdateInstallerApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java:69` |
| `GET /api/updates/history` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/UpdateInstallerApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java:78` |
| `GET /api/updates/current` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/UpdateInstallerApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/UpdateApiTest.java:90` |
| `GET /api/users` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:744` |
| `POST /api/users` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/DeactivatedUserAuthApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/DeactivatedUserAuthApiTest.java:30` |
| `GET /api/users/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/UserApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/UserApiTest.java:134` |
| `PUT /api/users/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/UserApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/UserApiTest.java:172` |
| `DELETE /api/users/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/DeactivatedUserAuthApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/UserApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/DeactivatedUserAuthApiTest.java:55` |

## API Test Classification
1. **True No-Mock HTTP**: integration suite boots real server and sends HTTP via REST-Assured (`repo/tests/java/com/eaglepoint/console/integration/BaseIntegrationTest.java:114`).
2. **HTTP with Mocking**: none found in integration test files.
3. **Non-HTTP (unit/integration without HTTP)**: unit tests under `repo/tests/java/com/eaglepoint/console/unit/**`.

## Coverage Summary
- Total endpoints: **108**
- Endpoints with HTTP tests: **108**
- Endpoints with TRUE no-mock HTTP tests: **108**
- HTTP coverage: **100.00%**
- True API coverage: **100.00%**

## Unit Test Summary
### Backend Unit Tests
- Present under `repo/tests/java/com/eaglepoint/console/unit/service/**` and `repo/tests/java/com/eaglepoint/console/unit/security/**`.
- Coverage includes auth, pickup points, route import, export, scheduler, security, and evaluation service logic.

### Frontend Unit Tests
- Frontend/UI test files:
  - `repo/tests/java/com/eaglepoint/console/unit/ui/AuthSessionTest.java`
  - `repo/tests/java/com/eaglepoint/console/unit/ui/BedBoardControllerShortcutTest.java`
  - `repo/tests/java/com/eaglepoint/console/unit/ui/BedDisplayFilterTest.java`
  - `repo/tests/java/com/eaglepoint/console/unit/ui/GlobalShortcutsBindingTest.java`
  - `repo/tests/java/com/eaglepoint/console/unit/ui/LoginInputValidatorTest.java`
- Framework/tools detected: JUnit 5.
- Components/modules covered: `GlobalShortcuts`, `BedBoardController` shortcut contract, `LoginInputValidator`, `BedDisplayFilter`, `AuthSession`.
**Frontend unit tests: PRESENT**

### Cross-Layer Observation
- Backend API coverage is complete; frontend has explicit unit coverage but no full JavaFX end-to-end automation.

## API Observability Check
- Endpoint method/path, request payloads, and response-body assertions are explicit across integration suites.

## Tests Check
- Success, failure, validation, and authorization paths are broadly covered.
- `run_tests.sh` is orchestration-only and executes tests in Docker.

## Test Coverage Score (0-100)
- **95/100**

## Score Rationale
- Full endpoint HTTP coverage and no HTTP-layer mocking.
- Strong breadth and strong role/negative-path coverage.
- Minor deduction for missing desktop UI automation/E2E.

## Key Gaps
- No full desktop UI automation/E2E across JavaFX controller workflows.

## Confidence & Assumptions
- Confidence: **high** for static route-to-test mapping and README gate checks.
- Assumption: string-concatenated dynamic endpoint calls in tests map to parameterized routes.

## Test Coverage Verdict
- **PASS**

---

# README Audit

## README Location Check
- File exists: `repo/README.md`.

## Hard Gate Failures
- **None detected.**

## High Priority Issues
- None.

## Medium Priority Issues
- None.

## Low Priority Issues
- None.

## Hard Gate Review Detail
- Project type declaration: PASS (`repo/README.md:3`).
- Startup instructions include `docker-compose up`: PASS (`repo/README.md:18`, `repo/README.md:127`).
- Access method documented (URL + port): PASS (`repo/README.md:30`).
- Verification method documented: PASS (`repo/README.md:138`, `repo/README.md:175`).
- Environment rules are Docker-contained for normal runtime: PASS (`repo/README.md:106`-`repo/README.md:113`).
- Demo credentials for all auth roles are documented: PASS (`repo/README.md:246`-`repo/README.md:253`).

## Engineering Quality
- Tech stack clarity: strong.
- Architecture explanation: strong.
- Testing instructions: aligned with current `run_tests.sh` flow.
- Security and role/workflow documentation: strong.

## README Verdict
- **PASS**

---

# Final Verdicts
- **Test Coverage Audit:** PASS
- **README Audit:** PASS
