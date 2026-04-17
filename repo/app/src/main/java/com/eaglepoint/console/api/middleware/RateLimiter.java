package com.eaglepoint.console.api.middleware;

import com.eaglepoint.console.model.User;
import io.javalin.http.Handler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
    /**
     * Default production rate limit: 60 requests per minute per user (business rule).
     * May be overridden for tests via the {@code RATE_LIMIT_MAX} env var or
     * {@code rate.limit.max} system property.  The production deployment does
     * not set these, so the rule remains 60/min in live use.
     */
    private static final int MAX_REQUESTS = resolveLimit();
    private static final long WINDOW_MILLIS = 60_000;

    private final ConcurrentHashMap<Long, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();

    private static int resolveLimit() {
        String prop = System.getProperty("rate.limit.max");
        if (prop == null || prop.isBlank()) prop = System.getenv("RATE_LIMIT_MAX");
        if (prop != null && !prop.isBlank()) {
            try { return Integer.parseInt(prop.trim()); } catch (NumberFormatException ignored) {}
        }
        return 60;
    }

    public Handler handle() {
        return ctx -> {
            User user = ctx.attribute("currentUser");
            if (user == null) return; // Not authenticated, skip rate limiting

            long userId = user.getId();
            long now = System.currentTimeMillis();

            requestTimestamps.compute(userId, (id, deque) -> {
                if (deque == null) deque = new ArrayDeque<>();
                // Remove entries older than 60 seconds
                while (!deque.isEmpty() && deque.peekFirst() < now - WINDOW_MILLIS) {
                    deque.pollFirst();
                }
                if (deque.size() >= MAX_REQUESTS) {
                    ctx.status(429);
                    ctx.json(java.util.Map.of("error", java.util.Map.of(
                        "code", "RATE_LIMIT_EXCEEDED",
                        "message", "Rate limit exceeded. Maximum 60 requests per minute."
                    )));
                    ctx.skipRemainingHandlers();
                } else {
                    deque.addLast(now);
                }
                return deque;
            });
        };
    }
}
