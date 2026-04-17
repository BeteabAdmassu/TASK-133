package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.dto.PagedResponse;
import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.service.UserService;
import io.javalin.Javalin;

import java.util.Map;

public class UserRoutes {

    public static void register(Javalin app, UserService userService) {
        app.get("/api/users", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            int page = Integer.parseInt(ctx.queryParamAsClass("page", String.class).getOrDefault("1"));
            int pageSize = Math.min(Integer.parseInt(ctx.queryParamAsClass("pageSize", String.class).getOrDefault("50")), 500);
            ctx.json(PagedResponse.of(userService.listUsers(page, pageSize)));
        });

        app.post("/api/users", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            User user = userService.createUser(
                body.get("username"), body.get("password"), body.get("displayName"),
                body.get("role"), body.get("staffId")
            );
            ctx.status(201).json(Map.of("user", user));
        });

        app.get("/api/users/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("user", userService.getUser(id)));
        });

        app.put("/api/users/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            User user = userService.updateUser(
                id,
                (String) body.get("displayName"),
                (String) body.get("role"),
                body.get("isActive") != null ? (Boolean) body.get("isActive") : null,
                (String) body.get("staffId")
            );
            ctx.json(Map.of("user", user));
        });

        app.delete("/api/users/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            long id = Long.parseLong(ctx.pathParam("id"));
            userService.deactivateUser(id);
            ctx.status(204);
        });
    }
}
