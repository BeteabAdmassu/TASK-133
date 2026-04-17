# Test Coverage Audit

## Project Type Detection
- README explicitly declares project type at top: `Project Type: desktop` (`repo/README.md:3`).
- Final type used for audit: **desktop**.

## Backend Endpoint Inventory
- Endpoint extraction source: route declarations in `repo/app/src/main/java/com/eaglepoint/console/api/routes/*Routes.java` (95 route entries).
- Registration source: `repo/app/src/main/java/com/eaglepoint/console/api/ApiServer.java:96`-`repo/app/src/main/java/com/eaglepoint/console/api/ApiServer.java:109`.
- Total unique endpoints (`METHOD + PATH`): **95**.
- Full endpoint-by-endpoint inventory is captured in the API Test Mapping Table below (all 95 rows).

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
| `DELETE /api/beds/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:454` |
| `POST /api/beds/{id}/transition` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:92` |
| `GET /api/beds/{id}/history` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/BedApiTest.java:192` |
| `GET /api/communities` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java` | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java:43` |
| `POST /api/communities` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java:17` |
| `GET /api/communities/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java` | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java:71` |
| `PUT /api/communities/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java:102` |
| `DELETE /api/communities/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/CommunityApiTest.java:121` |
| `GET /api/cycles` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:34` |
| `POST /api/cycles` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:23` |
| `GET /api/cycles/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:97` |
| `PUT /api/cycles/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:85` |
| `DELETE /api/cycles/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:95` |
| `GET /api/cycles/{cycleId}/templates` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:144` |
| `POST /api/cycles/{cycleId}/templates` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:117` |
| `POST /api/cycles/{cycleId}/templates/{templateId}/metrics` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:170` |
| `GET /api/scorecards` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:251` |
| `POST /api/scorecards` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:193` |
| `GET /api/scorecards/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:269` |
| `PUT /api/scorecards/{id}/responses` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:211` |
| `POST /api/scorecards/{id}/submit` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:219` |
| `POST /api/scorecards/{id}/recuse` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:241` |
| `GET /api/reviews` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:347` |
| `POST /api/reviews` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:283` |
| `GET /api/reviews/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:364` |
| `POST /api/reviews/{id}/approve` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:292` |
| `POST /api/reviews/{id}/reject` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:311` |
| `POST /api/reviews/{id}/flag-conflict` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:396` |
| `POST /api/reviews/{id}/assign-second` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:336` |
| `GET /api/appeals` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:464` |
| `POST /api/appeals` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:486` |
| `GET /api/appeals/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:509` |
| `POST /api/appeals/{id}/resolve` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:517` |
| `POST /api/appeals/{id}/reject` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:534` |
| `POST /api/exports` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ExportApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ExportApiTest.java:20` |
| `GET /api/exports/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ExportApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ExportApiTest.java:60` |
| `GET /api/geozones` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java:14` |
| `POST /api/geozones` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java:38` |
| `GET /api/geozones/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java:69` |
| `PUT /api/geozones/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java:86` |
| `DELETE /api/geozones/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/GeozoneApiTest.java:100` |
| `GET /api/kpis` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java:13` |
| `POST /api/kpis` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java:38` |
| `GET /api/kpis/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java:84` |
| `PUT /api/kpis/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java:99` |
| `GET /api/kpi-scores` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java:130` |
| `POST /api/kpi-scores` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/KpiApiTest.java:120` |
| `GET /api/leader-assignments` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java:42` |
| `POST /api/leader-assignments` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java:62` |
| `PUT /api/leader-assignments/{id}/end` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java:90` |
| `GET /api/pickup-points` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:154` |
| `POST /api/pickup-points` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:39` |
| `GET /api/pickup-points/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:144` |
| `PUT /api/pickup-points/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:186` |
| `DELETE /api/pickup-points/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:217` |
| `POST /api/pickup-points/{id}/pause` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:106` |
| `POST /api/pickup-points/{id}/resume` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:115` |
| `POST /api/pickup-points/match` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/PickupPointApiTest.java:234` |
| `GET /api/route-imports` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/RouteImportApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/RouteImportApiTest.java:20` |
| `POST /api/route-imports` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/RouteImportApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/RouteImportApiTest.java:44` |
| `GET /api/route-imports/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/RouteImportApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/RouteImportApiTest.java:100` |
| `GET /api/route-imports/{id}/checkpoints` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/RouteImportApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/RouteImportApiTest.java:121` |
| `GET /api/service-areas` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java:28` |
| `POST /api/service-areas` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java:27` |
| `GET /api/service-areas/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java:104` |
| `PUT /api/service-areas/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java:132` |
| `DELETE /api/service-areas/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/ServiceAreaApiTest.java:146` |
| `GET /api/health` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/AuthApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java` | `repo/tests/java/com/eaglepoint/console/integration/AuthApiTest.java:155` |
| `GET /api/audit-trail` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java`, `repo/tests/java/com/eaglepoint/console/integration/SystemAdminApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java:196` |
| `GET /api/logs` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/SystemAdminApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/SystemAdminApiTest.java:55` |
| `GET /api/jobs` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/SystemAdminApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/SystemAdminApiTest.java:69` |
| `POST /api/jobs/{id}/pause` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/SystemAdminApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/SystemAdminApiTest.java:86` |
| `POST /api/jobs/{id}/resume` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/SystemAdminApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/SystemAdminApiTest.java:93` |
| `GET /api/users` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java`, `repo/tests/java/com/eaglepoint/console/integration/LeaderAssignmentApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/EvaluationApiTest.java:600` |
| `POST /api/users` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/UserApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/UserApiTest.java:53` |
| `GET /api/users/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/UserApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/UserApiTest.java:134` |
| `PUT /api/users/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/UserApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/UserApiTest.java:172` |
| `DELETE /api/users/{id}` | yes | true no-mock HTTP | `repo/tests/java/com/eaglepoint/console/integration/UserApiTest.java` | `repo/tests/java/com/eaglepoint/console/integration/UserApiTest.java:195` |

