package com.eaglepoint.console.integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Object-level authorization for GET /api/exports/{id}:
 * only the user who initiated the export, or a SYSTEM_ADMIN, may read it.
 * Any other authenticated user receives 403.
 */
class ExportObjectAuthApiTest extends BaseIntegrationTest {

    @Test
    void crossUserExportAccessIsForbidden() {
        int jobId = asRole("OPS_MANAGER")
            .body(Map.of(
                "type", "CSV",
                "entityType", "COMMUNITIES",
                "destinationPath", "/tmp/exports"
            ))
        .when()
            .post("/api/exports")
        .then()
            .statusCode(201)
            .extract().path("export.id");

        // Different non-admin user tries to read it -> 403.
        asRole("REVIEWER")
        .when()
            .get("/api/exports/" + jobId)
        .then()
            .statusCode(403)
            .body("error.code", equalTo("FORBIDDEN"));
    }

    @Test
    void ownerCanReadOwnExport() {
        int jobId = asRole("OPS_MANAGER")
            .body(Map.of(
                "type", "CSV",
                "entityType", "COMMUNITIES",
                "destinationPath", "/tmp/exports"
            ))
        .when()
            .post("/api/exports")
        .then()
            .statusCode(201)
            .extract().path("export.id");

        asRole("OPS_MANAGER")
        .when()
            .get("/api/exports/" + jobId)
        .then()
            .statusCode(200)
            .body("export.id", equalTo(jobId));
    }

    @Test
    void systemAdminCanReadAnyExport() {
        int jobId = asRole("OPS_MANAGER")
            .body(Map.of(
                "type", "CSV",
                "entityType", "COMMUNITIES",
                "destinationPath", "/tmp/exports"
            ))
        .when()
            .post("/api/exports")
        .then()
            .statusCode(201)
            .extract().path("export.id");

        withAdmin()
        .when()
            .get("/api/exports/" + jobId)
        .then()
            .statusCode(200)
            .body("export.id", equalTo(jobId));
    }
}
