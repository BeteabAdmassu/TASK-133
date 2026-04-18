package com.eaglepoint.console.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the manual-override endpoint:
 * {@code POST /api/pickup-points/{id}/override}.
 *
 * Only {@code SYSTEM_ADMIN} and {@code OPS_MANAGER} may apply or clear overrides.
 * Every override action must produce an audit trail entry.
 */
class PickupPointOverrideApiTest extends BaseIntegrationTest {

    private int communityId;
    private int ppId;

    @BeforeEach
    void setup() {
        communityId = withAdmin()
            .body(Map.of("name", unique("Override Community")))
        .when().post("/api/communities")
        .then().statusCode(201).extract().path("community.id");

        ppId = withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "address", "100 Override Ave",
                "zipCode", "20001",
                "streetRangeStart", "100",
                "streetRangeEnd", "199",
                "hoursJson", "{}",
                "capacity", 5
            ))
        .when().post("/api/pickup-points")
        .then().statusCode(201).extract().path("pickupPoint.id");
    }

    @Test
    void adminCanApplyManualOverride() {
        withAdmin()
            .body(Map.of(
                "manualOverride", true,
                "overrideNotes", "Road closure on Main St — rerouted per ops ticket #1234"
            ))
        .when()
            .post("/api/pickup-points/" + ppId + "/override")
        .then()
            .statusCode(200)
            .body("pickupPoint.manualOverride", equalTo(true))
            .body("pickupPoint.overrideNotes", equalTo("Road closure on Main St — rerouted per ops ticket #1234"))
            .body("pickupPoint.id", equalTo(ppId));
    }

    @Test
    void opsManagerCanApplyManualOverride() {
        asRole("OPS_MANAGER")
            .body(Map.of(
                "manualOverride", true,
                "overrideNotes", "Manager-approved reroute"
            ))
        .when()
            .post("/api/pickup-points/" + ppId + "/override")
        .then()
            .statusCode(200)
            .body("pickupPoint.manualOverride", equalTo(true));
    }

    @Test
    void auditorCannotApplyOverride() {
        asRole("AUDITOR")
            .body(Map.of("manualOverride", true, "overrideNotes", "Unauthorized"))
        .when()
            .post("/api/pickup-points/" + ppId + "/override")
        .then()
            .statusCode(403);
    }

    @Test
    void reviewerCannotApplyOverride() {
        asRole("REVIEWER")
            .body(Map.of("manualOverride", true, "overrideNotes", "Unauthorized"))
        .when()
            .post("/api/pickup-points/" + ppId + "/override")
        .then()
            .statusCode(403);
    }

    @Test
    void overrideNotesRequiredWhenManualOverrideTrue() {
        withAdmin()
            .body(Map.of("manualOverride", true))
        .when()
            .post("/api/pickup-points/" + ppId + "/override")
        .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void canClearOverrideWithoutNotes() {
        // First set it
        withAdmin()
            .body(Map.of("manualOverride", true, "overrideNotes", "Temp override"))
        .when().post("/api/pickup-points/" + ppId + "/override")
        .then().statusCode(200);

        // Then clear it
        withAdmin()
            .body(Map.of("manualOverride", false))
        .when()
            .post("/api/pickup-points/" + ppId + "/override")
        .then()
            .statusCode(200)
            .body("pickupPoint.manualOverride", equalTo(false));
    }

    @Test
    void overrideIsPersistedAndRetrievable() {
        withAdmin()
            .body(Map.of(
                "manualOverride", true,
                "overrideNotes", "Persisted override note"
            ))
        .when().post("/api/pickup-points/" + ppId + "/override")
        .then().statusCode(200);

        withAdmin()
        .when()
            .get("/api/pickup-points/" + ppId)
        .then()
            .statusCode(200)
            .body("pickupPoint.manualOverride", equalTo(true))
            .body("pickupPoint.overrideNotes", equalTo("Persisted override note"));
    }

    @Test
    void overrideActionAppearsInAuditTrail() {
        withAdmin()
            .body(Map.of(
                "manualOverride", true,
                "overrideNotes", "Audit trail test override"
            ))
        .when().post("/api/pickup-points/" + ppId + "/override")
        .then().statusCode(200);

        withAdmin()
            .queryParam("entityType", "PickupPoint")
            .queryParam("entityId", ppId)
        .when()
            .get("/api/audit-trail")
        .then()
            .statusCode(200)
            .body("data.findAll { it.action == 'OVERRIDE' }.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void overrideOnNonExistentPickupPointReturns404() {
        withAdmin()
            .body(Map.of("manualOverride", true, "overrideNotes", "Ghost override"))
        .when()
            .post("/api/pickup-points/999999999/override")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void unauthenticatedOverrideRequestReturns401() {
        anonymous()
            .body(Map.of("manualOverride", true, "overrideNotes", "No auth"))
        .when()
            .post("/api/pickup-points/" + ppId + "/override")
        .then()
            .statusCode(401);
    }
}
