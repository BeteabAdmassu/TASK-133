package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.dto.PagedResponse;
import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.model.BedState;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.service.BedService;
import io.javalin.Javalin;

import java.util.Map;

public class BedRoutes {

    public static void register(Javalin app, BedService bedService) {

        // --- Bed Buildings ---
        app.get("/api/bed-buildings", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            ctx.json(PagedResponse.of(bedService.listBuildings(page, pageSize)));
        });

        app.post("/api/bed-buildings", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Long serviceAreaId = body.get("serviceAreaId") != null
                ? Long.parseLong(body.get("serviceAreaId").toString()) : null;
            ctx.status(201).json(Map.of("building", bedService.createBuilding(
                (String) body.get("name"), (String) body.get("address"), serviceAreaId
            )));
        });

        app.get("/api/bed-buildings/{id}", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("building", bedService.getBuilding(id)));
        });

        app.put("/api/bed-buildings/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            @SuppressWarnings("unchecked")
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            ctx.json(Map.of("building", bedService.updateBuilding(
                id, (String) body.get("name"), (String) body.get("address")
            )));
        });

        app.delete("/api/bed-buildings/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            long id = Long.parseLong(ctx.pathParam("id"));
            bedService.deleteBuilding(id);
            ctx.status(204);
        });

        // --- Bed Rooms ---
        app.get("/api/rooms", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            Long buildingId = ctx.queryParam("buildingId") != null ? Long.parseLong(ctx.queryParam("buildingId")) : null;
            ctx.json(PagedResponse.of(bedService.listRoomsPaged(buildingId, page, pageSize)));
        });

        app.post("/api/rooms", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            long buildingId = Long.parseLong(body.get("buildingId").toString());
            Integer floor = body.get("floor") != null ? Integer.parseInt(body.get("floor").toString()) : null;
            String roomType = (String) body.get("roomType");
            ctx.status(201).json(Map.of("room", bedService.createRoom(
                buildingId, (String) body.get("roomNumber"), floor, roomType
            )));
        });

        app.get("/api/rooms/{id}", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("room", bedService.getRoom(id)));
        });

        app.put("/api/rooms/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            @SuppressWarnings("unchecked")
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Integer floor = body.get("floor") != null ? Integer.parseInt(body.get("floor").toString()) : null;
            ctx.json(Map.of("room", bedService.updateRoom(
                id, (String) body.get("roomNumber"), floor, (String) body.get("roomType")
            )));
        });

        app.delete("/api/rooms/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            long id = Long.parseLong(ctx.pathParam("id"));
            bedService.deleteRoom(id);
            ctx.status(204);
        });

        // --- Beds ---
        app.get("/api/beds", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            Long roomId = ctx.queryParam("roomId") != null ? Long.parseLong(ctx.queryParam("roomId")) : null;
            Long buildingId = ctx.queryParam("buildingId") != null ? Long.parseLong(ctx.queryParam("buildingId")) : null;
            String stateFilter = ctx.queryParam("state");
            var result = (roomId != null || buildingId != null)
                ? bedService.listBeds(roomId, buildingId, page, pageSize)
                : bedService.listAllBeds(page, pageSize, stateFilter);
            if (ctx.queryParam("sort") != null || ctx.queryParam("fields") != null) {
                ctx.json(com.eaglepoint.console.api.QueryShaper.shape(ctx, result));
            } else {
                ctx.json(PagedResponse.of(result));
            }
        });

        app.post("/api/beds", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            long roomId = Long.parseLong(body.get("roomId").toString());
            ctx.status(201).json(Map.of("bed", bedService.createBed(
                roomId, (String) body.get("bedLabel")
            )));
        });

        app.get("/api/beds/{id}", ctx -> {
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            boolean unmask = "SYSTEM_ADMIN".equals(user.getRole());
            ctx.json(Map.of("bed", bedService.getBed(id, unmask)));
        });

        app.put("/api/beds/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            long id = Long.parseLong(ctx.pathParam("id"));
            @SuppressWarnings("unchecked")
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            ctx.json(Map.of("bed", bedService.updateBed(id, (String) body.get("bedLabel"))));
        });

        app.delete("/api/beds/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            long id = Long.parseLong(ctx.pathParam("id"));
            bedService.deleteBed(id);
            ctx.status(204);
        });

        app.post("/api/beds/{id}/transition", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "OPS_MANAGER");
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            @SuppressWarnings("unchecked")
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String toStateStr = (String) body.get("toState");
            BedState toState = BedState.valueOf(toStateStr.toUpperCase());
            String residentId = (String) body.get("residentId");
            String reason = (String) body.get("reason");
            String notes = (String) body.get("notes");
            ctx.json(bedService.transitionBed(id, toState, residentId, reason, notes, user.getId(), null));
        });

        app.get("/api/beds/{id}/history", ctx -> {
            AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            ctx.json(PagedResponse.of(bedService.getBedHistoryPaged(id, page, pageSize)));
        });
    }
}
