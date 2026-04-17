package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.ForbiddenException;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.export.CsvExporter;
import com.eaglepoint.console.export.ExcelExporter;
import com.eaglepoint.console.export.FingerprintUtil;
import com.eaglepoint.console.export.PdfExporter;
import com.eaglepoint.console.model.ExportJob;
import com.eaglepoint.console.repository.ExportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExportService {
    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private final ExportJobRepository exportRepo;
    private final ExecutorService executor;

    public ExportService(ExportJobRepository exportRepo) {
        this.exportRepo = exportRepo;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "export-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public ExportJob createExportJob(String type, String entityType, String destinationPath,
                                      String filtersJson, long initiatedBy) {
        if (!List.of("EXCEL", "PDF", "CSV").contains(type)) {
            throw new com.eaglepoint.console.exception.ValidationException("type", "Type must be EXCEL, PDF, or CSV");
        }
        if (destinationPath == null || destinationPath.isBlank()) {
            throw new com.eaglepoint.console.exception.ValidationException("destinationPath", "Destination path is required");
        }
        checkFolderWritable(destinationPath);

        ExportJob job = new ExportJob();
        job.setType(type);
        job.setEntityType(entityType != null ? entityType : "GENERIC");
        job.setFiltersJson(filtersJson);
        job.setDestinationPath(destinationPath);
        job.setInitiatedBy(initiatedBy);

        long id = exportRepo.insert(job);
        exportRepo.updateStarted(id);
        final long jobId = id;
        executor.submit(() -> executeJob(jobId));
        return exportRepo.findById(id).orElseThrow();
    }

    public ExportJob getExportJob(long id) {
        return exportRepo.findById(id).orElseThrow(() -> new NotFoundException("ExportJob", id));
    }

    public void executeJob(long jobId) {
        ExportJob job = exportRepo.findById(jobId).orElse(null);
        if (job == null) return;
        try {
            Path destDir = Paths.get(job.getDestinationPath());
            Files.createDirectories(destDir);
            String extension = job.getType().equals("EXCEL") ? ".xlsx" : job.getType().equals("PDF") ? ".pdf" : ".csv";
            String filename = job.getEntityType() + "_export_" + System.currentTimeMillis() + extension;
            Path outputPath = destDir.resolve(filename);

            List<Map<String, Object>> rows = buildSampleData(job);
            List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());

            switch (job.getType()) {
                case "EXCEL" -> new ExcelExporter().write(rows, columns, outputPath);
                case "PDF" -> new PdfExporter().write(rows, columns, job.getEntityType() + " Export", outputPath);
                case "CSV" -> new CsvExporter().write(rows, columns, outputPath);
            }

            String sha256 = FingerprintUtil.writeSha256Sidecar(outputPath);
            exportRepo.updateStatus(jobId, "COMPLETED", outputPath.toString(), sha256, null);
            log.info("Export job {} completed: {}", jobId, outputPath);
        } catch (ForbiddenException e) {
            exportRepo.updateStatus(jobId, "FAILED", null, null, e.getMessage());
        } catch (Exception e) {
            log.error("Export job {} failed: {}", jobId, e.getMessage(), e);
            exportRepo.updateStatus(jobId, "FAILED", null, null, e.getMessage());
        }
    }

    public void resumeIncompleteJobs() {
        List<ExportJob> incomplete = exportRepo.findIncomplete();
        for (ExportJob job : incomplete) {
            log.info("Resuming export job {}", job.getId());
            executor.submit(() -> executeJob(job.getId()));
        }
    }

    private void checkFolderWritable(String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) {
                Files.createDirectories(p);
            }
            if (!Files.isWritable(p)) {
                throw new ForbiddenException("Destination folder is not writable: " + path);
            }
        } catch (ForbiddenException e) {
            throw e;
        } catch (Exception e) {
            throw new ForbiddenException("Cannot access destination folder: " + path + " - " + e.getMessage());
        }
    }

    private List<Map<String, Object>> buildSampleData(ExportJob job) {
        // In production: query data based on entityType and filtersJson
        // Return minimal placeholder data for now
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("export_type", job.getEntityType());
        row.put("generated_at", Instant.now().toString());
        row.put("job_id", job.getId());
        rows.add(row);
        return rows;
    }
}
