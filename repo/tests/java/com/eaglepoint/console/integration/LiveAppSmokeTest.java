package com.eaglepoint.console.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * Smoke tests that hit the <strong>live compose container</strong> (not the
 * embedded per-JVM server used by the other integration tests).
 *
 * <p>These tests verify real deployment concerns: port mapping, migrations,
 * seeded credentials, role enforcement, and error shape against the actual
 * image that was just built by {@code docker compose up --build -d}.</p>
 *
 * <p>Enabled only when {@code LIVE_APP_URL} is set in the environment, e.g.
 * {@code LIVE_APP_URL=http://host.docker.internal:8080} when invoked from the
 * test container via {@code run_tests.sh}.  Without that variable, the class
 * is skipped so it never fails on a workstation that is not running compose.</p>
 */
@EnabledIfEnvironmentVariable(named = "LIVE_APP_URL", matches = "https?://.+")
class LiveAppSmokeTest {

    private static URI baseUri;
    private static String savedBaseUri;
    private static int savedPort;

    @BeforeAll
    static void pointAtLiveContainer() {
        String url = System.getenv("LIVE_APP_URL");
        baseUri = URI.create(url);
        // Save the shared RestAssured defaults so the embedded-server tests in
        // other classes continue to use 127.0.0.1:18080 after this class runs.
        savedBaseUri = RestAssured.baseURI;
        savedPort = RestAssured.port;
    }

    @AfterAll
    static void restoreDefaults() {
        RestAssured.baseURI = savedBaseUri;
        RestAssured.port = savedPort;
    }

    /** Build a RequestSpecification that targets the live container explicitly. */
    private static RequestSpecification live() {
        int port = baseUri.getPort() > 0 ? baseUri.getPort() : 8080;
        return RestAssured.given()
            .baseUri(baseUri.getScheme() + "://" + baseUri.getHost())
            .port(port);
    }

    private static RequestSpecification given() {
        return live();
    }

    // ─── Health ───────────────────────────────────────────────────────────────

    @Test
    void healthEndpointReportsLiveContainerUp() {
        given()
        .when()
            .get("/api/health")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("db", equalTo("OK"))
            .body("version", notNullValue())
            .body("uptime", notNullValue());
    }

    // ─── Auth ─────────────────────────────────────────────────────────────────

    @Test
    void loginWithSeededAdminReturnsToken() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "admin", "password", "Admin1234!"))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("token", not(emptyString()))
            .body("user.username", equalTo("admin"))
            .body("user.role", equalTo("SYSTEM_ADMIN"))
            .body("expiresAt", notNullValue());
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "admin", "password", "definitely-wrong"))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void getMeWithTokenReturnsCurrentUser() {
        String token = loginAs("admin", "Admin1234!");
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/auth/me")
        .then()
            .statusCode(200)
            .body("user.username", equalTo("admin"))
            .body("user.role", equalTo("SYSTEM_ADMIN"))
            .body("user.id", notNullValue());
    }

    @Test
    void getMeWithoutTokenReturns401() {
        given()
        .when()
            .get("/api/auth/me")
        .then()
            .statusCode(401);
    }

    // ─── Community CRUD ───────────────────────────────────────────────────────

    @Test
    void listCommunitiesReturnsPagedResponse() {
        String token = loginAs("admin", "Admin1234!");
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/communities")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("page", notNullValue())
            .body("pageSize", notNullValue())
            .body("totalPages", notNullValue());
    }

    @Test
    void createAndGetCommunityRoundTripsAgainstLiveContainer() {
        String token = loginAs("admin", "Admin1234!");
        String name = "Live Smoke Community " + System.nanoTime();

        int id = given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body(Map.of("name", name, "description", "created by live smoke test"))
        .when()
            .post("/api/communities")
        .then()
            .statusCode(201)
            .body("community.id", notNullValue())
            .body("community.name", equalTo(name))
            .body("community.status", equalTo("ACTIVE"))
            .extract().path("community.id");

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/communities/" + id)
        .then()
            .statusCode(200)
            .body("community.id", equalTo(id))
            .body("community.name", equalTo(name));
    }

    // ─── Role-based access ────────────────────────────────────────────────────

    @Test
    void auditorCannotListUsers() {
        String token = loginAs("auditor", "Auditor1234!");
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/users")
        .then()
            .statusCode(403);
    }

    @Test
    void auditorCanReadAuditTrail() {
        String token = loginAs("auditor", "Auditor1234!");
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/audit-trail")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("totalPages", notNullValue());
    }

    @Test
    void auditorCannotInitiateExports() {
        String token = loginAs("auditor", "Auditor1234!");
        given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "type", "CSV",
                "entityType", "COMMUNITIES",
                "destinationPath", "/tmp"
            ))
        .when()
            .post("/api/exports")
        .then()
            .statusCode(403)
            .body("error.code", equalTo("FORBIDDEN"));
    }

    // ─── Unauthorized / logout ────────────────────────────────────────────────

    @Test
    void listCommunitiesWithoutTokenReturns401() {
        given()
        .when()
            .get("/api/communities")
        .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void rateLimiterEnforces60RequestsPerMinute() throws InterruptedException {
        // Use data_integrator so admin's quota is untouched for other smoke assertions.
        String token = loginAs("data_integrator", "Integrator1234!");

        int total = 75;
        java.util.concurrent.atomic.AtomicInteger ok = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger tooMany = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(total);
        java.util.concurrent.ExecutorService pool =
            java.util.concurrent.Executors.newFixedThreadPool(20);
        try {
            for (int i = 0; i < total; i++) {
                pool.submit(() -> {
                    try {
                        int status = given()
                            .header("Authorization", "Bearer " + token)
                        .when()
                            .get("/api/communities")
                            .statusCode();
                        if (status == 200) ok.incrementAndGet();
                        else if (status == 429) tooMany.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            if (!latch.await(60, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new AssertionError("Rate-limit burst did not complete in 60s");
            }
        } finally {
            pool.shutdownNow();
        }

        // First 60 should succeed (per business rule), the extras should be 429.
        if (ok.get() < 1) throw new AssertionError("No requests succeeded; got " + ok.get());
        if (tooMany.get() < 1) {
            throw new AssertionError("Expected at least one 429 after burst of " + total
                + " requests in 60s; got 0 (OK=" + ok.get() + ")");
        }
    }

    @Test
    void logoutInvalidatesToken() {
        // Use ops_manager (no other live-smoke test consumes its quota — the
        // data_integrator user has been burned by the rate-limiter burst test).
        String token = loginAs("ops_manager", "Manager1234!");
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .post("/api/auth/logout")
        .then()
            .statusCode(200);

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/auth/me")
        .then()
            .statusCode(401);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private String loginAs(String username, String password) {
        Response resp = given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "password", password))
        .when()
            .post("/api/auth/login");
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Live login as " + username + " failed: HTTP "
                + resp.statusCode() + " " + resp.asString());
        }
        return resp.jsonPath().getString("token");
    }
}
