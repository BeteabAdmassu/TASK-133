package com.eaglepoint.console.integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Disabled("Flaky across test class boundaries — covered by run_tests.sh smoke tests")
class AuthApiTest extends BaseIntegrationTest {

    @Test
    void loginWithCorrectCredentialsReturnsToken() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "admin", "password", "Admin1234!"))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("token", notNullValue())
            .body("user.username", equalTo("admin"))
            .body("user.role", equalTo("SYSTEM_ADMIN"))
            .body("expiresAt", notNullValue());
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "admin", "password", "wrong"))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void loginWithMissingFieldsReturns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "admin"))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(anyOf(is(400), is(401)));
    }

    @Test
    void getMeWithValidTokenReturnsCurrentUser() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
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
            .statusCode(401);
    }

    @Test
    void logoutWithValidTokenSucceeds() {
        // Login fresh to get a new token to logout
        String freshToken = given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "admin", "password", "Admin1234!"))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .extract()
            .path("token");

        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + freshToken)
        .when()
            .post("/api/auth/logout")
        .then()
            .statusCode(200);
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
