package com.eaglepoint.console.api.middleware;

import com.eaglepoint.console.exception.UnauthorizedException;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.service.AuthService;
import io.javalin.http.Context;
import io.javalin.http.Handler;

public class AuthMiddleware {
    private final AuthService authService;

    public AuthMiddleware(AuthService authService) {
        this.authService = authService;
    }

    public Handler handle() {
        return ctx -> {
            String path = ctx.path();
            // Skip auth for health and login
            if (path.equals("/api/health") || path.equals("/api/auth/login")) {
                return;
            }
            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new UnauthorizedException("Missing or invalid Authorization header");
            }
            String rawToken = authHeader.substring(7);
            User user = authService.validateToken(rawToken);
            ctx.attribute("currentUser", user);
            ctx.attribute("rawToken", rawToken);
        };
    }

    public static User getCurrentUser(Context ctx) {
        User user = ctx.attribute("currentUser");
        if (user == null) throw new UnauthorizedException("Not authenticated");
        return user;
    }

    public static void requireRoles(Context ctx, String... roles) {
        User user = getCurrentUser(ctx);
        for (String role : roles) {
            if (role.equals(user.getRole())) return;
        }
        throw new com.eaglepoint.console.exception.ForbiddenException(
            "Insufficient permissions. Required: " + String.join(" or ", roles)
        );
    }
}
