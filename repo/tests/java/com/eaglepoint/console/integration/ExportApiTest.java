package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

class ExportApiTest extends BaseIntegrationTest {

    @Test
    void createExportRequiresAuth() {
        anonymous()
            .body(Map.of(
                "type", "CSV",
                "entityType", "COMMUNITIES",
                "destinationPath", "/tmp"
            ))
        .when()
            .post("/api/exports")
        .then()
            .statusCode(401);
    }

    @Test
    void auditorCannotInitiateExports() {
        asRole("AUDITOR")
            .body(Map.of(
                "type", "CSV",
                "entityType", "COMMUNITIES",
                "destinationPath", "/tmp"
            ))
        .when()
            .post("/api/exports")
        .then()
            .statusCode(403)
            .body("error.code", equalTo("FORBIDDEN"));
    }

    @Test
    void createExportJobAsAdminSucceeds() {
        int jobId = withAdmin()
            .body(Map.of(
                "type", "CSV",
                "entityType", "COMMUNITIES",
                "destinationPath", "/tmp/exports"
            ))
        .when()
            .post("/api/exports")
        .then()
            .statusCode(201)
            .body("export.id", notNullValue())
            .body("export.type", equalTo("CSV"))
            .body("export.entityType", equalTo("COMMUNITIES"))
            .body("export.status", notNullValue())
            .extract().path("export.id");

        withAdmin()
        .when()
            .get("/api/exports/" + jobId)
        .then()
            .statusCode(200)
            .body("export.id", equalTo(jobId))
            .body("export.entityType", equalTo("COMMUNITIES"));
    }

    @Test
    void getExportByIdReturns404WhenMissing() {
        withAdmin()
        .when()
            .get("/api/exports/9999999")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void opsManagerCanInitiateExport() {
        asRole("OPS_MANAGER")
            .body(Map.of(
                "type", "EXCEL",
                "entityType", "PICKUP_POINTS",
                "destinationPath", "/tmp"
            ))
        .when()
            .post("/api/exports")
        .then()
            .statusCode(201)
            .body("export.type", equalTo("EXCEL"));
    }

    @Test
    void createExportWithInvalidTypeReturnsValidationError() {
        withAdmin()
            .body(Map.of(
                "type", "BITMAP",
                "entityType", "COMMUNITIES",
                "destinationPath", "/tmp"
            ))
        .when()
            .post("/api/exports")
        .then()
            .statusCode(400);
    }

    @Test
    void createExportWithoutDestinationReturnsValidationError() {
        withAdmin()
            .body(Map.of(
                "type", "CSV",
                "entityType", "COMMUNITIES"
            ))
        .when()
            .post("/api/exports")
        .then()
            .statusCode(400);
    }
}
