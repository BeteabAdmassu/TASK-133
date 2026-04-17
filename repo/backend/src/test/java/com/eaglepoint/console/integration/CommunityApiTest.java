package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

class CommunityApiTest extends BaseIntegrationTest {

    @Test
    void createCommunitySucceeds() {
        withAdmin()
            .body(Map.of("name", "Test Community Alpha", "description", "A test community"))
        .when()
            .post("/api/communities")
        .then()
            .statusCode(201)
            .body("community.id", notNullValue())
            .body("community.name", equalTo("Test Community Alpha"))
            .body("community.status", equalTo("ACTIVE"));
    }

    @Test
    void createDuplicateCommunityReturns409() {
        withAdmin()
            .body(Map.of("name", "Unique Community Beta"))
        .when()
            .post("/api/communities");

        withAdmin()
            .body(Map.of("name", "Unique Community Beta"))
        .when()
            .post("/api/communities")
        .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"));
    }

    @Test
    void listCommunitiesRequiresAuth() {
        given().when().get("/api/communities").then().statusCode(401);
    }

    @Test
    void listCommunitiesReturnsPagedResponse() {
        withAdmin()
        .when()
            .get("/api/communities")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("page", notNullValue())
            .body("totalPages", notNullValue());
    }

    @Test
    void getCommunityByIdSucceeds() {
        int id = withAdmin()
            .body(Map.of("name", "Get By ID Community"))
        .when()
            .post("/api/communities")
        .then()
            .statusCode(201)
            .extract()
            .path("community.id");

        withAdmin()
        .when()
            .get("/api/communities/" + id)
        .then()
            .statusCode(200)
            .body("community.id", equalTo(id))
            .body("community.name", equalTo("Get By ID Community"));
    }

    @Test
    void getNonExistentCommunityReturns404() {
        withAdmin()
        .when()
            .get("/api/communities/99999")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void updateCommunitySucceeds() {
        int id = withAdmin()
            .body(Map.of("name", "Update Test Community"))
        .when()
            .post("/api/communities")
        .then()
            .statusCode(201)
            .extract()
            .path("community.id");

        withAdmin()
            .body(Map.of("name", "Updated Community Name", "description", "New desc"))
        .when()
            .put("/api/communities/" + id)
        .then()
            .statusCode(200)
            .body("community.name", equalTo("Updated Community Name"));
    }

    @Test
    void deleteCommunitySucceeds() {
        int id = withAdmin()
            .body(Map.of("name", "Delete Test Community"))
        .when()
            .post("/api/communities")
        .then()
            .statusCode(201)
            .extract()
            .path("community.id");

        withAdmin()
        .when()
            .delete("/api/communities/" + id)
        .then()
            .statusCode(204);
    }
}
