package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.security.UpdateSignatureVerifier;
import com.eaglepoint.console.service.UpdateService;
import io.javalin.Javalin;

import java.util.Map;

/**
 * Admin-only endpoints exposing the offline updater lifecycle.
 *
 * <p>Every route requires {@code SYSTEM_ADMIN}.  The routes return
 * structured JSON — signatures themselves are never echoed in responses
 * (only status + human-readable reason).</p>
 */
public class UpdateRoutes {

    public static void register(Javalin app, UpdateService updateService) {

        app.get("/api/updates/packages", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            ctx.json(Map.of("data", updateService.listAvailablePackages()));
        });

        app.post("/api/updates/packages/{name}/verify", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            String name = ctx.pathParam("name");
            UpdateSignatureVerifier.Result result = updateService.verifyPackage(name);
            if (!result.isValid()) {
                ctx.status(400).json(Map.of(
                    "signatureStatus", result.getStatus(),
                    "valid", false,
                    "reason", result.getReason()
                ));
                return;
            }
            ctx.json(Map.of(
                "signatureStatus", result.getStatus(),
                "valid", true,
                "reason", result.getReason()
            ));
        });

        app.post("/api/updates/packages/{name}/apply", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            User user = AuthMiddleware.getCurrentUser(ctx);
            String name = ctx.pathParam("name");
            var entry = updateService.applyPackage(name, user.getId());
            ctx.status(200).json(Map.of("update", entry));
        });

        app.post("/api/updates/rollback", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            User user = AuthMiddleware.getCurrentUser(ctx);
            var entry = updateService.rollback(user.getId());
            ctx.status(200).json(Map.of("update", entry));
        });

        app.get("/api/updates/history", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "AUDITOR");
            int limit = parseLimit(ctx.queryParam("limit"));
            ctx.json(Map.of("data", updateService.history(limit)));
        });

        app.get("/api/updates/current", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "AUDITOR");
            ctx.json(Map.of("current",
                updateService.currentInstalled().orElse(null)));
        });
    }

    private static int parseLimit(String raw) {
        if (raw == null || raw.isBlank()) return 20;
        try { return Integer.parseInt(raw.trim()); }
        catch (NumberFormatException e) { return 20; }
    }
}
