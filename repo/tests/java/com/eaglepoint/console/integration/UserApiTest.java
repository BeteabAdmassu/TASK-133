package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * User-management integration tests. All /api/users/* routes require SYSTEM_ADMIN.
 */
class UserApiTest extends BaseIntegrationTest {

    @Test
    void listUsersRequiresAuth() {
        anonymous().when().get("/api/users").then().statusCode(401);
    }

    @Test
    void listUsersRequiresAdminRole() {
        asRole("OPS_MANAGER").when().get("/api/users").then().statusCode(403);
        asRole("AUDITOR").when().get("/api/users").then().statusCode(403);
        asRole("REVIEWER").when().get("/api/users").then().statusCode(403);
    }

    @Test
    void listUsersAsAdminReturnsPagedSeedUsers() {
        withAdmin()
        .when()
            .get("/api/users")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("data.size()", greaterThanOrEqualTo(5)) // all 5 seed roles
            .body("page", equalTo(1))
            .body("totalPages", notNullValue())
            .body("data.find { it.username == 'admin' }.role", equalTo("SYSTEM_ADMIN"))
            .body("data.find { it.username == 'reviewer' }.role", equalTo("REVIEWER"));
    }

    @Test
    void createUserSucceedsAndReturnsCreatedPayload() {
        String uname = unique("t_usr").replace("-", "_");
        withAdmin()
            .body(Map.of(
                "username", uname,
                "password", "S3curePass!",
                "displayName", "Test User",
                "role", "AUDITOR",
                "staffId", "STAFF-001"
            ))
        .when()
            .post("/api/users")
        .then()
            .statusCode(201)
            .body("user.id", notNullValue())
            .body("user.username", equalTo(uname))
            .body("user.role", equalTo("AUDITOR"))
            .body("user.displayName", equalTo("Test User"))
            .body("user.active", equalTo(true));
    }

    @Test
    void createUserWithDuplicateUsernameReturns409() {
        String uname = unique("dup_usr").replace("-", "_");
        withAdmin()
            .body(Map.of(
                "username", uname, "password", "S3curePass!",
                "displayName", "First", "role", "AUDITOR", "staffId", "S-1"
            ))
        .when().post("/api/users").then().statusCode(201);

        withAdmin()
            .body(Map.of(
                "username", uname, "password", "S3curePass!",
                "displayName", "Second", "role", "REVIEWER", "staffId", "S-2"
            ))
        .when()
            .post("/api/users")
        .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"));
    }

    @Test
    void createUserWithInvalidRoleReturnsValidationError() {
        withAdmin()
            .body(Map.of(
                "username", unique("bad").replace("-", "_"),
                "password", "S3curePass!",
                "displayName", "Test",
                "role", "SUPER_HACKER",
                "staffId", "S-X"
            ))
        .when()
            .post("/api/users")
        .then()
            .statusCode(400)
            .body("error.code", anyOf(equalTo("VALIDATION_ERROR"), equalTo("INVALID_INPUT")));
    }

    @Test
    void createUserWithShortPasswordReturnsValidationError() {
        withAdmin()
            .body(Map.of(
                "username", unique("sp").replace("-", "_"),
                "password", "short",
                "displayName", "Test",
                "role", "AUDITOR",
                "staffId", "S-X"
            ))
        .when()
            .post("/api/users")
        .then()
            .statusCode(400);
    }

    @Test
    void getUserByIdReturnsUser() {
        int id = withAdmin()
            .body(Map.of(
                "username", unique("get_usr").replace("-", "_"),
                "password", "S3curePass!",
                "displayName", "GetMe",
                "role", "REVIEWER",
                "staffId", "S-G"
            ))
        .when().post("/api/users")
        .then().statusCode(201)
            .extract().path("user.id");

        withAdmin()
        .when()
            .get("/api/users/" + id)
        .then()
            .statusCode(200)
            .body("user.id", equalTo(id))
            .body("user.displayName", equalTo("GetMe"))
            .body("user.role", equalTo("REVIEWER"));
    }

    @Test
    void getUserByIdReturns404WhenMissing() {
        withAdmin()
        .when()
            .get("/api/users/99999999")
        .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void updateUserChangesRoleAndDisplayName() {
        int id = withAdmin()
            .body(Map.of(
                "username", unique("upd_usr").replace("-", "_"),
                "password", "S3curePass!",
                "displayName", "Before",
                "role", "REVIEWER",
                "staffId", "S-U"
            ))
        .when().post("/api/users").then().statusCode(201)
            .extract().path("user.id");

        withAdmin()
            .body(Map.of(
                "displayName", "After",
                "role", "OPS_MANAGER",
                "isActive", true
            ))
        .when()
            .put("/api/users/" + id)
        .then()
            .statusCode(200)
            .body("user.id", equalTo(id))
            .body("user.displayName", equalTo("After"))
            .body("user.role", equalTo("OPS_MANAGER"));
    }

    @Test
    void userResponsesNeverIncludePasswordHashOrStaffId() {
        // GET /api/users/{id} — single-user fetch
        withAdmin()
        .when()
            .get("/api/users/1")
        .then()
            .statusCode(200)
            .body("user.passwordHash", nullValue())
            .body("user.password_hash", nullValue())
            .body("user.staffIdEncrypted", nullValue())
            .body("user.staff_id_encrypted", nullValue())
            // Safe fields still present
            .body("user.username", notNullValue())
            .body("user.role", notNullValue());

        // GET /api/users — list
        withAdmin()
        .when()
            .get("/api/users")
        .then()
            .statusCode(200)
            .body("data.every { it.passwordHash == null && it.staffIdEncrypted == null }",
                  equalTo(true));

        // POST /api/auth/login response object — user payload must be clean too.
        int rc = anonymous()
            .body(Map.of("username", "admin", "password", "Admin1234!"))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("user.passwordHash", nullValue())
            .body("user.staffIdEncrypted", nullValue())
            .extract().statusCode();
        if (rc != 200) throw new AssertionError("Login smoke returned " + rc);
        // The fresh login invalidates the cached admin token — refresh the cache.
        invalidateTokenFor("SYSTEM_ADMIN");
    }

    @Test
    void deleteUserDeactivates() {
        int id = withAdmin()
            .body(Map.of(
                "username", unique("del_usr").replace("-", "_"),
                "password", "S3curePass!",
                "displayName", "ToDelete",
                "role", "AUDITOR",
                "staffId", "S-D"
            ))
        .when().post("/api/users").then().statusCode(201)
            .extract().path("user.id");

        withAdmin()
        .when()
            .delete("/api/users/" + id)
        .then()
            .statusCode(204);

        // User row should still exist but be inactive
        withAdmin()
        .when()
            .get("/api/users/" + id)
        .then()
            .statusCode(200)
            .body("user.active", equalTo(false));
    }
}
