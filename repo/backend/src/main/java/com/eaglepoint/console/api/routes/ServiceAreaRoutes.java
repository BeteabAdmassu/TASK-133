package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.dto.PagedResponse;
import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.service.ServiceAreaService;
import io.javalin.Javalin;

import java.util.Map;

public class ServiceAreaRoutes {

    public static void register(Javalin app, ServiceAreaService serviceAreaService) {
        app.get("/api/service-areas", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            int page = Integer.parseInt(ctx.queryParamAsClass("page", String.class).getOrDefault("1"));
            int pageSize = Math.min(Integer.parseInt(ctx.queryParamAsClass("pageSize", String.class).getOrDefault("50")), 500);
            ctx.json(PagedResponse.of(serviceAreaService.listServiceAreas(page, pageSize)));
        });

        app.post("/api/service-areas", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            long communityId = Long.parseLong(body.get("communityId").toString());
            ctx.status(201).json(Map.of("serviceArea",
                serviceAreaService.createServiceArea(communityId, (String) body.get("name"), (String) body.get("description"))
            ));
        });

        app.get("/api/service-areas/{id}", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("serviceArea", serviceAreaService.getServiceArea(id)));
        });

        app.put("/api/service-areas/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            ctx.json(Map.of("serviceArea",
                serviceAreaService.updateServiceArea(id, body.get("name"), body.get("description"), body.get("status"))
            ));
        });

        app.delete("/api/service-areas/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            long id = Long.parseLong(ctx.pathParam("id"));
            serviceAreaService.deleteServiceArea(id);
            ctx.status(204);
        });
    }
}
