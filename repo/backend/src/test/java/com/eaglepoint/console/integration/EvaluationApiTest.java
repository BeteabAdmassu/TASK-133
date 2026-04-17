package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class EvaluationApiTest extends BaseIntegrationTest {

    @Test
    void createCycleSucceeds() {
        withAdmin()
            .body(Map.of(
                "name", "Eval Cycle " + System.nanoTime(),
                "startDate", "2024-01-01",
                "endDate", "2024-03-31"
            ))
        .when()
            .post("/api/cycles")
        .then()
            .statusCode(201)
            .body("cycle.id", notNullValue())
            .body("cycle.status", equalTo("DRAFT"))
            .body("cycle.name", notNullValue());
    }

    @Test
    void listCyclesReturnsPagedData() {
        withAdmin()
        .when()
            .get("/api/cycles")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("page", notNullValue());
    }

    @Test
    void createCycleWithDuplicateNameReturns409() {
        String name = "Unique Cycle " + System.nanoTime();
        withAdmin()
            .body(Map.of("name", name, "startDate", "2024-01-01", "endDate", "2024-06-30"))
        .when()
            .post("/api/cycles")
        .then()
            .statusCode(201);

        withAdmin()
            .body(Map.of("name", name, "startDate", "2024-07-01", "endDate", "2024-12-31"))
        .when()
            .post("/api/cycles")
        .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"));
    }

    @Test
    void createTemplateForCycleSucceeds() {
        int cycleId = withAdmin()
            .body(Map.of(
                "name", "Template Cycle " + System.nanoTime(),
                "startDate", "2024-01-01",
                "endDate", "2024-06-30"
            ))
        .when()
            .post("/api/cycles")
        .then()
            .statusCode(201)
            .extract()
            .path("cycle.id");

        withAdmin()
            .body(Map.of("name", "SELF Template", "type", "SELF"))
        .when()
            .post("/api/cycles/" + cycleId + "/templates")
        .then()
            .statusCode(201)
            .body("template.id", notNullValue())
            .body("template.type", equalTo("SELF"));
    }

    @Test
    void listAppealsRequiresAuth() {
        given().when().get("/api/appeals").then().statusCode(401);
    }

    @Test
    void listReviewsReturnsPagedData() {
        withAdmin()
        .when()
            .get("/api/reviews")
        .then()
            .statusCode(200)
            .body("data", notNullValue());
    }
}
