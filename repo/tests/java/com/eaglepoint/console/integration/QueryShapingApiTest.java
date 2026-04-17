package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * Covers the optional {@code sort} and {@code fields} query params on list
 * endpoints.  When absent the response shape is unchanged; when present the
 * response is reshaped in-memory on the current paginated page.
 */
class QueryShapingApiTest extends BaseIntegrationTest {

    @Test
    void omittingSortAndFieldsPreservesDefaultShape() {
        withAdmin().when()
            .get("/api/communities")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("totalPages", notNullValue());
    }

    @Test
    void sortByIdAscendingOrdersRows() {
        // Seed two communities and verify ascending id order.
        int idA = createCommunity(unique("SortA"));
        int idB = createCommunity(unique("SortB"));

        Map<String, Object> resp = withAdmin().when()
            .get("/api/communities?sort=id&pageSize=500")
        .then().statusCode(200)
            .extract().as(Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.get("data");
        int lastId = Integer.MIN_VALUE;
        boolean sawA = false;
        boolean sawB = false;
        for (Map<String, Object> row : rows) {
            int id = ((Number) row.get("id")).intValue();
            if (id < lastId) throw new AssertionError("Rows not in ascending order: " + id + " < " + lastId);
            lastId = id;
            if (id == idA) sawA = true;
            if (id == idB) sawB = true;
        }
        if (!sawA || !sawB) throw new AssertionError("Expected both seeded communities in response");
    }

    @Test
    void sortDescendingIsRespected() {
        createCommunity(unique("DescA"));
        createCommunity(unique("DescB"));

        Map<String, Object> resp = withAdmin().when()
            .get("/api/communities?sort=-id&pageSize=500")
        .then().statusCode(200).extract().as(Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.get("data");
        int lastId = Integer.MAX_VALUE;
        for (Map<String, Object> row : rows) {
            int id = ((Number) row.get("id")).intValue();
            if (id > lastId) throw new AssertionError("Rows not descending: " + id + " > " + lastId);
            lastId = id;
        }
    }

    @Test
    void fieldsRestrictsRowKeys() {
        createCommunity(unique("Shape"));
        Map<String, Object> resp = withAdmin().when()
            .get("/api/communities?fields=name&pageSize=500")
        .then().statusCode(200).extract().as(Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.get("data");
        if (rows.isEmpty()) throw new AssertionError("No rows returned");
        Map<String, Object> row = rows.get(0);
        // id is always kept so clients can follow-up.
        if (!row.containsKey("id") || !row.containsKey("name")) {
            throw new AssertionError("Expected id + name in row, got " + row.keySet());
        }
        if (row.containsKey("description")) {
            throw new AssertionError("description should have been filtered out, got " + row.keySet());
        }
    }

    @Test
    void askingForPasswordHashViaFieldsDoesNotLeakIt() {
        // Even if a caller explicitly names passwordHash in ?fields=, the
        // field must not appear in the response because the User model
        // @JsonIgnores it at serialisation time.
        Map<String, Object> resp = withAdmin().when()
            .get("/api/users?fields=passwordHash,staffIdEncrypted,username&pageSize=50")
        .then().statusCode(200).extract().as(Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.get("data");
        for (Map<String, Object> row : rows) {
            if (row.containsKey("passwordHash")) {
                throw new AssertionError("passwordHash leaked even via explicit fields request");
            }
            if (row.containsKey("staffIdEncrypted")) {
                throw new AssertionError("staffIdEncrypted leaked even via explicit fields request");
            }
        }
    }

    @Test
    void usersListHonoursFieldsFilter() {
        Map<String, Object> resp = withAdmin().when()
            .get("/api/users?fields=username,role&pageSize=50")
        .then().statusCode(200).extract().as(Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.get("data");
        for (Map<String, Object> row : rows) {
            if (row.containsKey("passwordHash")) {
                throw new AssertionError("Password hash leaked in fields-filtered response");
            }
            if (row.containsKey("lastLogin")) {
                throw new AssertionError("lastLogin should have been filtered out");
            }
        }
    }

    private int createCommunity(String name) {
        return withAdmin()
            .body(Map.of("name", name))
        .when()
            .post("/api/communities")
        .then()
            .statusCode(201)
            .extract()
            .path("community.id");
    }

    // ─── Sort/fields coverage for ServiceAreaRoutes, KpiRoutes, GeozoneRoutes ─

    @Test
    void serviceAreasHonourSortDescending() {
        int communityId = createCommunity(unique("QSCommunity"));
        for (int i = 0; i < 3; i++) {
            withAdmin()
                .body(Map.of(
                    "communityId", communityId,
                    "name", unique("SA"),
                    "description", "x"))
            .when().post("/api/service-areas").then().statusCode(201);
        }

        Map<String, Object> resp = withAdmin().when()
            .get("/api/service-areas?sort=-id&pageSize=500")
        .then().statusCode(200).extract().as(Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.get("data");
        int lastId = Integer.MAX_VALUE;
        for (Map<String, Object> row : rows) {
            int id = ((Number) row.get("id")).intValue();
            if (id > lastId) throw new AssertionError("service-areas not desc: " + id + " > " + lastId);
            lastId = id;
        }
    }

    @Test
    void serviceAreasHonourFieldsFilter() {
        int communityId = createCommunity(unique("QSCommunity"));
        withAdmin()
            .body(Map.of("communityId", communityId, "name", unique("SA"), "description", "y"))
        .when().post("/api/service-areas").then().statusCode(201);

        Map<String, Object> resp = withAdmin().when()
            .get("/api/service-areas?fields=name&pageSize=50")
        .then().statusCode(200).extract().as(Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.get("data");
        if (rows.isEmpty()) throw new AssertionError("expected seeded service area");
        Map<String, Object> row = rows.get(0);
        if (!row.containsKey("id") || !row.containsKey("name")) {
            throw new AssertionError("expected id+name in row: " + row.keySet());
        }
        if (row.containsKey("description")) {
            throw new AssertionError("description should be filtered out: " + row.keySet());
        }
    }

    @Test
    void kpisHonourSortAscending() {
        for (int i = 0; i < 3; i++) {
            withAdmin()
                .body(Map.of(
                    "name", unique("KPI"),
                    "unit", "%",
                    "category", "QUALITY",
                    "formula", "x"))
            .when().post("/api/kpis").then().statusCode(201);
        }

        Map<String, Object> resp = withAdmin().when()
            .get("/api/kpis?sort=id&pageSize=500")
        .then().statusCode(200).extract().as(Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.get("data");
        int lastId = Integer.MIN_VALUE;
        for (Map<String, Object> row : rows) {
            int id = ((Number) row.get("id")).intValue();
            if (id < lastId) throw new AssertionError("kpis not ascending: " + id + " < " + lastId);
            lastId = id;
        }
    }

    @Test
    void kpisHonourFieldsFilter() {
        withAdmin()
            .body(Map.of("name", unique("KPI"), "unit", "%", "category", "Q", "formula", "x"))
        .when().post("/api/kpis").then().statusCode(201);

        Map<String, Object> resp = withAdmin().when()
            .get("/api/kpis?fields=name,category&pageSize=50")
        .then().statusCode(200).extract().as(Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.get("data");
        if (rows.isEmpty()) throw new AssertionError("expected at least one KPI");
        for (Map<String, Object> row : rows) {
            if (!row.containsKey("id") || !row.containsKey("name") || !row.containsKey("category")) {
                throw new AssertionError("missing required keys in " + row.keySet());
            }
            if (row.containsKey("formula") || row.containsKey("unit")) {
                throw new AssertionError("expected formula/unit to be filtered out: " + row.keySet());
            }
        }
    }

    @Test
    void geozonesHonourSortAndFields() {
        withAdmin()
            .body(Map.of(
                "name", unique("GZ"),
                "zipCodes", java.util.List.of("12345"),
                "streetRangesJson", "{}"))
        .when().post("/api/geozones").then().statusCode(201);

        Map<String, Object> resp = withAdmin().when()
            .get("/api/geozones?sort=-id&fields=name&pageSize=500")
        .then().statusCode(200).extract().as(Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.get("data");
        int lastId = Integer.MAX_VALUE;
        for (Map<String, Object> row : rows) {
            int id = ((Number) row.get("id")).intValue();
            if (id > lastId) throw new AssertionError("geozones not desc: " + id + " > " + lastId);
            lastId = id;
            if (!row.containsKey("name")) throw new AssertionError("expected name in row: " + row.keySet());
            if (row.containsKey("zipCodes")) {
                throw new AssertionError("zipCodes should be filtered out: " + row.keySet());
            }
        }
    }
}
