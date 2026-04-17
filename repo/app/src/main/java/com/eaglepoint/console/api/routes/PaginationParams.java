package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.exception.ValidationException;
import io.javalin.http.Context;

/**
 * Shared pagination parser for list endpoints.
 *
 * <p>Enforces the prompt's rule: {@code page >= 1} and
 * {@code 1 <= pageSize <= 500}.  Invalid input produces a 400 with a
 * structured validation error (rather than a silent clamp or a 500 from a
 * NumberFormatException) so clients can surface the problem to the user.</p>
 */
public final class PaginationParams {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 500;

    public final int page;
    public final int pageSize;

    private PaginationParams(int page, int pageSize) {
        this.page = page;
        this.pageSize = pageSize;
    }

    public static PaginationParams from(Context ctx) {
        String pageRaw = ctx.queryParam("page");
        String sizeRaw = ctx.queryParam("pageSize");
        int page = parseInt("page", pageRaw, DEFAULT_PAGE);
        int pageSize = parseInt("pageSize", sizeRaw, DEFAULT_PAGE_SIZE);
        if (page < 1) {
            throw new ValidationException("page", "page must be >= 1 (got " + page + ")");
        }
        if (pageSize < 1) {
            throw new ValidationException("pageSize", "pageSize must be >= 1 (got " + pageSize + ")");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new ValidationException("pageSize",
                "pageSize must be <= " + MAX_PAGE_SIZE + " (got " + pageSize + ")");
        }
        return new PaginationParams(page, pageSize);
    }

    private static int parseInt(String field, String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(field, field + " must be an integer (got '" + raw + "')");
        }
    }
}
