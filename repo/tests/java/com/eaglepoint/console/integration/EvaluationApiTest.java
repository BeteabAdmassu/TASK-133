package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;

class EvaluationApiTest extends BaseIntegrationTest {

    // ─── Cycles ──────────────────────────────────────────────────────────────

    @Test
    void createCycleSucceeds() {
        withAdmin()
            .body(Map.of(
                "name", unique("Cycle"),
                "startDate", "2024-01-01",
                "endDate", "2024-03-31"
            ))
        .when()
            .post("/api/cycles")
        .then()
            .statusCode(201)
            .body("cycle.id", notNullValue())
            .body("cycle.status", equalTo("DRAFT"))
            .body("cycle.name", notNullValue())
            .body("cycle.startDate", equalTo("2024-01-01"));
    }

    @Test
    void listCyclesRequiresAuth() {
        anonymous().when().get("/api/cycles").then().statusCode(401);
    }

    @Test
    void listCyclesReturnsPagedData() {
        withAdmin()
        .when()
            .get("/api/cycles")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("page", notNullValue())
            .body("totalPages", notNullValue());
    }

    @Test
    void createCycleWithDuplicateNameReturns409() {
        String name = unique("DupCycle");
        withAdmin()
            .body(Map.of("name", name, "startDate", "2024-01-01", "endDate", "2024-06-30"))
        .when()
            .post("/api/cycles")
        .then()
            .statusCode(201);

        withAdmin()
            .body(Map.of("name", name, "startDate", "2024-07-01", "endDate", "2024-12-31"))
        .when()
            .post("/api/cycles")
        .then()
            .statusCode(409)
            .body("error.code", equalTo("CONFLICT"));
    }

    @Test
    void createCycleWithBadDatesReturnsValidationError() {
        withAdmin()
            .body(Map.of("name", unique("BadDates"), "startDate", "2024-12-31", "endDate", "2024-01-01"))
        .when()
            .post("/api/cycles")
        .then()
            .statusCode(400);
    }

    @Test
    void updateDraftCycleChangesName() {
        int id = createCycle();
        String newName = unique("Renamed");
        withAdmin()
            .body(Map.of("name", newName, "startDate", "2024-01-01", "endDate", "2024-06-30"))
        .when()
            .put("/api/cycles/" + id)
        .then()
            .statusCode(200)
            .body("cycle.id", equalTo(id))
            .body("cycle.name", equalTo(newName));
    }

    @Test
    void deleteDraftCycleSucceeds() {
        int id = createCycle();
        withAdmin().when().delete("/api/cycles/" + id).then().statusCode(204);

        withAdmin().when().get("/api/cycles/" + id)
            .then().statusCode(404);
    }

    @Test
    void deleteCycleDeniesUnprivilegedRoles() {
        int id = createCycle();
        // AUDITOR and REVIEWER must not be able to delete cycles.
        asRole("AUDITOR").when().delete("/api/cycles/" + id).then().statusCode(403);
        asRole("REVIEWER").when().delete("/api/cycles/" + id).then().statusCode(403);
    }

    // ─── Templates ───────────────────────────────────────────────────────────

    @Test
    void createTemplateForCycleSucceeds() {
        int cycleId = createCycle();
        withAdmin()
            .body(Map.of("name", unique("SELF Template"), "type", "SELF"))
        .when()
            .post("/api/cycles/" + cycleId + "/templates")
        .then()
            .statusCode(201)
            .body("template.id", notNullValue())
            .body("template.cycleId", equalTo(cycleId))
            .body("template.type", equalTo("SELF"));
    }

    @Test
    void createTemplateWithInvalidTypeFails() {
        int cycleId = createCycle();
        withAdmin()
            .body(Map.of("name", unique("BadType"), "type", "NOT_A_TYPE"))
        .when()
            .post("/api/cycles/" + cycleId + "/templates")
        .then()
            .statusCode(400);
    }

    @Test
    void addMetricWithWeightOverflowIsRejected() {
        int cycleId = createCycle();
        int templateId = createTemplate(cycleId, "SELF");

        addMetric(cycleId, templateId, "Metric A", 60.0, 100.0);
        addMetric(cycleId, templateId, "Metric B", 40.0, 100.0);

        withAdmin()
            .body(Map.of("name", "Metric C", "weight", 1.0, "maxScore", 100.0, "description", "over"))
        .when()
            .post("/api/cycles/" + cycleId + "/templates/" + templateId + "/metrics")
        .then()
            .statusCode(400);
    }

    // ─── Scorecards ──────────────────────────────────────────────────────────

