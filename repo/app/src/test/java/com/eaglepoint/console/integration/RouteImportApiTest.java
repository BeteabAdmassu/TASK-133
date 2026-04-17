package com.eaglepoint.console.integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class RouteImportApiTest extends BaseIntegrationTest {

    @Test
    void listRouteImportsRequiresAuth() {
        given().when().get("/api/route-imports").then().statusCode(401);
    }

    @Test
    void listRouteImportsWithAuthSucceeds() {
        withAdmin()
        .when()
            .get("/api/route-imports")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("page", notNullValue());
    }

    @Test
    void uploadValidCsvSucceeds() throws Exception {
        String csv = "lat,lon,timestamp,sequence\n37.7749,-122.4194,2024-01-01T10:00:00Z,1\n" +
                     "37.7750,-122.4195,2024-01-01T10:01:00Z,2\n";
        byte[] content = csv.getBytes(StandardCharsets.UTF_8);

        given()
            .header("Authorization", "Bearer " + adminToken)
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
            .multiPart("file", "route.csv", "data".getBytes(), "text/csv")
        .when()
            .post("/api/route-imports")
        .then()
            .statusCode(401);
    }

    @Test
    void getCheckpointsForNonExistentImportReturns404() {
        withAdmin()
        .when()
            .get("/api/route-imports/99999/checkpoints")
        .then()
            .statusCode(404);
    }
}
