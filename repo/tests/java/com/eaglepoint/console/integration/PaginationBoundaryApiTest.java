package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

/**
 * Pagination boundaries must be enforced uniformly across list endpoints:
 * {@code page >= 1} and {@code 1 <= pageSize <= 500}.  Violations must
 * return 400 with a structured validation error, never silently clamp.
 */
class PaginationBoundaryApiTest extends BaseIntegrationTest {

    @Test
    void pageZeroRejected() {
        withAdmin()
            .queryParam("page", 0)
        .when()
            .get("/api/communities")
        .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_ERROR"))
            .body("error.fields.page", notNullValue());
    }

    @Test
    void negativePageRejected() {
        withAdmin()
            .queryParam("page", -5)
        .when()
            .get("/api/communities")
        .then()
            .statusCode(400);
    }

    @Test
    void pageSizeZeroRejected() {
        withAdmin()
            .queryParam("pageSize", 0)
        .when()
            .get("/api/communities")
        .then()
            .statusCode(400)
            .body("error.fields.pageSize", notNullValue());
    }

    @Test
    void pageSizeAboveMaxRejected() {
        withAdmin()
            .queryParam("pageSize", 501)
        .when()
            .get("/api/communities")
        .then()
            .statusCode(400)
            .body("error.fields.pageSize", containsString("<= 500"));
    }

    @Test
    void nonIntegerPageRejected() {
        withAdmin()
            .queryParam("page", "abc")
        .when()
            .get("/api/communities")
        .then()
            .statusCode(400);
    }

    @Test
    void validBoundariesAccepted() {
        withAdmin()
            .queryParam("page", 1)
            .queryParam("pageSize", 500)
        .when()
            .get("/api/communities")
        .then()
            .statusCode(200)
            .body("page", equalTo(1));
    }
}
