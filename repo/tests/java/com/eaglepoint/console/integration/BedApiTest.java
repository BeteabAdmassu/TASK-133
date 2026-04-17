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
                "bedLabel", shortUnique("B"),
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
            .body(Map.of("roomId", roomId, "bedLabel", shortUnique("B"), "bedType", "STANDARD"))
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
            .body(Map.of("roomId", roomId, "bedLabel", shortUnique("B"), "bedType", "STANDARD"))
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
            .body(Map.of("roomId", roomId, "bedLabel", shortUnique("B"), "bedType", "STANDARD"))
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
            .body(Map.of("roomId", roomId, "bedLabel", shortUnique("B"), "bedType", "STANDARD"))
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
            .body(Map.of("roomId", roomId, "bedLabel", shortUnique("B"), "bedType", "STANDARD"))
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
            .body(Map.of("roomId", roomId, "bedLabel", shortUnique("B"), "bedType", "STANDARD"))
        .when()
            .post("/api/beds")
        .then()
            .statusCode(403);
    }

    // ─── Bed-building list/get/update/delete (A1) ──────────────────────────────

    @Test
    void listBedBuildingsReturnsPagedResponse() {
        withAdmin()
        .when()
            .get("/api/bed-buildings")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("data.size()", greaterThanOrEqualTo(1))
            .body("page", equalTo(1))
            .body("pageSize", notNullValue())
            .body("totalPages", notNullValue())
            .body("data.find { it.id == " + buildingId + " }.id", equalTo(buildingId));
    }

    @Test
    void listBedBuildingsRequiresAuth() {
        anonymous().when().get("/api/bed-buildings").then().statusCode(401);
    }

    @Test
    void getBedBuildingByIdReturnsBuilding() {
        withAdmin()
        .when()
            .get("/api/bed-buildings/" + buildingId)
        .then()
            .statusCode(200)
            .body("building.id", equalTo(buildingId))
            .body("building.name", notNullValue())
            .body("building.address", equalTo("1 Hospital Ave"));
    }

    @Test
    void getBedBuildingByIdReturns404WhenMissing() {
        withAdmin()
        .when()
            .get("/api/bed-buildings/99999999")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void updateBedBuildingPersistsChanges() {
        String newName = unique("Renamed");
        withAdmin()
            .body(Map.of("name", newName, "address", "2 New Wing"))
        .when()
            .put("/api/bed-buildings/" + buildingId)
        .then()
            .statusCode(200)
            .body("building.id", equalTo(buildingId))
            .body("building.name", equalTo(newName))
            .body("building.address", equalTo("2 New Wing"));

        withAdmin()
        .when()
            .get("/api/bed-buildings/" + buildingId)
        .then()
            .statusCode(200)
            .body("building.name", equalTo(newName));
    }

    @Test
    void updateBedBuildingDeniedForAuditor() {
        asRole("AUDITOR")
            .body(Map.of("name", unique("Nope"), "address", "blocked"))
        .when()
            .put("/api/bed-buildings/" + buildingId)
        .then()
            .statusCode(403);
    }

    @Test
    void deleteBedBuildingRequiresAdminRole() {
        // Create a throwaway building (room/bed setup is only tied to class-level buildingId)
        int throwaway = withAdmin()
            .body(Map.of("name", unique("DelBuilding"), "address", "xx"))
        .when()
            .post("/api/bed-buildings").then().statusCode(201)
            .extract().path("building.id");

        asRole("OPS_MANAGER").when().delete("/api/bed-buildings/" + throwaway).then().statusCode(403);
        withAdmin().when().delete("/api/bed-buildings/" + throwaway).then().statusCode(204);

        withAdmin().when().get("/api/bed-buildings/" + throwaway)
            .then().statusCode(404);
    }

    // ─── Room list/get/update/delete (A2) ──────────────────────────────────────

    @Test
    void listRoomsReturnsPagedResponse() {
        withAdmin()
        .when()
            .get("/api/rooms")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("page", equalTo(1))
            .body("totalPages", notNullValue())
            .body("data.find { it.id == " + roomId + " }.id", equalTo(roomId));
    }

    @Test
    void listRoomsByBuildingFiltersCorrectly() {
        withAdmin()
        .when()
            .get("/api/rooms?buildingId=" + buildingId)
        .then()
            .statusCode(200)
            .body("data.every { it.buildingId == " + buildingId + " }", equalTo(true));
    }

    @Test
    void getRoomByIdReturnsRoom() {
        withAdmin()
        .when()
            .get("/api/rooms/" + roomId)
        .then()
            .statusCode(200)
            .body("room.id", equalTo(roomId))
            .body("room.buildingId", equalTo(buildingId))
            .body("room.floor", equalTo(1));
    }

    @Test
    void getRoomByIdReturns404WhenMissing() {
        withAdmin()
        .when()
            .get("/api/rooms/99999999")
        .then()
            .statusCode(404);
    }

    @Test
    void updateRoomPersistsChanges() {
        String newNumber = unique("R");
        withAdmin()
            .body(Map.of("roomNumber", newNumber, "floor", 3, "roomType", "ICU"))
        .when()
            .put("/api/rooms/" + roomId)
        .then()
            .statusCode(200)
            .body("room.id", equalTo(roomId))
            .body("room.roomNumber", equalTo(newNumber))
            .body("room.floor", equalTo(3))
            .body("room.roomType", equalTo("ICU"));

        withAdmin()
        .when()
            .get("/api/rooms/" + roomId)
        .then()
            .statusCode(200)
            .body("room.roomNumber", equalTo(newNumber))
            .body("room.floor", equalTo(3));
    }

    @Test
    void deleteRoomRequiresAdminRole() {
        int throwawayRoom = withAdmin()
            .body(Map.of("buildingId", buildingId, "roomNumber", unique("R"), "floor", 2, "roomType", "STANDARD"))
        .when()
            .post("/api/rooms").then().statusCode(201)
            .extract().path("room.id");

        asRole("OPS_MANAGER").when().delete("/api/rooms/" + throwawayRoom).then().statusCode(403);
        withAdmin().when().delete("/api/rooms/" + throwawayRoom).then().statusCode(204);

        withAdmin().when().get("/api/rooms/" + throwawayRoom)
            .then().statusCode(404);
    }

    // ─── Bed update/delete (A3) ────────────────────────────────────────────────

    @Test
    void updateBedLabelPersists() {
        // Bed labels are capped at 20 chars by BedService — use a short prefix
        // so the nanoTime suffix still fits.
        int bedId = withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", shortUnique("O"), "bedType", "STANDARD"))
        .when()
            .post("/api/beds").then().statusCode(201)
            .extract().path("bed.id");

        String newLabel = shortUnique("N");
        withAdmin()
            .body(Map.of("bedLabel", newLabel))
        .when()
            .put("/api/beds/" + bedId)
        .then()
            .statusCode(200)
            .body("bed.id", equalTo(bedId))
            .body("bed.bedLabel", equalTo(newLabel));

        withAdmin()
        .when()
            .get("/api/beds/" + bedId)
        .then()
            .statusCode(200)
            .body("bed.bedLabel", equalTo(newLabel));
    }

    @Test
    void listBedsByRoomIdFiltersCorrectly() {
        // Create a second room in the same building with its own bed, then
        // verify ?roomId=<primary> returns only the primary room's beds.
        int otherRoomId = withAdmin()
            .body(Map.of("buildingId", buildingId, "roomNumber", unique("R"), "floor", 2, "roomType", "STANDARD"))
        .when().post("/api/rooms").then().statusCode(201)
            .extract().path("room.id");

        int bedInRoomA = withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", shortUnique("A"), "bedType", "STANDARD"))
        .when().post("/api/beds").then().statusCode(201)
            .extract().path("bed.id");
        int bedInRoomB = withAdmin()
            .body(Map.of("roomId", otherRoomId, "bedLabel", shortUnique("B"), "bedType", "STANDARD"))
        .when().post("/api/beds").then().statusCode(201)
            .extract().path("bed.id");

        withAdmin()
        .when()
            .get("/api/beds?roomId=" + roomId + "&pageSize=500")
        .then()
            .statusCode(200)
            .body("data.every { it.roomId == " + roomId + " }", equalTo(true))
            .body("data.find { it.id == " + bedInRoomA + " }.id", equalTo(bedInRoomA))
            .body("data.find { it.id == " + bedInRoomB + " }", nullValue());
    }

    @Test
    void listBedsByBuildingIdFiltersCorrectly() {
        // Create a second building + room + bed to confirm the join excludes it.
        int otherBuildingId = withAdmin()
            .body(Map.of("name", unique("BldgB"), "address", "2 Other"))
        .when().post("/api/bed-buildings").then().statusCode(201)
            .extract().path("building.id");
        int otherRoomId = withAdmin()
            .body(Map.of("buildingId", otherBuildingId, "roomNumber", unique("R"), "floor", 1, "roomType", "STANDARD"))
        .when().post("/api/rooms").then().statusCode(201)
            .extract().path("room.id");
        int bedInOther = withAdmin()
            .body(Map.of("roomId", otherRoomId, "bedLabel", shortUnique("O"), "bedType", "STANDARD"))
        .when().post("/api/beds").then().statusCode(201)
            .extract().path("bed.id");

        // Bed inside THIS class's setUp() building
        int bedInPrimary = withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", shortUnique("P"), "bedType", "STANDARD"))
        .when().post("/api/beds").then().statusCode(201)
            .extract().path("bed.id");

        withAdmin()
        .when()
            .get("/api/beds?buildingId=" + buildingId + "&pageSize=500")
        .then()
            .statusCode(200)
            .body("data.find { it.id == " + bedInPrimary + " }.id", equalTo(bedInPrimary))
            .body("data.find { it.id == " + bedInOther + " }", nullValue());
    }

    @Test
    void listBedsWithUnknownRoomIdReturns404() {
        withAdmin()
        .when()
            .get("/api/beds?roomId=99999999")
        .then()
            .statusCode(404);
    }

    /** Short unique that fits the bed_label 20-char column. */
    private static String shortUnique(String prefix) {
        String tail = Long.toHexString(System.nanoTime()); // up to 16 hex chars
        String full = prefix + "-" + tail;
        return full.length() <= 20 ? full : full.substring(0, 20);
    }

    @Test
    void updateBedDeniedForAuditor() {
        int bedId = withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", shortUnique("B"), "bedType", "STANDARD"))
        .when()
            .post("/api/beds").then().statusCode(201)
            .extract().path("bed.id");

        asRole("AUDITOR")
            .body(Map.of("bedLabel", "hacker"))
        .when()
            .put("/api/beds/" + bedId)
        .then()
            .statusCode(403);
    }

    @Test
    void deleteBedRequiresAdminRole() {
        int bedId = withAdmin()
            .body(Map.of("roomId", roomId, "bedLabel", shortUnique("B"), "bedType", "STANDARD"))
        .when()
            .post("/api/beds").then().statusCode(201)
            .extract().path("bed.id");

        // OPS_MANAGER is not allowed to delete beds (SYSTEM_ADMIN only)
        asRole("OPS_MANAGER").when().delete("/api/beds/" + bedId).then().statusCode(403);
        withAdmin().when().delete("/api/beds/" + bedId).then().statusCode(204);

        // After delete, GET returns 404
        withAdmin().when().get("/api/beds/" + bedId)
            .then().statusCode(404);
    }
}
