package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.dto.PagedResponse;
import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.service.AppealService;
import com.eaglepoint.console.service.EvaluationService;
import com.eaglepoint.console.service.ReviewService;
import io.javalin.Javalin;

import java.util.List;
import java.util.Map;

public class EvaluationRoutes {

    public static void register(Javalin app, EvaluationService evalService,
                                ReviewService reviewService, AppealService appealService) {

        // --- Evaluation Cycles ---
        app.get("/api/cycles", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            ctx.json(PagedResponse.of(evalService.listCycles(page, pageSize)));
        });

        app.post("/api/cycles", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            User user = AuthMiddleware.getCurrentUser(ctx);
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            ctx.status(201).json(Map.of("cycle", evalService.createCycle(
                (String) body.get("name"),
                (String) body.get("startDate"),
                (String) body.get("endDate"),
                user.getId()
            )));
        });

        app.get("/api/cycles/{id}", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("cycle", evalService.getCycle(id)));
        });

        app.put("/api/cycles/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            ctx.json(Map.of("cycle", evalService.updateCycle(
                id, body.get("name"), body.get("startDate"), body.get("endDate")
            )));
        });

        app.delete("/api/cycles/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            evalService.deleteCycle(id);
            ctx.status(204);
        });

        // Lifecycle transitions are explicit endpoints — callers must use these
        // instead of PUT /api/cycles/{id} to move DRAFT -> ACTIVE -> CLOSED.
        app.post("/api/cycles/{id}/activate", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("cycle", evalService.activateCycle(id)));
        });

        app.post("/api/cycles/{id}/close", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("cycle", evalService.closeCycle(id)));
        });

        // --- Scorecard Templates ---
        app.get("/api/cycles/{cycleId}/templates", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long cycleId = Long.parseLong(ctx.pathParam("cycleId"));
            ctx.json(Map.of("templates", evalService.listTemplates(cycleId)));
        });

        app.post("/api/cycles/{cycleId}/templates", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long cycleId = Long.parseLong(ctx.pathParam("cycleId"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            ctx.status(201).json(Map.of("template", evalService.createTemplate(
                cycleId, (String) body.get("name"), (String) body.get("type")
            )));
        });

        app.post("/api/cycles/{cycleId}/templates/{templateId}/metrics", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long templateId = Long.parseLong(ctx.pathParam("templateId"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            double weight = Double.parseDouble(body.get("weight").toString());
            double maxScore = body.get("maxScore") != null
                ? Double.parseDouble(body.get("maxScore").toString()) : 100.0;
            ctx.status(201).json(Map.of("metric", evalService.addMetric(
                templateId, (String) body.get("name"), weight, maxScore, (String) body.get("description")
            )));
        });

        // --- Scorecards ---
        app.get("/api/scorecards", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            ctx.json(PagedResponse.of(evalService.listScorecards(page, pageSize)));
        });

        app.post("/api/scorecards", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER", "REVIEWER");
            User user = AuthMiddleware.getCurrentUser(ctx);
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            long templateId = Long.parseLong(body.get("templateId").toString());
            long evaluateeId = Long.parseLong(body.get("evaluateeId").toString());
            long cycleId = Long.parseLong(body.get("cycleId").toString());
            ctx.status(201).json(Map.of("scorecard", evalService.createScorecard(
                cycleId, templateId, evaluateeId, user.getId()
            )));
        });

        app.get("/api/scorecards/{id}", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("scorecard", evalService.getScorecard(id)));
        });

        app.put("/api/scorecards/{id}/responses", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER", "REVIEWER");
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawResponses = (List<Map<String, Object>>) body.get("responses");
            List<com.eaglepoint.console.service.EvaluationService.ResponseInput> responses = new java.util.ArrayList<>();
            if (rawResponses != null) {
                for (Map<String, Object> r : rawResponses) {
                    var input = new com.eaglepoint.console.service.EvaluationService.ResponseInput();
                    input.metricId = Long.parseLong(r.get("metricId").toString());
                    input.score = Double.parseDouble(r.get("score").toString());
                    input.comments = r.get("comments") != null ? r.get("comments").toString() : null;
                    responses.add(input);
                }
            }
            ctx.json(Map.of("scorecard", evalService.saveResponses(id, user.getId(), responses)));
        });

        app.post("/api/scorecards/{id}/submit", ctx -> {
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("scorecard", evalService.submitScorecard(id, user.getId())));
        });

        app.post("/api/scorecards/{id}/recuse", ctx -> {
            AuthMiddleware.requireRoles(ctx, "REVIEWER", "SYSTEM_ADMIN");
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            ctx.json(Map.of("scorecard", evalService.recuseScorecard(id, user.getId(), body.get("reason"))));
        });

        // --- Reviews ---
        app.get("/api/reviews", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            ctx.json(PagedResponse.of(reviewService.listReviews(page, pageSize)));
        });

        app.post("/api/reviews", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER", "REVIEWER");
            User user = AuthMiddleware.getCurrentUser(ctx);
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            long scorecardId = Long.parseLong(body.get("scorecardId").toString());
            ctx.status(201).json(Map.of("review", reviewService.createReview(scorecardId, user.getId())));
        });

        app.get("/api/reviews/{id}", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("review", reviewService.getReview(id)));
        });

        app.post("/api/reviews/{id}/approve", ctx -> {
            AuthMiddleware.requireRoles(ctx, "REVIEWER", "SYSTEM_ADMIN");
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            ctx.json(Map.of("review", reviewService.approveReview(id, user.getId(), body.get("notes"))));
        });

        app.post("/api/reviews/{id}/reject", ctx -> {
            AuthMiddleware.requireRoles(ctx, "REVIEWER", "SYSTEM_ADMIN");
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            ctx.json(Map.of("review", reviewService.rejectReview(id, user.getId(), body.get("notes"))));
        });

        app.post("/api/reviews/{id}/flag-conflict", ctx -> {
            AuthMiddleware.requireRoles(ctx, "REVIEWER", "SYSTEM_ADMIN");
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            ctx.json(Map.of("review", reviewService.flagConflict(id, user.getId(), body.get("reason"))));
        });

        app.post("/api/reviews/{id}/assign-second", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            long secondReviewerId = Long.parseLong(body.get("reviewerId").toString());
            ctx.json(Map.of("review", reviewService.assignSecondReviewer(id, user.getId(), secondReviewerId)));
        });

        // --- Appeals ---
        app.get("/api/appeals", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            ctx.json(PagedResponse.of(appealService.listAppeals(page, pageSize)));
        });

        app.post("/api/appeals", ctx -> {
            User user = AuthMiddleware.getCurrentUser(ctx);
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            long scorecardId = Long.parseLong(body.get("scorecardId").toString());
            ctx.status(201).json(Map.of("appeal",
                appealService.fileAppeal(scorecardId, user.getId(), (String) body.get("reason"))
            ));
        });

        app.get("/api/appeals/{id}", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("appeal", appealService.getAppeal(id)));
        });

        app.post("/api/appeals/{id}/resolve", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            ctx.json(Map.of("appeal", appealService.resolveAppeal(id, user.getId(), body.get("notes"))));
        });

        app.post("/api/appeals/{id}/reject", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            ctx.json(Map.of("appeal", appealService.rejectAppeal(id, user.getId(), body.get("notes"))));
        });
    }
}
