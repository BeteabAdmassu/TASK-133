package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;

class GeozoneApiTest extends BaseIntegrationTest {

    @Test
    void listGeozonesRequiresAuth() {
        anonymous().when().get("/api/geozones").then().statusCode(401);
    }

    @Test
    void listGeozonesReturnsPagedData() {
        withAdmin()
        .when()
            .get("/api/geozones")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("page", notNullValue())
            .body("totalPages", notNullValue());
    }

    @Test
    void createGeozoneRequiresElevatedRole() {
        asRole("AUDITOR")
            .body(Map.of(
                "name", unique("GZ"),
                "zipCodes", List.of("12345"),
                "streetRangesJson", "{}"
            ))
        .when()
            .post("/api/geozones")
        .then()
            .statusCode(403);
    }

    @Test
    void createGeozoneAsAdminSucceeds() {
        String name = unique("Zone");
        withAdmin()
            .body(Map.of(
                "name", name,
                "zipCodes", List.of("12345", "67890"),
                "streetRangesJson", "{\"main\":\"1-999\"}"
            ))
        .when()
            .post("/api/geozones")
        .then()
            .statusCode(201)
            .body("geozone.id", notNullValue())
            .body("geozone.name", equalTo(name));
    }

    @Test
    void getGeozoneByIdReturnsZone() {
        int id = withAdmin().body(Map.of(
            "name", unique("Zone"),
            "zipCodes", List.of("12345"),
            "streetRangesJson", "{}"
        )).when().post("/api/geozones").then().statusCode(201)
            .extract().path("geozone.id");

        withAdmin().when().get("/api/geozones/" + id).then()
            .statusCode(200)
            .body("geozone.id", equalTo(id));
    }

    @Test
    void updateGeozoneChangesName() {
        int id = withAdmin().body(Map.of(
            "name", unique("Before"),
            "zipCodes", List.of("11111"),
            "streetRangesJson", "{}"
        )).when().post("/api/geozones").then().statusCode(201)
            .extract().path("geozone.id");

        String newName = unique("After");
        withAdmin().body(Map.of(
            "name", newName
        )).when().put("/api/geozones/" + id).then()
            .statusCode(200)
            .body("geozone.name", equalTo(newName));
    }

    @Test
    void deleteGeozoneRequiresSystemAdmin() {
        int id = withAdmin().body(Map.of(
            "name", unique("DelZone"),
            "zipCodes", List.of("22222"),
            "streetRangesJson", "{}"
        )).when().post("/api/geozones").then().statusCode(201)
            .extract().path("geozone.id");

        asRole("OPS_MANAGER").when().delete("/api/geozones/" + id).then().statusCode(403);
        withAdmin().when().delete("/api/geozones/" + id).then().statusCode(204);
    }
}
