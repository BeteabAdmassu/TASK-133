package com.eaglepoint.console.integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Auth & session integration tests.
 *
 * <p>Fresh logins here target the seed accounts {@code auditor} and
 * {@code data_integrator} (not {@code admin}).  Each login revokes the
 * previously-issued token for that user, so {@link #invalidateSharedCaches()}
 * runs after every test to force sibling classes to re-login from scratch on
 * demand — keeping the JVM-wide token cache consistent.</p>
 */
class AuthApiTest extends BaseIntegrationTest {

    @AfterEach
    void invalidateSharedCaches() {
        // Any role we fresh-login or logout in this suite must be invalidated so
        // other test classes re-login cleanly next time they call tokenFor(role).
        invalidateTokenFor("AUDITOR");
        invalidateTokenFor("DATA_INTEGRATOR");
    }

    @Test
    void loginWithCorrectCredentialsReturnsToken() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "auditor", "password", "Auditor1234!"))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("token", notNullValue())
            .body("token", not(emptyString()))
            .body("user.username", equalTo("auditor"))
            .body("user.role", equalTo("AUDITOR"))
            .body("user.id", notNullValue())
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
    void loginWithUnknownUserReturns401() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "does_not_exist", "password", "whatever"))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void loginWithMissingFieldsReturnsValidationError() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "admin"))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(anyOf(is(400), is(401)))
            .body("error.code", notNullValue());
    }

    @Test
    void getMeWithValidTokenReturnsCurrentUser() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken())
        .when()
            .get("/api/auth/me")
        .then()
            .statusCode(200)
            .body("user.username", equalTo("admin"))
            .body("user.id", notNullValue())
            .body("user.role", equalTo("SYSTEM_ADMIN"));
    }

    @Test
    void getMeWithoutTokenReturns401() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/auth/me")
        .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void getMeWithMalformedTokenReturns401() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer not-a-real-token")
        .when()
            .get("/api/auth/me")
        .then()
            .statusCode(401);
    }

    @Test
    void logoutWithValidTokenSucceeds() {
        // Login as auditor (isolated from cached admin token)
        String token = given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "auditor", "password", "Auditor1234!"))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .extract()
            .path("token");

        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
        .when()
            .post("/api/auth/logout")
        .then()
            .statusCode(200);

        // Token should be invalid after logout
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/auth/me")
        .then()
            .statusCode(401);
    }

    @Test
    void healthEndpointIsAccessibleWithoutAuth() {
        given()
        .when()
            .get("/api/health")
        .then()
            .statusCode(200)
            .body("status", notNullValue())
            .body("db", notNullValue());
    }
}
