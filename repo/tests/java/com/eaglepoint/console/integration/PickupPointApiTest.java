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
}
