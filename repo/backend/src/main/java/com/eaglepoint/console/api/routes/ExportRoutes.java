package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.service.ExportService;
import io.javalin.Javalin;

import java.util.Map;

public class ExportRoutes {

    public static void register(Javalin app, ExportService exportService) {

        app.post("/api/exports", ctx -> {
            // All authenticated roles except AUDITOR can create exports
            User user = AuthMiddleware.getCurrentUser(ctx);
            if ("AUDITOR".equals(user.getRole())) {
                ctx.status(403).json(Map.of("error", Map.of(
                    "code", "FORBIDDEN",
                    "message", "AUDITOR role cannot initiate exports"
                )));
                return;
            }
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String type = (String) body.get("type");
            String entityType = (String) body.get("entityType");
            String destinationPath = (String) body.get("destinationPath");
            String filtersJson = body.get("filtersJson") != null ? body.get("filtersJson").toString() : null;
            ctx.status(201).json(Map.of("export",
                exportService.createExportJob(type, entityType, destinationPath, filtersJson, user.getId())
            ));
        });

        app.get("/api/exports/{id}", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("export", exportService.getExportJob(id)));
        });
    }
}
