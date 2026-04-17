package com.eaglepoint.console.integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Deactivating a user must invalidate any outstanding bearer tokens.
 * A subsequent request with the same token must get 401.
 */
class DeactivatedUserAuthApiTest extends BaseIntegrationTest {

    @Test
    void deactivatingUserRevokesTheirToken() {
        // 1) Admin creates a fresh user.
        String username = "deacttest_" + System.nanoTime();
        int userId = withAdmin()
            .body(Map.of(
                "username", username,
                "password", "Temp1234!",
                "displayName", "Deactivation Test",
                "role", "OPS_MANAGER",
                "staffId", "STAFF-DT-1"
            ))
        .when()
            .post("/api/users")
        .then()
            .statusCode(201)
            .extract().path("user.id");

        // 2) The new user logs in and gets a token.
        String token = given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "password", "Temp1234!"))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .extract().path("token");

        // 3) That token works for /api/users/{id} — users can read themselves? Actually SYSTEM_ADMIN only.
        //    Use a broadly readable endpoint to confirm the token is valid first.
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/communities")
        .then()
            .statusCode(200);

        // 4) Admin deactivates the user.
        withAdmin().when().delete("/api/users/" + userId).then().statusCode(204);

        // 5) The old token must now be rejected.
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/communities")
        .then()
            .statusCode(401)
            .body("error.code", equalTo("UNAUTHORIZED"));
    }
}