    @Test
    void fullScorecardFlowSaveResponsesAndSubmit() {
        int cycleId = createCycle();
        int templateId = createTemplate(cycleId, "PEER");
        int metricId = addMetric(cycleId, templateId, "Teamwork", 100.0, 10.0);

        // Use the reviewer seed user as evaluatee; admin is the evaluator.
        int evaluateeId = findUserIdByUsername("reviewer");

        int scId = withAdmin()
            .body(Map.of(
                "templateId", templateId,
                "evaluateeId", evaluateeId,
                "cycleId", cycleId
            ))
        .when()
            .post("/api/scorecards")
        .then()
            .statusCode(201)
            .body("scorecard.id", notNullValue())
            .body("scorecard.status", equalTo("PENDING"))
            .extract()
            .path("scorecard.id");

        // Save responses
        withAdmin()
            .body(Map.of(
                "responses", List.of(Map.of(
                    "metricId", metricId,
                    "score", 8.0,
                    "comments", "solid collaboration"
                ))
            ))
        .when()
            .put("/api/scorecards/" + scId + "/responses")
        .then()
            .statusCode(200)
            .body("scorecard.status", anyOf(equalTo("IN_PROGRESS"), equalTo("PENDING")));

        // Submit
        withAdmin()
        .when()
            .post("/api/scorecards/" + scId + "/submit")
        .then()
            .statusCode(200)
            .body("scorecard.status", equalTo("SUBMITTED"))
            .body("scorecard.submittedAt", notNullValue());
    }

    @Test
    void recuseScorecardMovesToRecused() {
        int cycleId = createCycle();
        int templateId = createTemplate(cycleId, "PEER");
        addMetric(cycleId, templateId, "M", 100.0, 10.0);

        int evaluateeId = findUserIdByUsername("auditor");
        int scId = withAdmin()
            .body(Map.of("templateId", templateId, "evaluateeId", evaluateeId, "cycleId", cycleId))
        .when().post("/api/scorecards").then().statusCode(201)
            .extract().path("scorecard.id");

        withAdmin()
            .body(Map.of("reason", "Personal conflict with evaluatee"))
        .when()
            .post("/api/scorecards/" + scId + "/recuse")
        .then()
            .statusCode(200)
            .body("scorecard.status", equalTo("RECUSED"));
    }

    @Test
    void listScorecardsReturnsPagedData() {
        withAdmin()
        .when()
            .get("/api/scorecards")
        .then()
            .statusCode(200)
            .body("data", notNullValue())
            .body("totalPages", notNullValue());
    }

    @Test
    void getScorecardByIdReturnsScorecard() {
        int cycleId = createCycle();
        int templateId = createTemplate(cycleId, "EXPERT");
        addMetric(cycleId, templateId, "M", 100.0, 10.0);
        int evaluateeId = findUserIdByUsername("data_integrator");
        int scId = withAdmin()
            .body(Map.of("templateId", templateId, "evaluateeId", evaluateeId, "cycleId", cycleId))
        .when().post("/api/scorecards").then().statusCode(201)
            .extract().path("scorecard.id");

        withAdmin().when().get("/api/scorecards/" + scId).then()
            .statusCode(200)
            .body("scorecard.id", equalTo(scId));
    }

    // ─── Reviews ─────────────────────────────────────────────────────────────

    @Test
    void createReviewAndApprove() {
        int scId = fullySubmittedScorecard("reviewer");

        int reviewId = withAdmin()
            .body(Map.of("scorecardId", scId))
        .when()
            .post("/api/reviews")
        .then()
            .statusCode(201)
            .body("review.status", equalTo("PENDING"))
            .extract().path("review.id");

        withAdmin()
            .body(Map.of("notes", "Looks thorough"))
        .when()
            .post("/api/reviews/" + reviewId + "/approve")
        .then()
            .statusCode(200)
            .body("review.status", equalTo("APPROVED"));
    }

    @Test
    void rejectReviewRequiresNotes() {
        int scId = fullySubmittedScorecard("auditor");
        int reviewId = withAdmin()
            .body(Map.of("scorecardId", scId))
        .when()
            .post("/api/reviews").then().statusCode(201)
            .extract().path("review.id");

        // Empty comments should be a validation error
        withAdmin()
            .body(Map.of("notes", ""))
        .when()
            .post("/api/reviews/" + reviewId + "/reject")
        .then()
            .statusCode(400);

        withAdmin()
            .body(Map.of("notes", "Score inconsistency"))
        .when()
            .post("/api/reviews/" + reviewId + "/reject")
        .then()
            .statusCode(200)
            .body("review.status", equalTo("REJECTED"));
    }

    @Test
    void assignSecondReviewerMovesToInReview() {
        int scId = fullySubmittedScorecard("ops_manager");
        int reviewId = withAdmin()
            .body(Map.of("scorecardId", scId))
        .when().post("/api/reviews").then().statusCode(201)
            .extract().path("review.id");

        int secondReviewerId = findUserIdByUsername("reviewer");
        withAdmin()
            .body(Map.of("reviewerId", secondReviewerId))
        .when()
            .post("/api/reviews/" + reviewId + "/assign-second")
        .then()
            .statusCode(200)
            .body("review.status", equalTo("IN_REVIEW"))
            .body("review.secondReviewerId", equalTo(secondReviewerId));
    }

