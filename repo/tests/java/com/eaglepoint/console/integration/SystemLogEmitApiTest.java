package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * Verifies that key business events are persisted to the {@code system_logs} table
 * and returned by {@code GET /api/logs}.
 */
class SystemLogEmitApiTest extends BaseIntegrationTest {

    @Test
    void createPickupPointEmitsSystemLog() {
        int communityId = withAdmin()
            .body(Map.of("name", unique("SysLog Community")))
        .when().post("/api/communities")
        .then().statusCode(201).extract().path("community.id");

        withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "address", "100 Logtest St",
                "zipCode", "30001",
                "streetRangeStart", "100",
                "streetRangeEnd", "199",
                "hoursJson", "{}",
                "capacity", 5
            ))
        .when().post("/api/pickup-points")
        .then().statusCode(201);

        // The /api/logs endpoint must return at least one entry (the pickup point creation)
        withAdmin()
            .queryParam("category", "BUSINESS")
        .when()
            .get("/api/logs")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("data.size()", greaterThanOrEqualTo(1))
            .body("data.find { it.entityType == 'PickupPoint' }.entityType", equalTo("PickupPoint"));
    }

    @Test
    void logsEndpointRequiresAdminOrAuditorRole() {
        // Auditor can read logs
        asRole("AUDITOR")
        .when()
            .get("/api/logs")
        .then()
            .statusCode(200);

        // DATA_INTEGRATOR cannot
        asRole("DATA_INTEGRATOR")
        .when()
            .get("/api/logs")
        .then()
            .statusCode(403);
    }

    @Test
    void logsEndpointSupportsLevelFilter() {
        withAdmin()
            .queryParam("level", "INFO")
        .when()
            .get("/api/logs")
        .then()
            .statusCode(200)
            .body("data", notNullValue());
    }

    @Test
    void logsEndpointSupportsCategoryFilter() {
        withAdmin()
            .queryParam("category", "SYSTEM")
        .when()
            .get("/api/logs")
        .then()
            .statusCode(200)
            .body("data", notNullValue());
    }

    @Test
    void logsReturnPaginatedResponse() {
        withAdmin()
        .when()
            .get("/api/logs")
        .then()
            .statusCode(200)
            .body("page", equalTo(1))
            .body("pageSize", notNullValue())
            .body("totalPages", notNullValue());
    }
}
