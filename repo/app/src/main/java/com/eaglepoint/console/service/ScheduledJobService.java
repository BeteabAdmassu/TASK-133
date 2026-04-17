package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.ScheduledJobConfig;
import com.eaglepoint.console.repository.ScheduledJobRepository;
import com.eaglepoint.console.scheduler.JobScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRUD + validation layer for scheduled jobs.
 *
 * <p>The lifecycle is:
 * <pre>
 *   POST /api/jobs               → {@link #create(ScheduledJobConfig, long)}
 *   PUT  /api/jobs/{id}          → {@link #update(long, ScheduledJobConfig, long)}
 *   DELETE /api/jobs/{id}        → {@link #delete(long, long)}
 *   POST /api/jobs/{id}/pause    → handled by JobScheduler (already existed)
 *   POST /api/jobs/{id}/resume   → handled by JobScheduler
 * </pre>
 *
 * <p>REPORT jobs require a {@code config_json} payload that names at minimum
 * the {@code entityType} to export.  The validation runs server-side so
 * operators cannot land a broken REPORT job by hand-editing the DB or
 * hitting a raw cron write.</p>
 */
public class ScheduledJobService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobService.class);
    private static final Set<String> VALID_JOB_TYPES = Set.of(
        "BACKUP", "ARCHIVE", "CONSISTENCY_CHECK", "REPORT");
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "PAUSED");
    private static final Set<String> VALID_EXPORT_FORMATS = Set.of("EXCEL", "PDF", "CSV");
    private static final Set<String> VALID_REPORT_ENTITIES = Set.of(
        "COMMUNITIES", "SERVICE_AREAS", "PICKUP_POINTS", "BEDS", "BED_BUILDINGS",
        "ROOMS", "USERS", "KPIS", "KPI_SCORES", "GEOZONES");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ScheduledJobRepository repo;
    private final AuditService auditService;

    public ScheduledJobService(ScheduledJobRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    public List<ScheduledJobConfig> list() {
        return repo.findAll();
    }

    public ScheduledJobConfig get(long id) {
        return repo.findById(id)
            .orElseThrow(() -> new NotFoundException("ScheduledJob", id));
    }

    public ScheduledJobConfig create(ScheduledJobConfig input, long initiatedBy) {
        validateJobType(input.getJobType());
        validateCron(input.getCronExpression());
        validateTimeout(input.getTimeoutSeconds());
        if (input.getStatus() == null) input.setStatus("ACTIVE");
        validateStatus(input.getStatus());
        validateConfigJson(input.getJobType(), input.getConfigJson());

        long id = repo.insert(input);
        ScheduledJobConfig saved = repo.findById(id).orElseThrow();
        auditService.record("ScheduledJob", id, "CREATE", initiatedBy, null, null, snapshot(saved), null);

        // Re-register with Quartz so the cron fires on schedule without a
        // restart.  Scheduler.register is idempotent — it replaces any
        // existing registration for the same db id.
        try {
            JobScheduler.getInstance().registerOrReplace(saved);
        } catch (Exception e) {
            log.warn("Job created but Quartz registration failed for {}: {}", id, e.getMessage());
        }
        return saved;
    }

    public ScheduledJobConfig update(long id, ScheduledJobConfig patch, long initiatedBy) {
        ScheduledJobConfig existing = repo.findById(id)
            .orElseThrow(() -> new NotFoundException("ScheduledJob", id));
        Map<String, Object> before = snapshot(existing);

        if (patch.getCronExpression() != null) {
            validateCron(patch.getCronExpression());
            existing.setCronExpression(patch.getCronExpression());
        }
        if (patch.getTimeoutSeconds() > 0) {
            validateTimeout(patch.getTimeoutSeconds());
            existing.setTimeoutSeconds(patch.getTimeoutSeconds());
        }
        if (patch.getStatus() != null) {
            validateStatus(patch.getStatus());
            existing.setStatus(patch.getStatus());
        }
        if (patch.getConfigJson() != null) {
            validateConfigJson(existing.getJobType(), patch.getConfigJson());
            existing.setConfigJson(patch.getConfigJson());
        }

        repo.update(existing);
        auditService.record("ScheduledJob", id, "UPDATE", initiatedBy, null, before, snapshot(existing), null);
        try {
            JobScheduler.getInstance().registerOrReplace(existing);
        } catch (Exception e) {
            log.warn("Quartz re-registration failed for {}: {}", id, e.getMessage());
        }
        return existing;
    }

    public void delete(long id, long initiatedBy) {
        ScheduledJobConfig existing = repo.findById(id)
            .orElseThrow(() -> new NotFoundException("ScheduledJob", id));
        try {
            JobScheduler.getInstance().unregister(existing);
        } catch (Exception e) {
            log.warn("Quartz unregister failed for {}: {}", id, e.getMessage());
        }
        repo.delete(id);
        auditService.record("ScheduledJob", id, "DELETE", initiatedBy, null, snapshot(existing), null, null);
    }

    // ─── validation ──────────────────────────────────────────────────────────

    private void validateJobType(String jobType) {
        if (jobType == null || !VALID_JOB_TYPES.contains(jobType)) {
            throw new ValidationException("jobType",
                "jobType must be one of " + VALID_JOB_TYPES);
        }
    }

    private void validateCron(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new ValidationException("cronExpression", "cronExpression is required");
        }
        if (!CronExpression.isValidExpression(cron.trim())) {
            throw new ValidationException("cronExpression",
                "cronExpression is not a valid Quartz cron: " + cron);
        }
    }

    private void validateTimeout(int timeoutSeconds) {
        if (timeoutSeconds < 0 || timeoutSeconds > 86400) {
            throw new ValidationException("timeoutSeconds",
                "timeoutSeconds must be between 0 and 86400");
        }
    }

    private void validateStatus(String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new ValidationException("status",
                "status must be one of " + VALID_STATUSES);
        }
    }

    /**
     * Schema check for {@code config_json}.  REPORT is the only job type
     * that currently has required config fields; other job types accept any
     * parseable JSON or null.
     */
    @SuppressWarnings("unchecked")
    private void validateConfigJson(String jobType, String configJson) {
        if (configJson == null || configJson.isBlank()) {
            if ("REPORT".equals(jobType)) {
                throw new ValidationException("configJson",
                    "REPORT jobs require configJson with at least { entityType }");
            }
            return;
        }
        Map<String, Object> cfg;
        try {
            cfg = MAPPER.readValue(configJson, Map.class);
        } catch (Exception e) {
            throw new ValidationException("configJson",
                "configJson must be valid JSON: " + e.getMessage());
        }
        if ("REPORT".equals(jobType)) {
            Object entityType = cfg.get("entityType");
            if (!(entityType instanceof String s) || s.isBlank()) {
                throw new ValidationException("configJson.entityType",
                    "REPORT configJson.entityType is required");
            }
            if (!VALID_REPORT_ENTITIES.contains(s)) {
                throw new ValidationException("configJson.entityType",
                    "entityType must be one of " + VALID_REPORT_ENTITIES);
            }
            Object format = cfg.get("format");
            if (format != null && !(format instanceof String fs && VALID_EXPORT_FORMATS.contains(fs))) {
                throw new ValidationException("configJson.format",
                    "format must be one of " + VALID_EXPORT_FORMATS);
            }
            Object dest = cfg.get("destinationPath");
            if (dest != null && !(dest instanceof String)) {
                throw new ValidationException("configJson.destinationPath",
                    "destinationPath must be a string");
            }
        }
    }

    private Map<String, Object> snapshot(ScheduledJobConfig j) {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("jobType", j.getJobType());
        m.put("cronExpression", j.getCronExpression());
        m.put("timeoutSeconds", j.getTimeoutSeconds());
        m.put("status", j.getStatus());
        m.put("configJson", j.getConfigJson());
        return m;
    }
}