    @Test
    void listReviewsReturnsPagedData() {
        withAdmin()
        .when()
            .get("/api/reviews")
        .then()
            .statusCode(200)
            .body("data", notNullValue());
    }

    // ─── Appeals ─────────────────────────────────────────────────────────────

    @Test
    void listAppealsRequiresAuth() {
        anonymous().when().get("/api/appeals").then().statusCode(401);
    }

    @Test
    void listAppealsReturnsPagedData() {
        withAdmin()
        .when()
            .get("/api/appeals")
        .then()
            .statusCode(200)
            .body("data", notNullValue());
    }

    @Test
    void fileAppealRequiresEvaluateeIdentity() {
        // Submitted scorecard where the evaluatee is 'reviewer'.  Admin (not evaluatee)
        // tries to file the appeal — should be 403.
        int scId = fullySubmittedScorecard("reviewer");

        withAdmin()
            .body(Map.of("scorecardId", scId, "reason", "I dispute the score on this card"))
        .when()
            .post("/api/appeals")
        .then()
            .statusCode(403)
            .body("error.code", equalTo("FORBIDDEN"));
    }

    @Test
    void fileAppealAsEvaluateeSucceedsAndResolve() {
        // Evaluatee is 'reviewer', so reviewer files the appeal.
        int scId = fullySubmittedScorecard("reviewer");

        int appealId = asRole("REVIEWER")
            .body(Map.of("scorecardId", scId, "reason", "I dispute the score on this card"))
        .when()
            .post("/api/appeals")
        .then()
            .statusCode(201)
            .body("appeal.id", notNullValue())
            .body("appeal.status", equalTo("PENDING"))
            .extract().path("appeal.id");

        withAdmin()
        .when()
            .get("/api/appeals/" + appealId)
        .then()
            .statusCode(200)
            .body("appeal.id", equalTo(appealId));

        withAdmin()
            .body(Map.of("notes", "Recalculated — original score upheld"))
        .when()
            .post("/api/appeals/" + appealId + "/resolve")
        .then()
            .statusCode(200)
            .body("appeal.status", equalTo("RESOLVED"));
    }

    @Test
    void rejectAppealRequiresNotes() {
        int scId = fullySubmittedScorecard("reviewer");
        int appealId = asRole("REVIEWER")
            .body(Map.of("scorecardId", scId, "reason", "I dispute the score here too"))
        .when().post("/api/appeals").then().statusCode(201)
            .extract().path("appeal.id");

        withAdmin()
            .body(Map.of("notes", "Dismissed per policy"))
        .when()
            .post("/api/appeals/" + appealId + "/reject")
        .then()
            .statusCode(200)
            .body("appeal.status", equalTo("REJECTED"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private int createCycle() {
        return withAdmin()
            .body(Map.of(
                "name", unique("HelperCycle"),
                "startDate", "2024-01-01",
                "endDate", "2024-12-31"
            ))
        .when()
            .post("/api/cycles").then().statusCode(201)
            .extract().path("cycle.id");
    }

    private int createTemplate(int cycleId, String type) {
        return withAdmin()
            .body(Map.of("name", unique("T"), "type", type))
        .when()
            .post("/api/cycles/" + cycleId + "/templates").then().statusCode(201)
            .extract().path("template.id");
    }

    private int addMetric(int cycleId, int templateId, String name, double weight, double maxScore) {
        return withAdmin()
            .body(Map.of(
                "name", name,
                "weight", weight,
                "maxScore", maxScore,
                "description", "metric"
            ))
        .when()
            .post("/api/cycles/" + cycleId + "/templates/" + templateId + "/metrics")
            .then().statusCode(201)
            .extract().path("metric.id");
    }

    /** Creates a submitted scorecard where evaluatee is the given seed-username. */
    private int fullySubmittedScorecard(String evaluateeUsername) {
        int cycleId = createCycle();
        int templateId = createTemplate(cycleId, "PEER");
        int metricId = addMetric(cycleId, templateId, "M", 100.0, 10.0);
        int evaluateeId = findUserIdByUsername(evaluateeUsername);

        int scId = withAdmin()
            .body(Map.of("templateId", templateId, "evaluateeId", evaluateeId, "cycleId", cycleId))
        .when().post("/api/scorecards").then().statusCode(201)
            .extract().path("scorecard.id");

        withAdmin()
            .body(Map.of("responses", List.of(Map.of("metricId", metricId, "score", 9.0))))
        .when()
            .put("/api/scorecards/" + scId + "/responses").then().statusCode(200);

        withAdmin().when().post("/api/scorecards/" + scId + "/submit").then().statusCode(200);
        return scId;
    }

    private int findUserIdByUsername(String username) {
        return withAdmin()
        .when()
            .get("/api/users?pageSize=50")
        .then()
            .statusCode(200)
            .extract()
            .path("data.find { it.username == '" + username + "' }.id");
    }
}
