package com.eaglepoint.console.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

/**
 * Integration coverage for the {@code /api/updates/*} surface.
 *
 * <p>Every test in this class starts from a wiped updater fixture
 * (update_history DELETEd, installed/ and backups/ emptied) via
 * {@link #clearBeforeEach()}.  That lets us assert the <em>exact</em>
 * HTTP outcome — 404, 409, 200 — without any either-or ambiguity with
 * sibling test classes that drop real packages.</p>
 */
class UpdateApiTest extends BaseIntegrationTest {

    @BeforeEach
    void clearBeforeEach() {
        clearUpdaterFixture();
    }

    @Test
    void listPackagesRequiresSystemAdmin() {
        anonymous().when().get("/api/updates/packages").then().statusCode(401);
        asRole("OPS_MANAGER").when().get("/api/updates/packages").then().statusCode(403);
        asRole("REVIEWER").when().get("/api/updates/packages").then().statusCode(403);
        asRole("AUDITOR").when().get("/api/updates/packages").then().statusCode(403);
    }

    @Test
    void listPackagesAsAdminReturnsDataFieldAndIsAnArray() {
        withAdmin()
        .when()
            .get("/api/updates/packages")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("data", instanceOf(java.util.List.class));
    }

    @Test
    void verifyUnknownPackageReturns404WithStructuredError() {
        withAdmin()
        .when()
            .post("/api/updates/packages/nonexistent-1.0.0/verify")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
            .body("error.message", containsString("UpdatePackage"));
    }

    @Test
    void applyUnknownPackageReturns404WithStructuredError() {
        withAdmin()
        .when()
            .post("/api/updates/packages/nonexistent-1.0.0/apply")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"))
            .body("error.message", containsString("UpdatePackage"));
    }

    @Test
    void rollbackWithoutHistoryReturns409Conflict() {
        withAdmin()
        .when()
            .post("/api/updates/rollback")
        .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"))
            .body("error.message", containsString("No installed update"));
    }

    @Test
    void historyIsReadableByAdminAndAuditor() {
        withAdmin().when().get("/api/updates/history")
            .then().statusCode(200).body("data", notNullValue());
        asRole("AUDITOR").when().get("/api/updates/history")
            .then().statusCode(200).body("data", notNullValue());
        asRole("OPS_MANAGER").when().get("/api/updates/history")
            .then().statusCode(403);
    }

    @Test
    void currentInstalledReturnsNullWithoutHistory() {
        withAdmin()
        .when()
            .get("/api/updates/current")
        .then()
            .statusCode(200)
            .body("current", nullValue());
    }
}
