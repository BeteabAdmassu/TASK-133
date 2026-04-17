package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.QueryShaper;
import com.eaglepoint.console.api.dto.PagedResponse;
import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.service.GeozoneService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.util.List;
import java.util.Map;

public class GeozoneRoutes {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void register(Javalin app, GeozoneService geozoneService) {

        app.get("/api/geozones", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            int page = Integer.parseInt(ctx.queryParamAsClass("page", String.class).getOrDefault("1"));
            int pageSize = Math.min(Integer.parseInt(ctx.queryParamAsClass("pageSize", String.class).getOrDefault("50")), 500);
            var result = geozoneService.listGeozones(page, pageSize);
            if (ctx.queryParam("sort") != null || ctx.queryParam("fields") != null) {
                ctx.json(QueryShaper.shape(ctx, result));
            } else {
                ctx.json(PagedResponse.of(result));
            }
        });

        app.post("/api/geozones", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String zipCodesJson = toJsonOrPassthrough(body.get("zipCodes"));
            ctx.status(201).json(Map.of("geozone", geozoneService.createGeozone(
                (String) body.get("name"),
                zipCodesJson,
                (String) body.get("streetRangesJson")
            )));
        });

        app.get("/api/geozones/{id}", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("geozone", geozoneService.getGeozone(id)));
        });

        app.put("/api/geozones/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String zipCodesJson = body.get("zipCodes") != null ? toJsonOrPassthrough(body.get("zipCodes")) : null;
            ctx.json(Map.of("geozone", geozoneService.updateGeozone(
                id,
                (String) body.get("name"),
                zipCodesJson,
                (String) body.get("streetRangesJson")
            )));
        });

        app.delete("/api/geozones/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            long id = Long.parseLong(ctx.pathParam("id"));
            geozoneService.deleteGeozone(id);
            ctx.status(204);
        });
    }

    @SuppressWarnings("unchecked")
    private static String toJsonOrPassthrough(Object value) throws Exception {
        if (value == null) return null;
        if (value instanceof String s) return s;
        if (value instanceof List) return MAPPER.writeValueAsString((List<Object>) value);
        return MAPPER.writeValueAsString(value);
    }
}
