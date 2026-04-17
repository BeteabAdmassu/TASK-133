package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.dto.auth.LoginRequest;
import com.eaglepoint.console.api.dto.auth.LoginResponse;
import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.service.AuthService;
import io.javalin.Javalin;

import java.util.Map;

public class AuthRoutes {

    public static void register(Javalin app, AuthService authService) {
        app.post("/api/auth/login", ctx -> {
            LoginRequest req = ctx.bodyAsClass(LoginRequest.class);
            Map<String, Object> result = authService.login(req.getUsername(), req.getPassword());
            User user = (User) result.get("user");
            String token = (String) result.get("token");
            String expiresAt = (String) result.get("expiresAt");
            ctx.status(200).json(new LoginResponse(user, token, expiresAt));
        });

        app.post("/api/auth/logout", ctx -> {
            String rawToken = ctx.attribute("rawToken");
            if (rawToken != null) {
                com.eaglepoint.console.security.TokenService ts = new com.eaglepoint.console.security.TokenService();
                authService.logout(ts.hashToken(rawToken));
            }
            ctx.status(200).json(Map.of("message", "Logged out successfully"));
        });

        app.get("/api/auth/me", ctx -> {
            User user = AuthMiddleware.getCurrentUser(ctx);
            ctx.json(Map.of("user", Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole(),
                "displayName", user.getDisplayName()
            )));
        });
    }
}
