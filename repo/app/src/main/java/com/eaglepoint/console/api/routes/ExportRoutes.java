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
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            var job = exportService.getExportJob(id);
            // Object-level authorization: only the user who initiated the
            // export OR a system admin (incident/audit-review access) may
            // view another user's export job.  AUDITOR is explicitly not
            // allowed cross-user visibility here — their read access is to
            // audit trail endpoints, not other users' export artefacts.
            boolean isOwner = job.getInitiatedBy() == user.getId();
            boolean isAdmin = "SYSTEM_ADMIN".equals(user.getRole());
            if (!isOwner && !isAdmin) {
                ctx.status(403).json(Map.of("error", Map.of(
                    "code", "FORBIDDEN",
                    "message", "You do not have access to this export job"
                )));
                return;
            }
            ctx.json(Map.of("export", job));
        });
    }
}
