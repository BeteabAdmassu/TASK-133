package com.eaglepoint.console.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

class BedApiTest extends BaseIntegrationTest {

    private int communityId;
    private int buildingId;
    private int roomId;

    @BeforeEach
    void setup() {
        communityId = withAdmin()
            .body(Map.of("name", "Bed Test Community " + System.nanoTime()))
        .when()
            .post("/api/communities")
        .then()
            .statusCode(201)
            .extract()
            .path("community.id");

        buildingId = withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "name", "Building A",
                "address", "1 Hospital Ave"
            ))
        .when()
            .post("/api/bed-buildings")
        .then()
            .statusCode(201)
            .extract()
            .path("building.id");

        roomId = withAdmin()
            .body(Map.of(
                "buildingId", buildingId,
                "roomNumber", "101",
                "floor", "1"
            ))
        .when()
            .post("/api/rooms")
        .then()
            .statusCode(201)
            .extract()
            .path("room.id");
    }

    @Test
    void createBedSucceeds() {
        withAdmin()
            .body(Map.of(
                "roomId", roomId,
                "bedLabel", "101-A",
                "bedType", "STANDARD"
            ))
        .when()
            .post("/api/beds")
        .then()
            .statusCode(201)
            .body("bed.id", notNullValue())
            .body("bed.bedLabel", equalTo("101-A"))
            .body("bed.state", equalTo("AVAILABLE"));
    }

    @Test
    void transitionBedFromAvailableToOccupied() {
        int bedId = withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", "101-B", "bedType", "STANDARD"))
        .when()
            .post("/api/beds")
        .then()
            .statusCode(201)
            .extract()
            .path("bed.id");

        withAdmin()
            .body(Map.of(
                "toState", "OCCUPIED",
                "residentId", "RESIDENT-001",
                "reason", "Admission",
                "notes", "New patient"
            ))
        .when()
            .post("/api/beds/" + bedId + "/transition")
        .then()
            .statusCode(200)
            .body("bed.state", equalTo("OCCUPIED"));
    }

    @Test
    void transitionBedFromOccupiedToOutOfServiceReturns409() {
        int bedId = withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", "101-C", "bedType", "STANDARD"))
        .when()
            .post("/api/beds")
        .then()
            .statusCode(201)
            .extract()
            .path("bed.id");

        // First transition to OCCUPIED
        withAdmin()
            .body(Map.of("toState", "OCCUPIED", "residentId", "RES-002", "reason", "Admit"))
        .when()
            .post("/api/beds/" + bedId + "/transition");

        // Then try OCCUPIED -> OUT_OF_SERVICE (should fail)
        withAdmin()
            .body(Map.of("toState", "OUT_OF_SERVICE", "reason", "Emergency"))
        .when()
            .post("/api/beds/" + bedId + "/transition")
        .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"));
    }

    @Test
    void listBedsReturnsAllBeds() {
        withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", "101-D", "bedType", "STANDARD"))
        .when()
            .post("/api/beds");

        withAdmin()
        .when()
            .get("/api/beds")
        .then()
            .statusCode(200)
            .body("data", notNullValue());
    }

    @Test
    void getBedHistoryReturnsTransitions() {
        int bedId = withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", "101-E", "bedType", "STANDARD"))
        .when()
            .post("/api/beds")
        .then()
            .statusCode(201)
            .extract()
            .path("bed.id");

        withAdmin()
            .body(Map.of("toState", "CLEANING", "reason", "Post-checkout"))
        .when()
            .post("/api/beds/" + bedId + "/transition");

        withAdmin()
        .when()
            .get("/api/beds/" + bedId + "/history")
        .then()
            .statusCode(200)
            .body("data", notNullValue());
    }
}
