package com.eaglepoint.console.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * Integration tests proving that manual-override semantics are honoured by the
 * {@code POST /api/pickup-points/match} endpoint.
 *
 * <h3>Override precedence contract under test</h3>
 * <ul>
 *   <li>An ACTIVE pickup point with {@code manualOverride=true} is selected
 *       before ZIP / street-range / geozone matching fires.</li>
 *   <li>A PAUSED override point is ineligible; normal matching runs instead.</li>
 *   <li>Multiple override candidates resolve via lowest-id tie-break.</li>
 *   <li>Only SYSTEM_ADMIN / OPS_MANAGER may set the override flag (403 otherwise).</li>
 *   <li>Every override-driven match is traceable in system_logs.</li>
 *   <li>The match response always includes a {@code matchedViaOverride} boolean.</li>
 * </ul>
 */
class PickupPointOverrideMatchApiTest extends BaseIntegrationTest {

    private int communityId;
    private int ppId;

    @BeforeEach
    void setup() {
        // Fresh community + one ACTIVE pickup point per test to stay within the
        // one-active-per-community-per-day rule.
        communityId = withAdmin()
            .body(Map.of("name", unique("Override-Match Community")))
        .when().post("/api/communities")
        .then().statusCode(201).extract().path("community.id");

        ppId = withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "address", "100 Override Blvd",
                "zipCode", "40001",
                "streetRangeStart", "100",
                "streetRangeEnd", "199",
                "hoursJson", "{}",
                "capacity", 5
            ))
        .when().post("/api/pickup-points")
        .then().statusCode(201).extract().path("pickupPoint.id");
    }

    // ─── Test 1: override candidate selected over normal geographic match ──────

    @Test
    void overrideCandidateReturnedForNonMatchingZip() {
        // PP's ZIP is "40001"; request ZIP is "99999" — no normal match.
        // After setting override, PP must be returned anyway.
        withAdmin()
            .body(Map.of("manualOverride", true, "overrideNotes", "Road closure — override active"))
        .when().post("/api/pickup-points/" + ppId + "/override")
        .then().statusCode(200);

        withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "zipCode", "99999",          // does NOT match PP's zipCode "40001"
                "streetAddress", "999 Nowhere Rd"
            ))
        .when().post("/api/pickup-points/match")
        .then()
            .statusCode(200)
            .body("pickupPoint.id", equalTo(ppId))
            .body("pickupPoint.manualOverride", equalTo(true))
            .body("matchedViaOverride", equalTo(true));
    }

    @Test
    void withoutOverrideNonMatchingZipReturns404() {
        // Same scenario without the override flag: normal matching fails.
        withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "zipCode", "99999",
                "streetAddress", "999 Nowhere Rd"
            ))
        .when().post("/api/pickup-points/match")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void matchWithOverrideResponseAlwaysIncludesMatchedViaOverrideField() {
        // Normal match (override not set): matchedViaOverride must be false
        withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "zipCode", "40001",     // matches the PP's ZIP normally
                "streetAddress", "150 Override Blvd"
            ))
        .when().post("/api/pickup-points/match")
        .then()
            .statusCode(200)
            .body("pickupPoint.id", equalTo(ppId))
            .body("matchedViaOverride", equalTo(false));
    }

    // ─── Test 2: PAUSED override point is not eligible ────────────────────────

    @Test
    void pausedOverrideCandidateDoesNotFireOverridePass() {
        // Set override, then pause the pickup point.
        withAdmin()
            .body(Map.of("manualOverride", true, "overrideNotes", "Override before pause"))
        .when().post("/api/pickup-points/" + ppId + "/override")
        .then().statusCode(200);

        withAdmin()
            .body(Map.of("reason", "Paused for test"))
        .when().post("/api/pickup-points/" + ppId + "/pause")
        .then().statusCode(200).body("pickupPoint.status", equalTo("PAUSED"));

        // Now match — override point is PAUSED, ineligible.
        // No other active point exists → 404 from normal pass.
        withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "zipCode", "99999",
                "streetAddress", "Anywhere"
            ))
        .when().post("/api/pickup-points/match")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void pausedOverrideCandidateFallsBackToNormalMatchOnResume() {
        // Set override, pause, then resume (same day = allowed).
        withAdmin()
            .body(Map.of("manualOverride", true, "overrideNotes", "Override before pause test"))
        .when().post("/api/pickup-points/" + ppId + "/override")
        .then().statusCode(200);

        withAdmin()
            .body(Map.of("reason", "Brief pause"))
        .when().post("/api/pickup-points/" + ppId + "/pause")
        .then().statusCode(200);

        withAdmin()
        .when().post("/api/pickup-points/" + ppId + "/resume")
        .then().statusCode(200).body("pickupPoint.status", equalTo("ACTIVE"));

        // After resume, override flag still set → override pass fires
        withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "zipCode", "99999",
                "streetAddress", "Anywhere"
            ))
        .when().post("/api/pickup-points/match")
        .then()
            .statusCode(200)
            .body("pickupPoint.id", equalTo(ppId))
            .body("matchedViaOverride", equalTo(true));
    }

    // ─── Test 3: deterministic tie-break (lowest id wins) — unit level ────────
    // Full multi-override scenario requires >1 ACTIVE pickup in same community
    // which is blocked by the per-day rule in integration.  The unit test in
    // PickupPointOverrideMatchServiceTest.java covers the multi-candidate path.

    @Test
    void overrideResponseSchemaIsStable() {
        withAdmin()
            .body(Map.of("manualOverride", true, "overrideNotes", "Schema test"))
        .when().post("/api/pickup-points/" + ppId + "/override")
        .then().statusCode(200);

        withAdmin()
            .body(Map.of("communityId", communityId, "zipCode", "40001", "streetAddress", "x"))
        .when().post("/api/pickup-points/match")
        .then()
            .statusCode(200)
            .body("pickupPoint", notNullValue())
            .body("pickupPoint.id", notNullValue())
            .body("pickupPoint.status", equalTo("ACTIVE"))
            .body("pickupPoint.manualOverride", equalTo(true))
            .body("pickupPoint.overrideNotes", equalTo("Schema test"))
            .body("matchedViaOverride", equalTo(true));
    }

    // ─── Test 4: authorization guard on override set endpoint (retained) ──────

    @Test
    void auditorCannotSetOverride() {
        asRole("AUDITOR")
            .body(Map.of("manualOverride", true, "overrideNotes", "Unauthorized"))
        .when().post("/api/pickup-points/" + ppId + "/override")
        .then().statusCode(403);
    }

    @Test
    void reviewerCannotSetOverride() {
        asRole("REVIEWER")
            .body(Map.of("manualOverride", true, "overrideNotes", "Unauthorized"))
        .when().post("/api/pickup-points/" + ppId + "/override")
        .then().statusCode(403);
    }

    @Test
    void dataIntegratorCannotSetOverride() {
        asRole("DATA_INTEGRATOR")
            .body(Map.of("manualOverride", true, "overrideNotes", "Unauthorized"))
        .when().post("/api/pickup-points/" + ppId + "/override")
        .then().statusCode(403);
    }

    // ─── Test 5 + 6: match response correctness + audit/log traceability ─────

    @Test
    void overrideDrivenMatchIsTracedInSystemLogs() {
        withAdmin()
            .body(Map.of("manualOverride", true, "overrideNotes", "Log trace test"))
        .when().post("/api/pickup-points/" + ppId + "/override")
        .then().statusCode(200);

        // Perform a match that fires the override pass
        withAdmin()
            .body(Map.of("communityId", communityId, "zipCode", "88888", "streetAddress", "any"))
        .when().post("/api/pickup-points/match")
        .then().statusCode(200).body("matchedViaOverride", equalTo(true));

        // The system_logs table must have a BUSINESS entry for the override match
        withAdmin()
            .queryParam("category", "BUSINESS")
        .when().get("/api/logs")
        .then()
            .statusCode(200)
            .body("data.findAll { it.entityType == 'PickupPoint' && it.entityId == " + ppId + " }.size()",
                greaterThanOrEqualTo(1));
    }

    @Test
    void clearingOverrideSwitchesMatchBackToNormalPath() {
        // Set then immediately clear the override
        withAdmin()
            .body(Map.of("manualOverride", true, "overrideNotes", "Temp override"))
        .when().post("/api/pickup-points/" + ppId + "/override")
        .then().statusCode(200);

        withAdmin()
            .body(Map.of("manualOverride", false))
        .when().post("/api/pickup-points/" + ppId + "/override")
        .then().statusCode(200).body("pickupPoint.manualOverride", equalTo(false));

        // Normal pass now governs; non-matching ZIP → 404
        withAdmin()
            .body(Map.of("communityId", communityId, "zipCode", "99999", "streetAddress", "anywhere"))
        .when().post("/api/pickup-points/match")
        .then()
            .statusCode(404);
    }
}
