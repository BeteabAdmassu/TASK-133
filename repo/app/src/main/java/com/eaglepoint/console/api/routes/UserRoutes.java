package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.QueryShaper;
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
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            var result = userService.listUsers(page, pageSize);
            if (ctx.queryParam("sort") != null || ctx.queryParam("fields") != null) {
                ctx.json(QueryShaper.shape(ctx, result));
            } else {
                ctx.json(PagedResponse.of(result));
            }
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
