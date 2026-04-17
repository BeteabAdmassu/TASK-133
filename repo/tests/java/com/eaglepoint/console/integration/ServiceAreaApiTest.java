package com.eaglepoint.console.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

class ServiceAreaApiTest extends BaseIntegrationTest {

    private int communityId;

    @BeforeEach
    void createCommunity() {
        communityId = withAdmin()
            .body(Map.of("name", unique("SA Community")))
        .when()
            .post("/api/communities")
        .then()
            .statusCode(201)
            .extract()
            .path("community.id");
    }

    @Test
    void listServiceAreasRequiresAuth() {
        anonymous().when().get("/api/service-areas").then().statusCode(401);
    }

    @Test
    void listServiceAreasAsAnyAuthenticatedUserSucceeds() {
        asRole("AUDITOR").when().get("/api/service-areas").then().statusCode(200)
            .body("data", notNullValue())
            .body("totalPages", notNullValue());
    }

    @Test
    void createServiceAreaRequiresElevatedRole() {
        asRole("AUDITOR")
            .body(Map.of("communityId", communityId, "name", unique("SA"), "description", "x"))
        .when()
            .post("/api/service-areas")
        .then()
            .statusCode(403);
    }

    @Test
    void createServiceAreaSucceedsForAdmin() {
        withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "name", unique("North Area"),
                "description", "North district coverage"
            ))
        .when()
            .post("/api/service-areas")
        .then()
            .statusCode(201)
            .body("serviceArea.id", notNullValue())
            .body("serviceArea.communityId", equalTo(communityId))
            .body("serviceArea.name", notNullValue());
    }

    @Test
    void createServiceAreaAsOpsManagerSucceeds() {
        asRole("OPS_MANAGER")
            .body(Map.of(
                "communityId", communityId,
                "name", unique("Ops Area"),
                "description", "Created by ops manager"
            ))
        .when()
            .post("/api/service-areas")
        .then()
            .statusCode(201)
            .body("serviceArea.name", notNullValue());
    }

    @Test
    void createServiceAreaWithUnknownCommunityReturns404() {
        withAdmin()
            .body(Map.of(
                "communityId", 999999,
                "name", unique("Orphan"),
                "description", "no parent"
            ))
        .when()
            .post("/api/service-areas")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void getServiceAreaByIdReturnsArea() {
        int id = withAdmin()
            .body(Map.of("communityId", communityId, "name", unique("SA"), "description", "x"))
        .when().post("/api/service-areas").then().statusCode(201)
            .extract().path("serviceArea.id");

        withAdmin()
        .when()
            .get("/api/service-areas/" + id)
        .then()
            .statusCode(200)
            .body("serviceArea.id", equalTo(id))
            .body("serviceArea.communityId", equalTo(communityId));
    }

    @Test
    void getServiceAreaByIdReturns404WhenMissing() {
        withAdmin()
        .when()
            .get("/api/service-areas/9999999")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void updateServiceAreaChangesName() {
        int id = withAdmin()
            .body(Map.of("communityId", communityId, "name", unique("Before"), "description", "x"))
        .when().post("/api/service-areas").then().statusCode(201)
            .extract().path("serviceArea.id");

        String newName = unique("After");
        withAdmin()
            .body(Map.of("name", newName, "description", "Updated"))
        .when()
            .put("/api/service-areas/" + id)
        .then()
            .statusCode(200)
            .body("serviceArea.id", equalTo(id))
            .body("serviceArea.name", equalTo(newName));
    }

    @Test
    void deleteServiceAreaRequiresAdminRole() {
        int id = withAdmin()
            .body(Map.of("communityId", communityId, "name", unique("ToDelete"), "description", "x"))
        .when().post("/api/service-areas").then().statusCode(201)
            .extract().path("serviceArea.id");

        asRole("OPS_MANAGER").when().delete("/api/service-areas/" + id).then().statusCode(403);
        withAdmin().when().delete("/api/service-areas/" + id).then().statusCode(204);
    }
}
