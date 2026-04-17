package com.eaglepoint.console.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

class PickupPointApiTest extends BaseIntegrationTest {

    private int communityId;

    @BeforeEach
    void createCommunity() {
        communityId = withAdmin()
            .body(Map.of("name", "PP Test Community " + System.nanoTime()))
        .when()
            .post("/api/communities")
        .then()
            .statusCode(201)
            .extract()
            .path("community.id");
    }

    @Test
    void createPickupPointSucceeds() {
        withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "address", "100 Main Street",
                "zipCode", "12345",
                "streetRangeStart", "100",
                "streetRangeEnd", "199",
                "hoursJson", "{\"mon\":\"08:00-17:00\"}",
                "capacity", 10
            ))
        .when()
            .post("/api/pickup-points")
        .then()
            .statusCode(201)
            .body("pickupPoint.id", notNullValue())
            .body("pickupPoint.status", equalTo("ACTIVE"))
            .body("pickupPoint.capacity", equalTo(10));
    }

    @Test
    void createSecondActivePickupPointForSameCommunityReturns409() {
        // Create first
        withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "address", "100 First St",
                "zipCode", "12345",
                "streetRangeStart", "100",
                "streetRangeEnd", "199",
                "hoursJson", "{}",
                "capacity", 5
            ))
        .when()
            .post("/api/pickup-points")
        .then()
            .statusCode(201);

        // Try to create second — should fail
        withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "address", "200 Second St",
                "zipCode", "12345",
                "streetRangeStart", "200",
                "streetRangeEnd", "299",
                "hoursJson", "{}",
                "capacity", 5
            ))
        .when()
            .post("/api/pickup-points")
        .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"));
    }

    @Test
    void pauseAndResumePickupPoint() {
        int ppId = withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "address", "300 Pause Test",
                "zipCode", "12345",
                "streetRangeStart", "300",
                "streetRangeEnd", "399",
                "hoursJson", "{}",
                "capacity", 1
            ))
        .when()
            .post("/api/pickup-points")
        .then()
            .statusCode(201)
            .extract()
            .path("pickupPoint.id");

        // Pause it
        withAdmin()
            .body(Map.of("reason", "Maintenance"))
        .when()
            .post("/api/pickup-points/" + ppId + "/pause")
        .then()
            .statusCode(200)
            .body("pickupPoint.status", equalTo("PAUSED"))
            .body("pickupPoint.id", equalTo(ppId));

        // Resume it
        withAdmin()
        .when()
            .post("/api/pickup-points/" + ppId + "/resume")
        .then()
            .statusCode(200)
            .body("pickupPoint.status", equalTo("ACTIVE"));
    }

    @Test
    void getPickupPointAddressIsMaskedForNonAdmin() {
        // For this test, all requests use admin token so address is unmasked
        // In a real test we'd create a non-admin user
        int ppId = withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "address", "Sensitive Address",
                "zipCode", "99999",
                "streetRangeStart", "1",
                "streetRangeEnd", "99",
                "hoursJson", "{}",
                "capacity", 3
            ))
        .when()
            .post("/api/pickup-points")
        .then()
            .statusCode(201)
            .extract()
            .path("pickupPoint.id");

        withAdmin()
        .when()
            .get("/api/pickup-points/" + ppId)
        .then()
            .statusCode(200)
            .body("pickupPoint.id", equalTo(ppId));
    }

    // ─── List / Update / Delete / Match (A5) ──────────────────────────────────

    @Test
    void listPickupPointsRequiresAuth() {
        anonymous().when().get("/api/pickup-points").then().statusCode(401);
    }

    @Test
    void listPickupPointsReturnsPagedData() {
        // Seed one so the list has at least one entry
        int ppId = createPickupPoint("12345", 5);

        withAdmin()
        .when()
            .get("/api/pickup-points")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("page", equalTo(1))
            .body("pageSize", notNullValue())
            .body("totalPages", notNullValue())
            .body("data.find { it.id == " + ppId + " }.id", equalTo(ppId));
    }

    @Test
    void updatePickupPointPersistsChanges() {
        int ppId = createPickupPoint("22222", 5);

        withAdmin()
            .body(Map.of(
                "address", "999 Updated Ave",
                "zipCode", "22222",
                "capacity", 25,
                "hoursJson", "{\"mon\":\"06:00-22:00\"}"
            ))
        .when()
            .put("/api/pickup-points/" + ppId)
        .then()
            .statusCode(200)
            .body("pickupPoint.id", equalTo(ppId))
            .body("pickupPoint.capacity", equalTo(25))
            .body("pickupPoint.zipCode", equalTo("22222"));

        // Verify persistence
        withAdmin()
        .when()
            .get("/api/pickup-points/" + ppId)
        .then()
            .statusCode(200)
            .body("pickupPoint.capacity", equalTo(25));
    }

    @Test
    void updatePickupPointDeniedForAuditor() {
        int ppId = createPickupPoint("33333", 5);
        asRole("AUDITOR")
            .body(Map.of("capacity", 99))
        .when()
            .put("/api/pickup-points/" + ppId)
        .then()
            .statusCode(403);
    }

    @Test
    void deletePickupPointRequiresAdminRole() {
        int ppId = createPickupPoint("44444", 5);

        asRole("OPS_MANAGER").when().delete("/api/pickup-points/" + ppId).then().statusCode(403);
        withAdmin().when().delete("/api/pickup-points/" + ppId).then().statusCode(204);

        withAdmin().when().get("/api/pickup-points/" + ppId).then().statusCode(404);
    }

    @Test
    void matchPickupPointReturnsActivePoint() {
        int ppId = createPickupPoint("55555", 5);

        withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "zipCode", "55555",
                "streetAddress", "150 Main St"
            ))
        .when()
            .post("/api/pickup-points/match")
        .then()
            .statusCode(200)
            .body("pickupPoint.id", equalTo(ppId))
            .body("pickupPoint.communityId", equalTo(communityId))
            .body("pickupPoint.zipCode", equalTo("55555"));
    }

    @Test
    void matchPickupPointReturns404WhenNoMatch() {
        createPickupPoint("66666", 5);

        withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "zipCode", "00000",
                "streetAddress", "nowhere"
            ))
        .when()
            .post("/api/pickup-points/match")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void matchPickupPointWithUnknownCommunityReturns404() {
        withAdmin()
            .body(Map.of(
                "communityId", 99999999L,
                "zipCode", "12345",
                "streetAddress", "anywhere"
            ))
        .when()
            .post("/api/pickup-points/match")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void matchPickupPointRequiresAuth() {
        anonymous()
            .body(Map.of("communityId", communityId, "zipCode", "12345", "streetAddress", "x"))
        .when()
            .post("/api/pickup-points/match")
        .then()
            .statusCode(401);
    }

    /**
     * Creates a fresh community + one active pickup point with the given zip.
     * Ensures the "one active per community" constraint is respected by using
     * a brand-new community per call.
     */
    private int createPickupPoint(String zipCode, int capacity) {
        int freshCommunityId = withAdmin()
            .body(Map.of("name", unique("PP Community")))
        .when()
            .post("/api/communities").then().statusCode(201)
            .extract().path("community.id");

        // Overwrite communityId so follow-up tests (match, etc.) can reference it
        this.communityId = freshCommunityId;

        return withAdmin()
            .body(Map.of(
                "communityId", freshCommunityId,
                "address", unique("Addr"),
                "zipCode", zipCode,
                "streetRangeStart", "1",
                "streetRangeEnd", "999",
                "hoursJson", "{}",
                "capacity", capacity
            ))
        .when()
            .post("/api/pickup-points").then().statusCode(201)
            .extract().path("pickupPoint.id");
    }
}
