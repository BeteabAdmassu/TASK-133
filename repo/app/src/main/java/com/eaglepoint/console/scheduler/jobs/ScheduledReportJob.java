package com.eaglepoint.console.scheduler.jobs;

import com.eaglepoint.console.config.AppConfig;
import com.eaglepoint.console.model.ExportJob;
import com.eaglepoint.console.scheduler.JobScheduler;
import com.eaglepoint.console.service.ExportService;
import com.eaglepoint.console.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;

/**
 * Runs a saved report through the real export pipeline.
 *
 * <p>The scheduled_job's {@code config_json} is the source of truth for the
 * report definition and must contain at minimum:
 * <pre>
 * {
 *   "entityType":      "COMMUNITIES",            // required
 *   "format":          "EXCEL" | "PDF" | "CSV",  // optional, default EXCEL
 *   "destinationPath": "/var/reports/daily",     // optional, falls back to AppConfig.getBackupDir()/reports
 *   "filtersJson":     "{...}"                   // optional
 * }
 * </pre>
 *
 * <p>Execution goes through {@link ExportService#createExportJob} so the
 * resulting artefact is persisted in {@code export_jobs}, writes through the
 * crash-safe {@code .part} → rename path, and has a SHA-256 sidecar — the
 * exact same pipeline ad-hoc operator exports use.</p>
 */
public class ScheduledReportJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(ScheduledReportJob.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap data = context.getJobDetail().getJobDataMap();
        String configJson = data.getString("configJson");
        log.info("ScheduledReportJob starting (config={})", configJson);

        ExportService exportService = JobScheduler.getExportService();
        if (exportService == null) {
            String msg = "ScheduledReportJob cannot execute: ExportService not registered with JobScheduler";
            log.error(msg);
            NotificationService.getInstance().addAlert("ERROR", msg, "ScheduledReport", null);
            return;
        }

        try {
            Map<String, Object> cfg = parseConfig(configJson);
            String entityType = asString(cfg.get("entityType"));
            if (entityType == null || entityType.isBlank()) {
                throw new IllegalArgumentException("ScheduledReport config is missing 'entityType'");
            }
            String format = asString(cfg.getOrDefault("format", "EXCEL"));
            String destinationPath = asString(cfg.get("destinationPath"));
            if (destinationPath == null || destinationPath.isBlank()) {
                destinationPath = defaultDestination();
            }
            String filtersJson = asString(cfg.get("filtersJson"));

            long initiatedBy = JobScheduler.getSchedulerUserId();
            ExportJob job = exportService.createExportJob(
                format, entityType, destinationPath, filtersJson, initiatedBy);

            log.info("Scheduled report queued as export job {} (entity={}, format={}, dest={})",
                job.getId(), entityType, format, destinationPath);
            NotificationService.getInstance().addAlert("INFO",
                "Scheduled report started: " + entityType + " -> " + destinationPath
                    + " (export job #" + job.getId() + ")",
                "ScheduledReport", job.getId());
        } catch (Exception e) {
            log.error("ScheduledReportJob failed: {}", e.getMessage(), e);
            NotificationService.getInstance().addAlert("ERROR",
                "Scheduled report failed at " + Instant.now() + ": " + e.getMessage(),
                "ScheduledReport", null);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) throws Exception {
        if (configJson == null || configJson.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(configJson, Map.class);
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String defaultDestination() {
        Path root;
        try {
            root = Paths.get(AppConfig.getInstance().getBackupDir()).toAbsolutePath();
        } catch (Exception e) {
            root = Paths.get("data", "backups").toAbsolutePath();
        }
        return root.resolve("reports").toString();
    }
}
