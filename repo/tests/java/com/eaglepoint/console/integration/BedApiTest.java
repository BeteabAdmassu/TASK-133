package com.eaglepoint.console.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * Bed-management integration tests.
 *
 * <p>Uses the bed-buildings API contract: the POST body accepts
 * {@code serviceAreaId} (nullable) per the V1 schema
 * ({@code service_area_id INTEGER REFERENCES service_areas(id) ON DELETE SET NULL}).
 * Tests omit the foreign key where it is not required.</p>
 */
class BedApiTest extends BaseIntegrationTest {

    private int buildingId;
    private int roomId;

    @BeforeEach
    void setup() {
        buildingId = withAdmin()
            .body(Map.of(
                "name", unique("Building"),
                "address", "1 Hospital Ave"
            ))
        .when()
            .post("/api/bed-buildings")
        .then()
            .statusCode(201)
            .body("building.id", notNullValue())
            .body("building.name", notNullValue())
            .extract()
            .path("building.id");

        roomId = withAdmin()
            .body(Map.of(
                "buildingId", buildingId,
                "roomNumber", unique("R"),
                "floor", 1,
                "roomType", "STANDARD"
            ))
        .when()
            .post("/api/rooms")
        .then()
            .statusCode(201)
            .body("room.id", notNullValue())
            .body("room.buildingId", equalTo(buildingId))
            .extract()
            .path("room.id");
    }

    @Test
    void createBedSucceeds() {
        withAdmin()
            .body(Map.of(
                "roomId", roomId,
                "bedLabel", unique("BED"),
                "bedType", "STANDARD"
            ))
        .when()
            .post("/api/beds")
        .then()
            .statusCode(201)
            .body("bed.id", notNullValue())
            .body("bed.roomId", equalTo(roomId))
            .body("bed.state", equalTo("AVAILABLE"));
    }

    @Test
    void transitionBedFromAvailableToOccupied() {
        int bedId = withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", unique("BED"), "bedType", "STANDARD"))
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
            .body("bed.id", equalTo(bedId))
            .body("bed.state", equalTo("OCCUPIED"));
    }

    @Test
    void transitionBedFromOccupiedDirectlyToOutOfServiceIsForbidden() {
        int bedId = withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", unique("BED"), "bedType", "STANDARD"))
        .when()
            .post("/api/beds")
        .then()
            .statusCode(201)
            .extract()
            .path("bed.id");

        // Legal move: AVAILABLE -> OCCUPIED
        withAdmin()
            .body(Map.of("toState", "OCCUPIED", "residentId", "RES-002", "reason", "Admit"))
        .when()
            .post("/api/beds/" + bedId + "/transition")
        .then()
            .statusCode(200)
            .body("bed.state", equalTo("OCCUPIED"));

        // Illegal: OCCUPIED -> OUT_OF_SERVICE without a CLEANING checkpoint.
        withAdmin()
            .body(Map.of("toState", "OUT_OF_SERVICE", "reason", "Emergency"))
        .when()
            .post("/api/beds/" + bedId + "/transition")
        .then()
            .statusCode(anyOf(is(409), is(400)))
            .body("error.code", anyOf(
                equalTo("CONFLICT"),
                equalTo("INVALID_INPUT"),
                equalTo("VALIDATION_ERROR")));
    }

    @Test
    void listBedsReturnsPagedData() {
        withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", unique("BED"), "bedType", "STANDARD"))
        .when()
            .post("/api/beds")
        .then()
            .statusCode(201);

        withAdmin()
        .when()
            .get("/api/beds")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("page", notNullValue())
            .body("pageSize", notNullValue())
            .body("totalPages", notNullValue());
    }

    @Test
    void getBedByIdReturnsBed() {
        int bedId = withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", unique("BED"), "bedType", "STANDARD"))
        .when()
            .post("/api/beds")
        .then()
            .statusCode(201)
            .extract()
            .path("bed.id");

        withAdmin()
        .when()
            .get("/api/beds/" + bedId)
        .then()
            .statusCode(200)
            .body("bed.id", equalTo(bedId))
            .body("bed.state", equalTo("AVAILABLE"));
    }

    @Test
    void getBedHistoryReturnsTransitions() {
        int bedId = withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", unique("BED"), "bedType", "STANDARD"))
        .when()
            .post("/api/beds")
        .then()
            .statusCode(201)
            .extract()
            .path("bed.id");

        withAdmin()
            .body(Map.of("toState", "CLEANING", "reason", "Post-checkout"))
        .when()
            .post("/api/beds/" + bedId + "/transition")
        .then()
            .statusCode(200);

        withAdmin()
        .when()
            .get("/api/beds/" + bedId + "/history")
        .then()
            .statusCode(200)
            .body("data", notNullValue());
    }

    @Test
    void listBedsRequiresAuth() {
        anonymous()
        .when()
            .get("/api/beds")
        .then()
            .statusCode(401);
    }

    @Test
    void createBedWithoutPermissionIsForbidden() {
        asRole("AUDITOR")
            .body(Map.of("roomId", roomId, "bedLabel", unique("BED"), "bedType", "STANDARD"))
        .when()
            .post("/api/beds")
        .then()
            .statusCode(403);
    }
}
