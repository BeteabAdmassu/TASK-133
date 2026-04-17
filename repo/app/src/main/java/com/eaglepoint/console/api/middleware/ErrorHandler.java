package com.eaglepoint.console.api.middleware;

import com.eaglepoint.console.exception.AppException;
import com.eaglepoint.console.exception.ValidationException;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    public static void register(Javalin app) {
        app.exception(ValidationException.class, (ex, ctx) -> {
            Map<String, Object> error = new HashMap<>();
            error.put("code", ex.getCode());
            error.put("message", ex.getMessage());
            if (!ex.getFieldErrors().isEmpty()) {
                error.put("fields", ex.getFieldErrors());
            }
            ctx.status(ex.getHttpStatus()).json(Map.of("error", error));
        });

        app.exception(AppException.class, (ex, ctx) -> {
            Map<String, Object> error = new HashMap<>();
            error.put("code", ex.getCode());
            error.put("message", ex.getMessage());
            ctx.status(ex.getHttpStatus()).json(Map.of("error", error));
        });

        app.exception(Exception.class, (ex, ctx) -> {
            log.error("Unhandled exception on {}: {}", ctx.path(), ex.getMessage(), ex);
            ctx.status(500).json(Map.of("error", Map.of(
                "code", "INTERNAL_ERROR",
                "message", "An unexpected error occurred"
            )));
        });
    }
}
