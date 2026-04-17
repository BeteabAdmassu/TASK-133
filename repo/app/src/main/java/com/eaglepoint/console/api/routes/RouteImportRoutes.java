package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.dto.PagedResponse;
import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.service.RouteImportService;
import io.javalin.Javalin;

import java.util.Map;

public class RouteImportRoutes {

    public static void register(Javalin app, RouteImportService routeImportService) {

        app.get("/api/route-imports", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            ctx.json(PagedResponse.of(routeImportService.listImports(page, pageSize)));
        });

        app.post("/api/route-imports", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            User user = AuthMiddleware.getCurrentUser(ctx);
            var uploadedFile = ctx.uploadedFile("file");
            if (uploadedFile == null) {
                ctx.status(400).json(Map.of("error", Map.of(
                    "code", "VALIDATION_ERROR",
                    "message", "No file uploaded"
                )));
                return;
            }
            byte[] fileContent = uploadedFile.content().readAllBytes();
            ctx.status(201).json(Map.of("import",
                routeImportService.startImport(uploadedFile.filename(), fileContent, user.getId())
            ));
        });

        app.get("/api/route-imports/{id}", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("import", routeImportService.getImport(id)));
        });

        app.get("/api/route-imports/{id}/checkpoints", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            ctx.json(PagedResponse.of(routeImportService.getCheckpoints(id, page, pageSize)));
        });
    }
}
