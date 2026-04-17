package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.QueryShaper;
import com.eaglepoint.console.api.dto.PagedResponse;
import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.service.PickupPointService;
import io.javalin.Javalin;

import java.util.Map;
import java.util.UUID;

public class PickupPointRoutes {

    public static void register(Javalin app, PickupPointService ppService) {
        app.get("/api/pickup-points", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            var result = ppService.listPickupPoints(page, pageSize);
            if (ctx.queryParam("sort") != null || ctx.queryParam("fields") != null) {
                ctx.json(QueryShaper.shape(ctx, result));
            } else {
                ctx.json(PagedResponse.of(result));
            }
        });

        app.post("/api/pickup-points", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            long communityId = Long.parseLong(body.get("communityId").toString());
            Long geozoneId = body.get("geozoneId") != null ? Long.parseLong(body.get("geozoneId").toString()) : null;
            int capacity = Integer.parseInt(body.get("capacity").toString());
            ctx.status(201).json(Map.of("pickupPoint", ppService.createPickupPoint(
                communityId, (String) body.get("address"), (String) body.get("zipCode"),
                (String) body.get("streetRangeStart"), (String) body.get("streetRangeEnd"),
                (String) body.get("hoursJson"), capacity, geozoneId
            )));
        });

        app.get("/api/pickup-points/{id}", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("pickupPoint", ppService.getPickupPoint(id)));
        });

        app.put("/api/pickup-points/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Integer capacity = body.get("capacity") != null ? Integer.parseInt(body.get("capacity").toString()) : null;
            Long geozoneId = body.get("geozoneId") != null ? Long.parseLong(body.get("geozoneId").toString()) : null;
            ctx.json(Map.of("pickupPoint", ppService.updatePickupPoint(
                id, (String) body.get("address"), (String) body.get("zipCode"),
                capacity, (String) body.get("hoursJson"), geozoneId
            )));
        });

        app.delete("/api/pickup-points/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            long id = Long.parseLong(ctx.pathParam("id"));
            ppService.deletePickupPoint(id);
            ctx.status(204);
        });

        app.post("/api/pickup-points/{id}/pause", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            User user = AuthMiddleware.getCurrentUser(ctx);
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String traceId = UUID.randomUUID().toString();
            ctx.json(Map.of("pickupPoint",
                ppService.pausePickupPoint(id, body.get("reason"), body.get("pausedUntil"), user.getId(), traceId)
            ));
        });

        app.post("/api/pickup-points/{id}/resume", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            User user = AuthMiddleware.getCurrentUser(ctx);
            String traceId = UUID.randomUUID().toString();
            ctx.json(Map.of("pickupPoint", ppService.resumePickupPoint(id, user.getId(), traceId)));
        });

        app.post("/api/pickup-points/match", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            long communityId = Long.parseLong(body.get("communityId").toString());
            ctx.json(Map.of("pickupPoint",
                ppService.matchPickupPoint((String) body.get("zipCode"), (String) body.get("streetAddress"), communityId)
            ));
        });
    }
}
