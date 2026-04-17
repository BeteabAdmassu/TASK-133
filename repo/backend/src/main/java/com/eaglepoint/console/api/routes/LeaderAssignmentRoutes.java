package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.dto.PagedResponse;
import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.service.LeaderAssignmentService;
import io.javalin.Javalin;

import java.util.Map;

public class LeaderAssignmentRoutes {

    public static void register(Javalin app, LeaderAssignmentService service) {
        app.get("/api/leader-assignments", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            int page = Integer.parseInt(ctx.queryParamAsClass("page", String.class).getOrDefault("1"));
            int pageSize = Math.min(Integer.parseInt(ctx.queryParamAsClass("pageSize", String.class).getOrDefault("50")), 500);
            ctx.json(PagedResponse.of(service.listAssignments(page, pageSize)));
        });

        app.post("/api/leader-assignments", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            User user = AuthMiddleware.getCurrentUser(ctx);
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            long serviceAreaId = Long.parseLong(body.get("serviceAreaId").toString());
            long userId = Long.parseLong(body.get("userId").toString());
            ctx.status(201).json(Map.of("assignment",
                service.assignLeader(serviceAreaId, userId, user.getId())
            ));
        });

        app.put("/api/leader-assignments/{id}/end", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("assignment", service.endAssignment(id, user.getId())));
        });
    }
}
