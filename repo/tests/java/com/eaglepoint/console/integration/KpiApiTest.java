package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

class KpiApiTest extends BaseIntegrationTest {

    @Test
    void listKpisRequiresAuth() {
        anonymous().when().get("/api/kpis").then().statusCode(401);
    }

    @Test
    void listKpisReturnsPagedData() {
        withAdmin()
        .when()
            .get("/api/kpis")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("page", notNullValue())
            .body("totalPages", notNullValue());
    }

    @Test
    void createKpiRequiresElevatedRole() {
        asRole("AUDITOR")
            .body(Map.of(
                "name", unique("KPI"),
                "unit", "%",
                "category", "QUALITY",
                "formula", "pass/total*100"
            ))
        .when()
            .post("/api/kpis")
        .then()
            .statusCode(403);
    }

    @Test
    void createKpiSucceedsForAdmin() {
        String name = unique("KPI");
        withAdmin()
            .body(Map.of(
                "name", name,
                "unit", "%",
                "category", "QUALITY",
                "formula", "pass/total*100",
                "description", "Pass rate"
            ))
        .when()
            .post("/api/kpis")
        .then()
            .statusCode(201)
            .body("kpi.id", notNullValue())
            .body("kpi.name", equalTo(name))
            .body("kpi.category", equalTo("QUALITY"))
            .body("kpi.active", equalTo(true));
    }

    @Test
    void createKpiWithDuplicateNameReturns409() {
        String name = unique("Dup");
        withAdmin().body(Map.of(
            "name", name, "unit", "%", "category", "Q", "formula", "x"
        )).when().post("/api/kpis").then().statusCode(201);

        withAdmin().body(Map.of(
            "name", name, "unit", "%", "category", "Q", "formula", "y"
        )).when().post("/api/kpis").then().statusCode(409)
            .body("error.code", equalTo("CONFLICT"));
    }

    @Test
    void getKpiByIdReturnsKpi() {
        int id = withAdmin().body(Map.of(
            "name", unique("KPI"), "unit", "%", "category", "Q", "formula", "x"
        )).when().post("/api/kpis").then().statusCode(201)
            .extract().path("kpi.id");

        withAdmin().when().get("/api/kpis/" + id).then()
            .statusCode(200)
            .body("kpi.id", equalTo(id));
    }

    @Test
    void updateKpiChangesCategoryAndActive() {
        int id = withAdmin().body(Map.of(
            "name", unique("KPI"), "unit", "%", "category", "Q", "formula", "x"
        )).when().post("/api/kpis").then().statusCode(201)
            .extract().path("kpi.id");

        withAdmin().body(Map.of(
            "category", "EFFICIENCY",
            "isActive", false
        )).when().put("/api/kpis/" + id).then()
            .statusCode(200)
            .body("kpi.category", equalTo("EFFICIENCY"))
            .body("kpi.active", equalTo(false));
    }

    @Test
    void recordKpiScoreSucceeds() {
        int kpiId = withAdmin().body(Map.of(
            "name", unique("KPI"), "unit", "%", "category", "Q", "formula", "x"
        )).when().post("/api/kpis").then().statusCode(201)
            .extract().path("kpi.id");

        withAdmin()
            .body(Map.of(
                "kpiId", kpiId,
                "value", 87.5,
                "scoreDate", "2024-06-15",
                "notes", "quarterly review"
            ))
        .when()
            .post("/api/kpi-scores")
        .then()
            .statusCode(201)
            .body("kpiScore.id", notNullValue())
            .body("kpiScore.value", equalTo(87.5f))
            .body("kpiScore.kpiId", equalTo(kpiId));
    }

    @Test
    void listKpiScoresReturnsPagedData() {
        withAdmin().when().get("/api/kpi-scores").then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("totalPages", notNullValue());
    }
}
