package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.QueryShaper;
import com.eaglepoint.console.api.dto.PagedResponse;
import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.service.KpiService;
import io.javalin.Javalin;

import java.util.Map;

public class KpiRoutes {

    public static void register(Javalin app, KpiService kpiService) {

        app.get("/api/kpis", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            var result = kpiService.listKpis(page, pageSize);
            if (ctx.queryParam("sort") != null || ctx.queryParam("fields") != null) {
                ctx.json(QueryShaper.shape(ctx, result));
            } else {
                ctx.json(PagedResponse.of(result));
            }
        });

        app.post("/api/kpis", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            ctx.status(201).json(Map.of("kpi", kpiService.createKpi(
                (String) body.get("name"),
                (String) body.get("unit"),
                (String) body.get("category"),
                (String) body.get("formula"),
                (String) body.get("description")
            )));
        });

        app.get("/api/kpis/{id}", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("kpi", kpiService.getKpi(id)));
        });

        app.put("/api/kpis/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Boolean isActive = body.get("isActive") != null
                ? Boolean.parseBoolean(body.get("isActive").toString()) : null;
            ctx.json(Map.of("kpi", kpiService.updateKpi(
                id,
                (String) body.get("name"),
                (String) body.get("unit"),
                (String) body.get("category"),
                (String) body.get("formula"),
                isActive
            )));
        });

        app.get("/api/kpi-scores", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            Long kpiId = ctx.queryParam("kpiId") != null ? Long.parseLong(ctx.queryParam("kpiId")) : null;
            Long serviceAreaId = ctx.queryParam("serviceAreaId") != null ? Long.parseLong(ctx.queryParam("serviceAreaId")) : null;
            String from = ctx.queryParam("from");
            String to = ctx.queryParam("to");
            ctx.json(PagedResponse.of(kpiService.listScores(kpiId, serviceAreaId, from, to, page, pageSize)));
        });

        app.post("/api/kpi-scores", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            User user = AuthMiddleware.getCurrentUser(ctx);
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            long kpiId = Long.parseLong(body.get("kpiId").toString());
            double value = Double.parseDouble(body.get("value").toString());
            Long serviceAreaId = body.get("serviceAreaId") != null ? Long.parseLong(body.get("serviceAreaId").toString()) : null;
            Long cycleId = body.get("cycleId") != null ? Long.parseLong(body.get("cycleId").toString()) : null;
            ctx.status(201).json(Map.of("kpiScore", kpiService.recordScore(
                kpiId, value, (String) body.get("scoreDate"),
                serviceAreaId, cycleId, (String) body.get("notes"),
                user.getId()
            )));
        });
    }
}