## API Test Classification
1. **True No-Mock HTTP**
   - Integration test harness boots real Javalin server and exercises HTTP via REST-Assured (`repo/tests/java/com/eaglepoint/console/integration/BaseIntegrationTest.java:68`, `repo/tests/java/com/eaglepoint/console/integration/BaseIntegrationTest.java:92`-`repo/tests/java/com/eaglepoint/console/integration/BaseIntegrationTest.java:97`).
   - Live deployment smoke tests hit compose container over HTTP (`repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java:30`, `repo/run_tests.sh:50`-`repo/run_tests.sh:53`).
2. **HTTP with Mocking**
   - None found in integration/API tests.
3. **Non-HTTP unit/integration without HTTP**
   - Unit suites under `repo/tests/java/com/eaglepoint/console/unit/**`.

## Mock Detection
- Unit tests with Mockito mocks (expected for unit layer):
  - `repo/tests/java/com/eaglepoint/console/unit/service/AuthServiceTest.java` (`@Mock` repositories/token service)
  - `repo/tests/java/com/eaglepoint/console/unit/service/PickupPointServiceTest.java` (`@Mock` repositories/audit)
  - `repo/tests/java/com/eaglepoint/console/unit/service/EvaluationServiceTest.java` (`@Mock` repository/audit)
  - `repo/tests/java/com/eaglepoint/console/unit/service/RouteImportServiceTest.java` (`@Mock` repository/notification/audit)
  - `repo/tests/java/com/eaglepoint/console/unit/service/AppealServiceTest.java` (`@Mock` repository/audit)
- No `jest.mock`, `vi.mock`, `sinon.stub`, or DI override patterns found in Java API integration tests.

## Coverage Summary
- Total endpoints: **95**
- Endpoints with HTTP tests: **95**
- Endpoints with true no-mock HTTP tests: **95**
- HTTP coverage: **100.00%**
- True API coverage: **100.00%**

## Unit Test Summary
### Backend Unit Tests
- Files:
  - `repo/tests/java/com/eaglepoint/console/unit/service/AuthServiceTest.java`
  - `repo/tests/java/com/eaglepoint/console/unit/service/PickupPointServiceTest.java`
  - `repo/tests/java/com/eaglepoint/console/unit/service/EvaluationServiceTest.java`
  - `repo/tests/java/com/eaglepoint/console/unit/service/RouteImportServiceTest.java`
  - `repo/tests/java/com/eaglepoint/console/unit/service/AppealServiceTest.java`
  - `repo/tests/java/com/eaglepoint/console/unit/service/BedStateMachineTest.java`
  - `repo/tests/java/com/eaglepoint/console/unit/security/EncryptionUtilTest.java`
  - `repo/tests/java/com/eaglepoint/console/unit/security/MaskingUtilTest.java`
- Modules covered:
  - services (auth, pickup points, evaluation, route import, appeal, bed state machine)
  - security utilities (encryption, masking)
- Important backend modules still not directly unit tested:
  - route/controller layer classes (`*Routes.java`), middleware (`AuthMiddleware`, `RateLimiter`, `ErrorHandler`), and several services (`UserService`, `CommunityService`, `ServiceAreaService`, `LeaderAssignmentService`, `KpiService`, `GeozoneService`, `ExportService`, `JobScheduler` behavior in isolation).

### Frontend Unit Tests
- Frontend/UI unit test files:
  - `repo/tests/java/com/eaglepoint/console/unit/ui/LoginInputValidatorTest.java`
  - `repo/tests/java/com/eaglepoint/console/unit/ui/BedDisplayFilterTest.java`
  - `repo/tests/java/com/eaglepoint/console/unit/ui/AuthSessionTest.java`
- Framework/tools detected:
  - JUnit 5 (`org.junit.jupiter.api.Test`) in all UI unit test files.
