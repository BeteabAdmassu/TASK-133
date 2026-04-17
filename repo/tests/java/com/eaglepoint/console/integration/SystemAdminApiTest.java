package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * Tests for governance routes — audit trail, logs, job scheduler admin.
 */
class SystemAdminApiTest extends BaseIntegrationTest {

    // ─── Audit trail ───────────────────────────────────────────────────────────

    @Test
    void auditTrailRequiresAuth() {
        anonymous().when().get("/api/audit-trail").then().statusCode(401);
    }

    @Test
    void auditTrailDeniesUnprivilegedRoles() {
        asRole("OPS_MANAGER").when().get("/api/audit-trail").then().statusCode(403);
        asRole("REVIEWER").when().get("/api/audit-trail").then().statusCode(403);
    }

    @Test
    void auditTrailAllowsAdminAndAuditor() {
        withAdmin().when().get("/api/audit-trail").then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("totalPages", notNullValue());
        asRole("AUDITOR").when().get("/api/audit-trail").then().statusCode(200);
    }

    @Test
    void auditTrailCanFilterByEntityType() {
        // Trigger an auditable event (create community) so audit entries exist
        withAdmin()
            .body(Map.of("name", unique("AuditCheck")))
        .when().post("/api/communities").then().statusCode(201);

        withAdmin()
        .when()
            .get("/api/audit-trail?entityType=Community&pageSize=50")
        .then()
            .statusCode(200)
            .body("data", notNullValue());
    }

    // ─── System logs ─────────────────────────────────────────────────────────

    @Test
    void logsDeniesUnprivilegedRoles() {
        asRole("REVIEWER").when().get("/api/logs").then().statusCode(403);
    }

    @Test
    void logsAllowsAdminAndAuditor() {
        withAdmin().when().get("/api/logs").then().statusCode(200)
            .body("data", notNullValue());
        asRole("AUDITOR").when().get("/api/logs").then().statusCode(200);
    }

    // ─── Scheduled jobs ───────────────────────────────────────────────────────

    @Test
    void jobsListRequiresSystemAdmin() {
        anonymous().when().get("/api/jobs").then().statusCode(401);
        asRole("AUDITOR").when().get("/api/jobs").then().statusCode(403);

        withAdmin().when().get("/api/jobs").then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("data.size()", greaterThanOrEqualTo(3)); // BACKUP, ARCHIVE, CONSISTENCY_CHECK seeds
    }

    @Test
    void pauseAndResumeJob() {
        // First job from seed should be available
        int jobId = withAdmin().when().get("/api/jobs").then().statusCode(200)
            .extract().path("data[0].id");

        withAdmin()
        .when()
            .post("/api/jobs/" + jobId + "/pause")
        .then()
            .statusCode(200)
            .body("job.status", equalTo("PAUSED"));

        withAdmin()
        .when()
            .post("/api/jobs/" + jobId + "/resume")
        .then()
            .statusCode(200)
            .body("job.status", equalTo("ACTIVE"));
    }

    @Test
    void pauseJobDeniesUnprivilegedRoles() {
        int jobId = withAdmin().when().get("/api/jobs").then().statusCode(200)
            .extract().path("data[0].id");

        asRole("AUDITOR").when().post("/api/jobs/" + jobId + "/pause").then().statusCode(403);
    }

    // ─── Health ───────────────────────────────────────────────────────────────

    @Test
    void healthEndpointReturnsStatusAndVersion() {
        anonymous()
        .when()
            .get("/api/health")
        .then()
            .statusCode(200)
            .body("status", notNullValue())
            .body("db", notNullValue())
            .body("version", notNullValue())
            .body("uptime", notNullValue());
    }
}
