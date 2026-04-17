package com.eaglepoint.console.integration;

import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end coverage for the {@code >0.5 miles} deviation-alert logic on
 * route imports.  Uploads staged CSV coordinates and asserts the resulting
 * checkpoint rows have the correct {@code deviationMiles} / {@code deviationAlert}
 * values.
 */
class RouteDeviationApiTest extends BaseIntegrationTest {

    /** Approximately 1.1 mile hop — well above the 0.5 mile alert threshold. */
    private static final String DEVIATING_CSV =
        "checkpoint_name,expected_at,actual_at,lat,lon\n" +
        "CP1,2024-01-01T10:00:00Z,2024-01-01T10:00:00Z,37.7749,-122.4194\n" +
        "CP2,2024-01-01T10:05:00Z,2024-01-01T10:05:00Z,37.7949,-122.4394\n";

    /** About 90 ft — well under the 0.5 mile alert threshold. */
    private static final String ON_TRACK_CSV =
        "checkpoint_name,expected_at,actual_at,lat,lon\n" +
        "CP1,2024-01-01T10:00:00Z,2024-01-01T10:00:00Z,37.7749,-122.4194\n" +
        "CP2,2024-01-01T10:05:00Z,2024-01-01T10:05:00Z,37.7750,-122.4193\n";

    @Test
    void consecutiveCheckpointsFurtherThanHalfMileAreFlagged() throws Exception {
        int importId = uploadCsv(DEVIATING_CSV);
        waitForImportCompleted(importId);

        Map<String, Object> checkpoints = withAdmin().when()
            .get("/api/route-imports/" + importId + "/checkpoints")
        .then().statusCode(200)
            .body("data", notNullValue())
            .extract().as(Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) checkpoints.get("data");
        // First checkpoint has no predecessor — no alert, deviation=0.
        Map<String, Object> cp1 = findByName(rows, "CP1");
        Map<String, Object> cp2 = findByName(rows, "CP2");
        if (cp1 == null || cp2 == null) throw new AssertionError("missing checkpoints: " + rows);

        assertFlag(cp1, false);
        assertFlag(cp2, true);
        double devMiles = ((Number) cp2.get("deviationMiles")).doubleValue();
        if (devMiles <= 0.5) {
            throw new AssertionError("Expected deviationMiles > 0.5 for CP2, got " + devMiles);
        }
        // Status should have been promoted to DEVIATED.
        if (!"DEVIATED".equals(cp2.get("status"))) {
            throw new AssertionError("Expected CP2 status DEVIATED, got " + cp2.get("status"));
        }
    }

    @Test
    void onTrackCheckpointsDoNotFireDeviationAlerts() throws Exception {
        int importId = uploadCsv(ON_TRACK_CSV);
        waitForImportCompleted(importId);

        Map<String, Object> checkpoints = withAdmin().when()
            .get("/api/route-imports/" + importId + "/checkpoints")
        .then().statusCode(200)
            .extract().as(Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) checkpoints.get("data");
        Map<String, Object> cp2 = findByName(rows, "CP2");
        if (cp2 == null) throw new AssertionError("missing CP2: " + rows);
        assertFlag(cp2, false);
        if (!"ON_TIME".equals(cp2.get("status"))) {
            throw new AssertionError("Expected CP2 status ON_TIME, got " + cp2.get("status"));
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private int uploadCsv(String csv) {
        return given()
            .header("Authorization", "Bearer " + adminToken())
            .contentType("multipart/form-data")
            .multiPart("file", "route.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv")
        .when()
            .post("/api/route-imports")
        .then()
            .statusCode(201)
            .extract().path("import.id");
    }

    /** Poll up to ~5s for the async worker to land the import in COMPLETED. */
    private void waitForImportCompleted(int importId) throws Exception {
        for (int i = 0; i < 50; i++) {
            String status = withAdmin().when().get("/api/route-imports/" + importId).then()
                .statusCode(200).extract().path("import.status");
            if ("COMPLETED".equals(status) || "INVALID".equals(status) || "FAILED".equals(status)) {
                if (!"COMPLETED".equals(status)) {
                    throw new AssertionError("Import " + importId + " ended in " + status);
                }
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Import " + importId + " did not complete within 5s");
    }

    private Map<String, Object> findByName(List<Map<String, Object>> rows, String name) {
        for (Map<String, Object> row : rows) {
            if (name.equals(row.get("checkpointName"))) return row;
        }
        return null;
    }

    private void assertFlag(Map<String, Object> row, boolean expected) {
        Object v = row.get("deviationAlert");
        boolean actual = v instanceof Boolean ? (Boolean) v : Boolean.parseBoolean(String.valueOf(v));
        if (actual != expected) {
            throw new AssertionError("deviationAlert expected=" + expected + " for " + row);
        }
        // Avoid unused-import warning on RestAssured static when running this class in isolation.
        RestAssured.baseURI = RestAssured.baseURI;
    }
}
