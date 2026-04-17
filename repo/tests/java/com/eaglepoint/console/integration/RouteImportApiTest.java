package com.eaglepoint.console.integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class RouteImportApiTest extends BaseIntegrationTest {

    private static final String SAMPLE_CSV =
        "lat,lon,timestamp,sequence\n" +
        "37.7749,-122.4194,2024-01-01T10:00:00Z,1\n" +
        "37.7750,-122.4195,2024-01-01T10:01:00Z,2\n";

    @Test
    void listRouteImportsRequiresAuth() {
        given().when().get("/api/route-imports").then().statusCode(401);
    }

    @Test
    void listRouteImportsReturnsPagedData() {
        withAdmin()
        .when()
            .get("/api/route-imports")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("page", notNullValue())
            .body("totalPages", notNullValue());
    }

    @Test
    void uploadValidCsvSucceeds() {
        byte[] content = SAMPLE_CSV.getBytes(StandardCharsets.UTF_8);

        given()
            .header("Authorization", "Bearer " + adminToken())
            .contentType("multipart/form-data")
            .multiPart("file", "route.csv", content, "text/csv")
        .when()
            .post("/api/route-imports")
        .then()
            .statusCode(201)
            .body("import.id", notNullValue())
            .body("import.filename", equalTo("route.csv"));
    }

    @Test
    void uploadWithoutAuthReturns401() {
        given()
            .contentType("multipart/form-data")
            .multiPart("file", "route.csv", SAMPLE_CSV.getBytes(), "text/csv")
        .when()
            .post("/api/route-imports")
        .then()
            .statusCode(401);
    }

    @Test
    void uploadAsAuditorReturns403() {
        given()
            .header("Authorization", "Bearer " + tokenFor("AUDITOR"))
            .contentType("multipart/form-data")
            .multiPart("file", "route.csv", SAMPLE_CSV.getBytes(), "text/csv")
        .when()
            .post("/api/route-imports")
        .then()
            .statusCode(403);
    }

    @Test
    void uploadWithoutFileReturns400() {
        given()
            .header("Authorization", "Bearer " + adminToken())
            .contentType(ContentType.JSON)
            .body("{}")
        .when()
            .post("/api/route-imports")
        .then()
            .statusCode(anyOf(is(400), is(415)));
    }

    @Test
    void getImportByIdReturnsImport() {
        int importId = given()
            .header("Authorization", "Bearer " + adminToken())
            .contentType("multipart/form-data")
            .multiPart("file", "route.csv", SAMPLE_CSV.getBytes(), "text/csv")
        .when()
            .post("/api/route-imports")
        .then()
            .statusCode(201)
            .extract().path("import.id");

        withAdmin()
        .when()
            .get("/api/route-imports/" + importId)
        .then()
            .statusCode(200)
            .body("import.id", equalTo(importId))
            .body("import.filename", equalTo("route.csv"));
    }

    @Test
    void getImportByIdReturns404WhenMissing() {
        withAdmin()
        .when()
            .get("/api/route-imports/9999999")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void getCheckpointsForNonExistentImportReturns404() {
        withAdmin()
        .when()
            .get("/api/route-imports/9999999/checkpoints")
        .then()
            .statusCode(404);
    }
}
