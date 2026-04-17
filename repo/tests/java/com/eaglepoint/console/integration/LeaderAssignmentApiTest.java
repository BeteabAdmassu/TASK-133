package com.eaglepoint.console.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

class LeaderAssignmentApiTest extends BaseIntegrationTest {

    private int communityId;
    private int serviceAreaId;
    private int opsUserId;

    @BeforeEach
    void seedCommunityAndServiceArea() {
        communityId = withAdmin()
            .body(Map.of("name", unique("LA Community")))
        .when()
            .post("/api/communities").then().statusCode(201)
            .extract().path("community.id");

        serviceAreaId = withAdmin()
            .body(Map.of("communityId", communityId, "name", unique("LA Area"), "description", "x"))
        .when()
            .post("/api/service-areas").then().statusCode(201)
            .extract().path("serviceArea.id");

        // The seeded ops_manager is a reasonable leader candidate.  Look up its id.
        opsUserId = withAdmin()
        .when()
            .get("/api/users?pageSize=50")
        .then()
            .statusCode(200)
            .extract()
            .path("data.find { it.username == 'ops_manager' }.id");
    }

    @Test
    void listAssignmentsRequiresAuth() {
        anonymous().when().get("/api/leader-assignments").then().statusCode(401);
    }

    @Test
    void listAssignmentsReturnsPagedData() {
        withAdmin()
        .when()
            .get("/api/leader-assignments")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("page", notNullValue())
            .body("totalPages", notNullValue());
    }

    @Test
    void assignLeaderSucceedsForAdmin() {
        withAdmin()
            .body(Map.of("serviceAreaId", serviceAreaId, "userId", opsUserId))
        .when()
            .post("/api/leader-assignments")
        .then()
            .statusCode(201)
            .body("assignment.id", notNullValue())
            .body("assignment.serviceAreaId", equalTo(serviceAreaId))
            .body("assignment.userId", equalTo(opsUserId));
    }

    @Test
    void assignLeaderRequiresElevatedRole() {
        asRole("AUDITOR")
            .body(Map.of("serviceAreaId", serviceAreaId, "userId", opsUserId))
        .when()
            .post("/api/leader-assignments")
        .then()
            .statusCode(403);
    }

    @Test
    void endAssignmentMovesItToClosed() {
        int aid = withAdmin()
            .body(Map.of("serviceAreaId", serviceAreaId, "userId", opsUserId))
        .when()
            .post("/api/leader-assignments").then().statusCode(201)
            .extract().path("assignment.id");

        withAdmin()
        .when()
            .put("/api/leader-assignments/" + aid + "/end")
        .then()
            .statusCode(200)
            .body("assignment.id", equalTo(aid))
            .body("assignment.unassignedAt", notNullValue());
    }
}
