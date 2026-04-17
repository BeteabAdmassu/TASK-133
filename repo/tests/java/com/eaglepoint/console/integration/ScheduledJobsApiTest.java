package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * CRUD coverage for {@code /api/jobs} (scheduled job management).
 *
 * <p>Tests run against the embedded server with an in-memory SQLite DB.
 * Each test generates a unique cron/config so jobs don't collide.</p>
 */
class ScheduledJobsApiTest extends BaseIntegrationTest {

    @Test
    void listRequiresSystemAdmin() {
        anonymous().when().get("/api/jobs").then().statusCode(401);
        asRole("OPS_MANAGER").when().get("/api/jobs").then().statusCode(403);
        asRole("REVIEWER").when().get("/api/jobs").then().statusCode(403);
        asRole("AUDITOR").when().get("/api/jobs").then().statusCode(403);
    }

    @Test
    void createValidReportJobPersistsAndListsIt() {
        int id = withAdmin()
            .body(Map.of(
                "jobType", "REPORT",
                "cronExpression", "0 5 3 * * ?",
                "timeoutSeconds", 600,
                "status", "ACTIVE",
                "configJson", Map.of(
                    "entityType", "COMMUNITIES",
                    "format", "CSV",
                    "destinationPath", "/tmp/reports"
                )
            ))
        .when()
            .post("/api/jobs")
        .then()
            .statusCode(201)
            .body("job.id", notNullValue())
            .body("job.jobType", equalTo("REPORT"))
            .body("job.status", equalTo("ACTIVE"))
            .body("job.configJson", containsString("COMMUNITIES"))
            .extract().path("job.id");

        withAdmin()
        .when()
            .get("/api/jobs")
        .then()
            .statusCode(200)
            .body("data.findAll { it.id == " + id + " }.size()", equalTo(1));

        withAdmin()
        .when()
            .get("/api/jobs/" + id)
        .then()
            .statusCode(200)
            .body("job.id", equalTo(id))
            .body("job.cronExpression", equalTo("0 5 3 * * ?"));
    }

    @Test
    void createReportJobWithoutEntityTypeReturns400() {
        withAdmin()
            .body(Map.of(
                "jobType", "REPORT",
                "cronExpression", "0 5 3 * * ?",
                "configJson", Map.of("format", "CSV")
            ))
        .when()
            .post("/api/jobs")
        .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void createWithInvalidCronReturns400() {
        withAdmin()
            .body(Map.of(
                "jobType", "BACKUP",
                "cronExpression", "every five minutes"
            ))
        .when()
            .post("/api/jobs")
        .then()
            .statusCode(400)
            .body("error.fields.cronExpression", notNullValue());
    }

    @Test
    void nonAdminCannotCreateJob() {
        asRole("OPS_MANAGER")
            .body(Map.of(
                "jobType", "BACKUP",
                "cronExpression", "0 0 2 * * ?"
            ))
        .when()
            .post("/api/jobs")
        .then()
            .statusCode(403);
    }

    @Test
    void updateChangesCronAndStatus() {
        int id = withAdmin()
            .body(Map.of(
                "jobType", "REPORT",
                "cronExpression", "0 10 3 * * ?",
                "configJson", Map.of("entityType", "PICKUP_POINTS")
            ))
        .when()
            .post("/api/jobs")
        .then()
            .statusCode(201)
            .extract().path("job.id");

        withAdmin()
            .body(Map.of(
                "cronExpression", "0 20 4 * * ?",
                "status", "PAUSED"
            ))
        .when()
            .put("/api/jobs/" + id)
        .then()
            .statusCode(200)
            .body("job.cronExpression", equalTo("0 20 4 * * ?"))
            .body("job.status", equalTo("PAUSED"));
    }

    @Test
    void pauseResumeReflectsStatus() {
        int id = withAdmin()
            .body(Map.of(
                "jobType", "CONSISTENCY_CHECK",
                "cronExpression", "0 0 1 * * ?"
            ))
        .when()
            .post("/api/jobs")
        .then()
            .statusCode(201)
            .extract().path("job.id");

        withAdmin().when().post("/api/jobs/" + id + "/pause")
            .then().statusCode(200).body("job.status", equalTo("PAUSED"));
        withAdmin().when().post("/api/jobs/" + id + "/resume")
            .then().statusCode(200).body("job.status", equalTo("ACTIVE"));
    }

    @Test
    void deleteRemovesJob() {
        int id = withAdmin()
            .body(Map.of(
                "jobType", "BACKUP",
                "cronExpression", "0 30 2 * * ?"
            ))
        .when()
            .post("/api/jobs")
        .then()
            .statusCode(201)
            .extract().path("job.id");

        withAdmin().when().delete("/api/jobs/" + id).then().statusCode(204);
        withAdmin().when().get("/api/jobs/" + id).then().statusCode(404);
    }

    @Test
    void updateUnknownJobReturns404() {
        withAdmin()
            .body(Map.of("status", "PAUSED"))
        .when()
            .put("/api/jobs/999999")
        .then()
            .statusCode(404);
    }
}
