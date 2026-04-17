package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.dto.PagedResponse;
import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.service.CommunityService;
import io.javalin.Javalin;

import java.util.Map;

public class CommunityRoutes {

    public static void register(Javalin app, CommunityService communityService) {
        app.get("/api/communities", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            int page = Integer.parseInt(ctx.queryParamAsClass("page", String.class).getOrDefault("1"));
            int pageSize = Math.min(Integer.parseInt(ctx.queryParamAsClass("pageSize", String.class).getOrDefault("50")), 500);
            ctx.json(PagedResponse.of(communityService.listCommunities(page, pageSize)));
        });

        app.post("/api/communities", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            ctx.status(201).json(Map.of("community",
                communityService.createCommunity(body.get("name"), body.get("description"))
            ));
        });

        app.get("/api/communities/{id}", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("community", communityService.getCommunity(id)));
        });

        app.put("/api/communities/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            ctx.json(Map.of("community",
                communityService.updateCommunity(id, body.get("name"), body.get("description"), body.get("status"))
            ));
        });

        app.delete("/api/communities/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            long id = Long.parseLong(ctx.pathParam("id"));
            communityService.deleteCommunity(id);
            ctx.status(204);
        });
    }
}
