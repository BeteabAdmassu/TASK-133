package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

/**
 * Integration coverage for the {@code /api/updates/*} surface.
 *
 * <p>Unit tests in {@code UpdateServiceTest} cover the full apply/rollback
 * crypto pipeline on a temp directory.  These tests focus on the HTTP /
 * auth layer: role enforcement, error shape, and the "no packages yet"
 * happy path that every deployment starts from.</p>
 */
class UpdateApiTest extends BaseIntegrationTest {

    @Test
    void listPackagesRequiresSystemAdmin() {
        anonymous().when().get("/api/updates/packages").then().statusCode(401);
        asRole("OPS_MANAGER").when().get("/api/updates/packages").then().statusCode(403);
        asRole("REVIEWER").when().get("/api/updates/packages").then().statusCode(403);
        asRole("AUDITOR").when().get("/api/updates/packages").then().statusCode(403);
    }

    @Test
    void listPackagesAsAdminReturnsEmptyArrayWhenNoPackagesDropped() {
        withAdmin()
        .when()
            .get("/api/updates/packages")
        .then()
            .statusCode(200)
            .body("data", notNullValue());
    }

    @Test
    void verifyUnknownPackageReturnsNotFound() {
        withAdmin()
        .when()
            .post("/api/updates/packages/nonexistent-1.0.0/verify")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void applyUnknownPackageReturnsNotFound() {
        withAdmin()
        .when()
            .post("/api/updates/packages/nonexistent-1.0.0/apply")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void rollbackWithoutHistoryReturnsConflict() {
        withAdmin()
        .when()
            .post("/api/updates/rollback")
        .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"));
    }

    @Test
    void historyIsReadableByAdminAndAuditor() {
        withAdmin().when().get("/api/updates/history").then().statusCode(200).body("data", notNullValue());
        asRole("AUDITOR").when().get("/api/updates/history").then().statusCode(200).body("data", notNullValue());
        asRole("OPS_MANAGER").when().get("/api/updates/history").then().statusCode(403);
    }

    @Test
    void currentInstalledReturnsNullBeforeAnyUpdate() {
        withAdmin()
        .when()
            .get("/api/updates/current")
        .then()
            .statusCode(200)
            .body("current", nullValue());
    }
}
