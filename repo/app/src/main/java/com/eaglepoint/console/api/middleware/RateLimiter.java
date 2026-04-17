package com.eaglepoint.console.api.middleware;

import com.eaglepoint.console.model.User;
import io.javalin.http.Handler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
    private static final int MAX_REQUESTS = 60;
    private static final long WINDOW_MILLIS = 60_000;

    private final ConcurrentHashMap<Long, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();

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
