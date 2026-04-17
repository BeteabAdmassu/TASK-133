package com.eaglepoint.console.service;

import com.eaglepoint.console.config.AppConfig;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.model.RouteCheckpoint;
import com.eaglepoint.console.model.RouteImport;
import com.eaglepoint.console.repository.RouteImportRepository;
import com.eaglepoint.console.security.MaskingUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Route-import service with crash-safe checkpoint resume.
 *
 * <p>The import pipeline is split into two phases:
 * <ol>
 *   <li><strong>Parse phase</strong> — validate the uploaded file, build the
 *       in-memory {@link RouteCheckpoint} list, and persist it as a JSON
 *       checkpoint file to disk (path recorded in
 *       {@code route_imports.checkpoint_path}).</li>
 *   <li><strong>Commit phase</strong> — read the checkpoint file back and
 *       insert rows sequentially, using the current row count in
 *       {@code route_checkpoints} as the resume pointer.  Already-committed
 *       rows are skipped, so the commit phase is fully idempotent.</li>
 * </ol>
 *
 * <p>If the JVM crashes during commit, {@link #resumeIncompleteImports()}
 * picks up any {@code PROCESSING} imports that still have a checkpoint file
 * and replays the commit from the first uncommitted index — no duplicates,
 * no lost rows.  Imports whose checkpoint file is missing (crash before
 * parse completed) are marked {@code FAILED}.
 */
public class RouteImportService {
    private static final Logger log = LoggerFactory.getLogger(RouteImportService.class);
    private static final double DEVIATION_ALERT_MILES = 0.5;
    private static final long MISSED_ALERT_MINUTES = 15;

    private final RouteImportRepository importRepo;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper mapper;
    private final ExecutorService executor;
    private final Path checkpointDir;

    public RouteImportService(RouteImportRepository importRepo, NotificationService notificationService,
                               AuditService auditService) {
        this(importRepo, notificationService, auditService, defaultCheckpointDir());
    }

    public RouteImportService(RouteImportRepository importRepo, NotificationService notificationService,
                               AuditService auditService, Path checkpointDir) {
        this.importRepo = importRepo;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.mapper = new ObjectMapper();
        this.checkpointDir = checkpointDir;
        try {
            Files.createDirectories(checkpointDir);
        } catch (IOException e) {
            log.warn("Could not create route-import checkpoint dir {}: {}", checkpointDir, e.getMessage());
        }
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "route-import");
            t.setDaemon(true);
            return t;
        });
    }

    private static Path defaultCheckpointDir() {
        String db;
        try {
            db = AppConfig.getInstance().getDbPath();
        } catch (Exception e) {
            db = "data/console.db";
        }
        Path root = Paths.get(db).toAbsolutePath().getParent();
        if (root == null) root = Paths.get(".").toAbsolutePath();
        return root.resolve("checkpoints").resolve("route-imports");
    }

    public RouteImport startImport(String filename, byte[] fileContent, long importedBy) {
        if (filename == null || (!filename.endsWith(".csv") && !filename.endsWith(".json"))) {
            throw new ValidationException("file", "File must have .csv or .json extension");
        }
        if (fileContent == null || fileContent.length == 0) {
            throw new ValidationException("file", "File must not be empty");
        }
        RouteImport ri = new RouteImport();
        ri.setFilename(filename);
        ri.setImportedBy(importedBy);
        long id = importRepo.insert(ri);
        ri = importRepo.findById(id).orElseThrow();

        final RouteImport finalRi = ri;
        final byte[] content = fileContent;
        executor.submit(() -> validateAndProcess(finalRi.getId(), content, filename.endsWith(".json")));
        return ri;
    }

    public void validateAndProcess(long importId, byte[] fileContent, boolean isJson) {
        importRepo.updateStatus(importId, "VALIDATING", null, null);
        try {
            List<RouteCheckpoint> checkpoints;
            if (isJson) {
                checkpoints = parseJson(importId, fileContent);
            } else {
                checkpoints = parseCsv(importId, fileContent);
            }
            annotateDeviations(checkpoints);

            // Persist parsed, annotated checkpoints as a resumable JSON sidecar
            // BEFORE we start inserting rows — if the JVM crashes mid-commit,
            // resumeIncompleteImports() can pick up exactly where we left off.
            Path checkpointFile = writeCheckpointFile(importId, checkpoints);
            importRepo.updateCheckpointPath(importId, checkpointFile.toString());
            importRepo.updateStatus(importId, "PROCESSING", null, null);

            commitFromCheckpoint(importId);
        } catch (ValidationException e) {
            importRepo.updateStatus(importId, "INVALID", null, null);
            notificationService.addAlert("ERROR", "Route import " + importId + " validation failed: " + e.getMessage(),
                "RouteImport", importId);
        } catch (Exception e) {
            log.error("Route import {} failed: {}", importId, e.getMessage(), e);
            importRepo.updateStatus(importId, "FAILED", null, null);
            notificationService.addAlert("ERROR", "Route import " + importId + " failed: " + e.getMessage(),
                "RouteImport", importId);
        }
    }

    /**
     * Commit phase — reads the persisted checkpoint file and inserts any
     * rows that were not yet committed.  Safe to call multiple times:
     * the resume pointer is {@code countCheckpoints(importId)} from the
     * database itself, so previously-committed rows are skipped cleanly.
     */
    public void commitFromCheckpoint(long importId) {
        RouteImport ri = importRepo.findById(importId).orElse(null);
        if (ri == null) return;
        String path = ri.getCheckpointPath();
        if (path == null || path.isBlank()) {
            // No checkpoint — nothing we can resume.
            importRepo.updateStatus(importId, "FAILED", null, null);
            notificationService.addAlert("ERROR",
                "Route import " + importId + " could not be resumed: checkpoint missing",
                "RouteImport", importId);
            return;
        }
        Path checkpointFile = Paths.get(path);
        List<RouteCheckpoint> checkpoints;
        try {
            checkpoints = readCheckpointFile(checkpointFile);
        } catch (IOException e) {
            log.error("Failed to read checkpoint {} for import {}: {}", checkpointFile, importId, e.getMessage());
            importRepo.updateStatus(importId, "FAILED", null, null);
            notificationService.addAlert("ERROR",
                "Route import " + importId + " could not be resumed: " + e.getMessage(),
                "RouteImport", importId);
            return;
        }

        int alreadyCommitted = importRepo.countCheckpoints(importId);
        int alertCount = importRepo.countAlertCheckpoints(importId);
        for (int i = alreadyCommitted; i < checkpoints.size(); i++) {
            RouteCheckpoint cp = checkpoints.get(i);
            cp.setImportId(importId);
            importRepo.insertCheckpoint(cp);
            if (cp.isDeviationAlert() || cp.isMissedAlert()) alertCount++;
        }
        importRepo.updateStatus(importId, "COMPLETED", checkpoints.size(), alertCount);

        // Clean up the on-disk checkpoint — the DB is now authoritative.
        try {
            Files.deleteIfExists(checkpointFile);
        } catch (IOException e) {
            log.warn("Could not delete checkpoint file {}: {}", checkpointFile, e.getMessage());
        }
        importRepo.updateCheckpointPath(importId, null);

        if (alertCount > 0) {
            notificationService.addAlert("WARN",
                "Route import " + importId + " completed with " + alertCount + " alerts",
                "RouteImport", importId);
        }
        auditService.record("RouteImport", importId, "COMPLETE", 0, null, null, null, null);
    }

    private Path writeCheckpointFile(long importId, List<RouteCheckpoint> checkpoints) throws IOException {
        Files.createDirectories(checkpointDir);
        Path finalFile = checkpointDir.resolve("import-" + importId + ".json");
        Path tmpFile = checkpointDir.resolve("import-" + importId + ".json.tmp");
        mapper.writeValue(tmpFile.toFile(), checkpoints);
        // Atomic rename so partial writes never look like a valid checkpoint.
        try {
            Files.move(tmpFile, finalFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmpFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return finalFile;
    }

    private List<RouteCheckpoint> readCheckpointFile(Path file) throws IOException {
        return mapper.readValue(file.toFile(), new TypeReference<List<RouteCheckpoint>>() {});
    }

    private List<RouteCheckpoint> parseCsv(long importId, byte[] content) throws Exception {
        List<RouteCheckpoint> checkpoints = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) throw new ValidationException("file", "CSV file is empty");
            int lineNum = 1;
            String[] row;
            while ((row = reader.readNext()) != null) {
                lineNum++;
                List<String> rowErrors = validateCsvRow(row, header, lineNum);
                if (!rowErrors.isEmpty()) {
                    errors.addAll(rowErrors);
                } else {
                    checkpoints.add(processRow(importId, row, header));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("CSV validation errors:\n" + String.join("\n", errors));
        }
        return checkpoints;
    }

    @SuppressWarnings("unchecked")
    private List<RouteCheckpoint> parseJson(long importId, byte[] content) throws Exception {
        List<Map<String, Object>> rows = mapper.readValue(content, List.class);
        List<RouteCheckpoint> checkpoints = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> row : rows) {
            i++;
            String name = (String) row.get("checkpoint_name");
            String expectedAt = (String) row.get("expected_at");
            String actualAt = (String) row.get("actual_at");
            Object latObj = row.get("lat");
            Object lonObj = row.get("lon");

            if (name == null || expectedAt == null || latObj == null || lonObj == null) {
                throw new ValidationException("Row " + i + ": Missing required fields");
            }
            double lat = ((Number) latObj).doubleValue();
            double lon = ((Number) lonObj).doubleValue();
            RouteCheckpoint cp = buildCheckpoint(importId, name, expectedAt, actualAt, lat, lon,
                (String) row.get("notes"));
            Object expLatObj = row.get("expected_lat");
            Object expLonObj = row.get("expected_lon");
            if (expLatObj instanceof Number && expLonObj instanceof Number) {
                cp.setExpectedLatMasked(MaskingUtil.maskLat(((Number) expLatObj).doubleValue()));
                cp.setExpectedLonMasked(MaskingUtil.maskLon(((Number) expLonObj).doubleValue()));
            }
            checkpoints.add(cp);
        }
        return checkpoints;
    }

    private List<String> validateCsvRow(String[] row, String[] header, int lineNum) {
        List<String> errors = new ArrayList<>();
        if (findCol(header, "checkpoint_name") < 0) errors.add("Line " + lineNum + ": missing column checkpoint_name");
        if (findCol(header, "expected_at") < 0) errors.add("Line " + lineNum + ": missing column expected_at");
        if (findCol(header, "lat") < 0) errors.add("Line " + lineNum + ": missing column lat");
        if (findCol(header, "lon") < 0) errors.add("Line " + lineNum + ": missing column lon");
        if (!errors.isEmpty()) return errors;

        String name = getCol(row, header, "checkpoint_name");
        if (name == null || name.isBlank()) errors.add("Line " + lineNum + ": checkpoint_name is required");
        String expectedAt = getCol(row, header, "expected_at");
        if (expectedAt == null || expectedAt.isBlank()) errors.add("Line " + lineNum + ": expected_at is required");
        try {
            double lat = Double.parseDouble(getCol(row, header, "lat"));
            if (lat < -90 || lat > 90) errors.add("Line " + lineNum + ": lat must be -90..90");
            double lon = Double.parseDouble(getCol(row, header, "lon"));
            if (lon < -180 || lon > 180) errors.add("Line " + lineNum + ": lon must be -180..180");
        } catch (NumberFormatException e) {
            errors.add("Line " + lineNum + ": lat and lon must be numeric");
        }
        return errors;
    }

    private RouteCheckpoint processRow(long importId, String[] row, String[] header) {
        String name = getCol(row, header, "checkpoint_name");
        String expectedAt = getCol(row, header, "expected_at");
        String actualAt = getCol(row, header, "actual_at");
        double lat = Double.parseDouble(getCol(row, header, "lat"));
        double lon = Double.parseDouble(getCol(row, header, "lon"));
        RouteCheckpoint cp = buildCheckpoint(importId, name, expectedAt, actualAt, lat, lon, null);
        String expLatStr = getCol(row, header, "expected_lat");
        String expLonStr = getCol(row, header, "expected_lon");
        if (expLatStr != null && !expLatStr.isBlank() && expLonStr != null && !expLonStr.isBlank()) {
            try {
                cp.setExpectedLatMasked(MaskingUtil.maskLat(Double.parseDouble(expLatStr)));
                cp.setExpectedLonMasked(MaskingUtil.maskLon(Double.parseDouble(expLonStr)));
            } catch (NumberFormatException ignored) {
            }
        }
        return cp;
    }

    private RouteCheckpoint buildCheckpoint(long importId, String name, String expectedAt,
                                              String actualAt, double lat, double lon, String notes) {
        RouteCheckpoint cp = new RouteCheckpoint();
        cp.setImportId(importId);
        cp.setCheckpointName(name);
        cp.setExpectedAt(expectedAt);
        cp.setActualAt(actualAt != null && !actualAt.isBlank() ? actualAt : null);
        cp.setLatMasked(MaskingUtil.maskLat(lat));
        cp.setLonMasked(MaskingUtil.maskLon(lon));

        if (actualAt == null || actualAt.isBlank()) {
            cp.setMissedAlert(true);
            cp.setStatus("MISSED");
        } else {
            try {
                Instant expected = Instant.parse(expectedAt);
                Instant actual = Instant.parse(actualAt);
                long minutesLate = ChronoUnit.MINUTES.between(expected, actual);
                if (minutesLate > MISSED_ALERT_MINUTES) {
                    cp.setMissedAlert(true);
                    cp.setStatus("DEVIATED");
                } else {
                    cp.setStatus("ON_TIME");
                }
            } catch (Exception e) {
                cp.setStatus("PENDING");
            }
        }
        return cp;
    }

    private int findCol(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private String getCol(String[] row, String[] header, String name) {
        int idx = findCol(header, name);
        if (idx < 0 || idx >= row.length) return null;
        return row[idx].trim();
    }

    private void annotateDeviations(List<RouteCheckpoint> checkpoints) {
        RouteCheckpoint prev = null;
        for (RouteCheckpoint cp : checkpoints) {
            Double lat = cp.getLatMasked();
            Double lon = cp.getLonMasked();
            Double expLat = cp.getExpectedLatMasked();
            Double expLon = cp.getExpectedLonMasked();

            Double miles = null;
            if (lat != null && lon != null && expLat != null && expLon != null) {
                miles = computeDeviationMiles(expLat, expLon, lat, lon);
            } else if (prev != null && lat != null && lon != null
                    && prev.getLatMasked() != null && prev.getLonMasked() != null) {
                miles = computeDeviationMiles(
                    prev.getLatMasked(), prev.getLonMasked(), lat, lon);
            }

            if (miles == null) {
                cp.setDeviationMiles(0.0);
                cp.setDeviationAlert(false);
            } else {
                cp.setDeviationMiles(miles);
                if (miles > DEVIATION_ALERT_MILES) {
                    cp.setDeviationAlert(true);
                    if (!"MISSED".equals(cp.getStatus())) {
                        cp.setStatus("DEVIATED");
                    }
                }
            }
            prev = cp;
        }
    }

    private double computeDeviationMiles(double lat1, double lon1, double lat2, double lon2) {
        final double R = 3958.8;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * On startup, finish any import that was mid-commit when the JVM last
     * stopped.  If the checkpoint sidecar exists, we replay the commit (which
     * skips already-inserted rows).  If not, the import is marked FAILED and
     * the operator must re-upload the file.
     */
    public void resumeIncompleteImports() {
        List<RouteImport> incomplete = importRepo.findIncomplete();
        for (RouteImport ri : incomplete) {
            try {
                String path = ri.getCheckpointPath();
                if (path != null && !path.isBlank() && Files.exists(Paths.get(path))) {
                    log.info("Resuming route import {} from checkpoint {}", ri.getId(), path);
                    final long id = ri.getId();
                    executor.submit(() -> commitFromCheckpoint(id));
                } else {
                    log.warn("Route import {} has no usable checkpoint; marking FAILED", ri.getId());
                    importRepo.updateStatus(ri.getId(), "FAILED", null, null);
                    notificationService.addAlert("WARN",
                        "Route import " + ri.getId() + " could not be resumed: checkpoint missing",
                        "RouteImport", ri.getId());
                }
            } catch (Exception e) {
                log.error("Failed to resume route import {}: {}", ri.getId(), e.getMessage(), e);
            }
        }
    }

    public PagedResult<RouteImport> listImports(int page, int pageSize) {
        return importRepo.findAll(page, pageSize);
    }

    public RouteImport getImport(long id) {
        return importRepo.findById(id)
            .orElseThrow(() -> new com.eaglepoint.console.exception.NotFoundException("RouteImport", id));
    }

    public PagedResult<RouteCheckpoint> getCheckpoints(long importId, int page, int pageSize) {
        importRepo.findById(importId)
            .orElseThrow(() -> new com.eaglepoint.console.exception.NotFoundException("RouteImport", importId));
        return importRepo.findCheckpointsByImport(importId, page, pageSize);
    }
}
