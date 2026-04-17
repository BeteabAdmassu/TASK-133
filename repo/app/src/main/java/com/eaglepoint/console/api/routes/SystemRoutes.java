package com.eaglepoint.console.api.routes;

import com.eaglepoint.console.api.dto.PagedResponse;
import com.eaglepoint.console.api.middleware.AuthMiddleware;
import com.eaglepoint.console.config.AppConfig;
import com.eaglepoint.console.config.DatabaseConfig;
import com.eaglepoint.console.model.ScheduledJobConfig;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.repository.AuditTrailRepository;
import com.eaglepoint.console.repository.ScheduledJobRepository;
import com.eaglepoint.console.repository.SystemLogRepository;
import com.eaglepoint.console.scheduler.JobScheduler;
import com.eaglepoint.console.service.ScheduledJobService;
import io.javalin.Javalin;

import java.lang.management.ManagementFactory;
import java.util.Map;

public class SystemRoutes {

    public static void register(Javalin app,
                                AuditTrailRepository auditRepo,
                                SystemLogRepository logRepo,
                                ScheduledJobRepository jobRepo,
                                JobScheduler jobScheduler,
                                ScheduledJobService scheduledJobService) {

        app.get("/api/health", ctx -> {
            long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
            boolean dbOk;
            try {
                DatabaseConfig.getInstance().getDataSource().getConnection().close();
                dbOk = true;
            } catch (Exception e) {
                dbOk = false;
            }
            ctx.json(Map.of(
                "status", dbOk ? "UP" : "DEGRADED",
                "uptime", uptimeMs,
                "db", dbOk ? "OK" : "ERROR",
                "version", AppConfig.getInstance().getVersion()
            ));
        });

        app.get("/api/audit-trail", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "AUDITOR");
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            String entityType = ctx.queryParam("entityType");
            Long entityId = ctx.queryParam("entityId") != null ? Long.parseLong(ctx.queryParam("entityId")) : null;
            ctx.json(PagedResponse.of(auditRepo.findAll(entityType, entityId, null, null, null, page, pageSize)));
        });

        app.get("/api/logs", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN", "AUDITOR");
            var __p = PaginationParams.from(ctx);
            int page = __p.page;
            int pageSize = __p.pageSize;
            String level = ctx.queryParam("level");
            String category = ctx.queryParam("category");
            ctx.json(PagedResponse.of(logRepo.findAll(level, category, null, null, page, pageSize)));
        });

        app.get("/api/jobs", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            ctx.json(Map.of("data", scheduledJobService.list()));
        });

        app.get("/api/jobs/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(Map.of("job", scheduledJobService.get(id)));
        });

        app.post("/api/jobs", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            User user = AuthMiddleware.getCurrentUser(ctx);
            ScheduledJobConfig input = parseJobConfig(ctx.bodyAsClass(Map.class));
            ctx.status(201).json(Map.of("job",
                scheduledJobService.create(input, user.getId())));
        });

        app.put("/api/jobs/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            ScheduledJobConfig patch = parseJobConfig(ctx.bodyAsClass(Map.class));
            ctx.json(Map.of("job", scheduledJobService.update(id, patch, user.getId())));
        });

        app.delete("/api/jobs/{id}", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            User user = AuthMiddleware.getCurrentUser(ctx);
            long id = Long.parseLong(ctx.pathParam("id"));
            scheduledJobService.delete(id, user.getId());
            ctx.status(204);
        });

        app.post("/api/jobs/{id}/pause", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            long id = Long.parseLong(ctx.pathParam("id"));
            jobScheduler.pauseJob(id);
            ctx.json(Map.of("job", jobRepo.findById(id).orElseThrow()));
        });

        app.post("/api/jobs/{id}/resume", ctx -> {
            AuthMiddleware.requireRoles(ctx, "SYSTEM_ADMIN");
            long id = Long.parseLong(ctx.pathParam("id"));
            jobScheduler.resumeJob(id);
            ctx.json(Map.of("job", jobRepo.findById(id).orElseThrow()));
        });
    }

    @SuppressWarnings("unchecked")
    private static ScheduledJobConfig parseJobConfig(Map<String, Object> body) {
        ScheduledJobConfig j = new ScheduledJobConfig();
        Object jobType = body.get("jobType");
        if (jobType != null) j.setJobType(String.valueOf(jobType));
        Object cron = body.get("cronExpression");
        if (cron != null) j.setCronExpression(String.valueOf(cron));
        Object timeout = body.get("timeoutSeconds");
        if (timeout instanceof Number n) j.setTimeoutSeconds(n.intValue());
        Object status = body.get("status");
        if (status != null) j.setStatus(String.valueOf(status));
        Object configJson = body.get("configJson");
        if (configJson instanceof String s) {
            j.setConfigJson(s);
        } else if (configJson instanceof Map) {
            try {
                j.setConfigJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(configJson));
            } catch (Exception e) {
                throw new com.eaglepoint.console.exception.ValidationException("configJson",
                    "configJson is not serialisable: " + e.getMessage());
            }
        }
        return j;
    }
}