- Components/modules covered:
  - `repo/app/src/main/java/com/eaglepoint/console/ui/LoginInputValidator.java`
  - `repo/app/src/main/java/com/eaglepoint/console/ui/bed/BedDisplayFilter.java`
  - `repo/app/src/main/java/com/eaglepoint/console/ui/AuthSession.java`
- Important frontend components/modules not directly tested:
  - full JavaFX controllers and windows such as `MainWindow`, `LoginDialog` interaction thread path, `BedBoardController`, `PickupPointController`, `EvaluationController`, `ReportsController`.

**Frontend unit tests: PRESENT**

### Cross-Layer Observation
- Balance improved materially: backend API coverage is complete; frontend now has explicit unit-level coverage for extracted UI logic/state.
- Remaining imbalance: no full JavaFX controller/integration UI automation (acceptable for now, but still a maturity gap).

## API Observability Check
- Strong: tests explicitly show method/path + request payload + response assertions across core domains (users, service areas, pickups, evaluations, beds, exports, kpis, geozones, system admin).
- Strong live observability: `LiveAppSmokeTest` validates container-level behavior through real HTTP (`repo/tests/java/com/eaglepoint/console/integration/LiveAppSmokeTest.java`).
- Minor weakness: some list/auth tests are status-focused with lighter payload assertions.

## Tests Check
- Success paths: comprehensive across all endpoint groups.
- Failure paths: present (401, 403, 404, 409, 400) in most suites.
- Edge/business rules: covered for key rules (bed transitions, appeal identity/deadline flows, duplicate conflicts, role restrictions).
- Validation quality: generally meaningful payload assertions; not purely autogenerated.
- `run_tests.sh`: Docker-based orchestration is compliant (`repo/run_tests.sh:44`, `repo/run_tests.sh:50`).
- Local dependency note: script still requires host `curl` for readiness probe (`repo/run_tests.sh:29`), so environment is mostly Docker-contained but not 100% host-tool-free.

## Test Coverage Score (0-100)
- **92/100**

## Score Rationale
- Full endpoint-level HTTP coverage with true no-mock execution path.
- No disabled critical API suites.
- Added UI-unit presence improves cross-layer completeness.
- Deductions: limited direct unit coverage for middleware/controllers/core infra, and lack of end-to-end JavaFX automation.

## Key Gaps
- Middleware and scheduler behavior still rely primarily on integration coverage rather than dedicated unit tests.
- UI testing is helper/state focused; no controller-level automated UI interaction coverage.

## Confidence & Assumptions
- Confidence: **high** for endpoint inventory/mapping and README gate checks; **medium-high** for behavioral sufficiency due static-only review.
- Assumptions:
  - Any route hit by exact method/path (including dynamic variants) is counted as covered.
  - `@Disabled` absence indicates active test inclusion potential.
  - Maven test source configuration (`repo/app/pom.xml:181`) includes `repo/tests/java` as authoritative test tree.

## Test Coverage Verdict
- **PASS**

---

# README Audit

## README Location Check
- File exists: `repo/README.md`.

## Hard Gate Failures
- **None detected**.

## High Priority Issues
- None.

## Medium Priority Issues
- `run_tests.sh` no longer performs jq-based smoke assertions, but README testing section still describes jq smoke checks as part of script behavior (`repo/README.md:280`-`repo/README.md:282` vs `repo/run_tests.sh:42`-`repo/run_tests.sh:57`).

## Low Priority Issues
- Optional local JavaFX workflow documentation is clear but could explicitly separate "runtime deliverable" vs "developer-only UI run" even more strongly.

## Hard Gate Review Detail
- Formatting/readability: PASS (well-structured markdown).
- Startup instructions (desktop): PASS (`docker-compose up --build -d` and desktop build/run steps present at `repo/README.md:18`, `repo/README.md:127`, `repo/README.md:170`-`repo/README.md:173`).
- Access method: PASS (API URL/port and desktop launch/usage documented at `repo/README.md:30`, `repo/README.md:166`-`repo/README.md:185`).
- Verification method: PASS (deterministic API checks + desktop UI verification table at `repo/README.md:138`-`repo/README.md:158`, `repo/README.md:175`-`repo/README.md:185`).
- Environment rules: PASS (no npm/pip/apt/manual DB setup required for normal operation; Docker-centered flow at `repo/README.md:106`-`repo/README.md:113`).
- Demo credentials (auth exists): PASS (all seeded roles documented with username/password/role at `repo/README.md:221`-`repo/README.md:227`; seed evidence `repo/app/src/main/resources/db/migrations/V2__seed_admin_user.sql:5`-`repo/app/src/main/resources/db/migrations/V2__seed_admin_user.sql:11`).

## Engineering Quality
- Tech stack clarity: strong.
- Architecture explanation: strong.
- Testing instructions: strong but slightly out of sync with current `run_tests.sh` behavior.
- Security/roles/workflows: clear and concrete.
- Presentation quality: high.

## README Verdict
- **PASS**

---

# Final Verdicts
- **Test Coverage Audit:** PASS
- **README Audit:** PASS
