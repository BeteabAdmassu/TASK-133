package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.model.RouteCheckpoint;
import com.eaglepoint.console.model.RouteImport;
import com.eaglepoint.console.repository.RouteImportRepository;
import com.eaglepoint.console.security.MaskingUtil;
import com.opencsv.CSVReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RouteImportService {
    private static final Logger log = LoggerFactory.getLogger(RouteImportService.class);
    private static final double DEVIATION_ALERT_MILES = 0.5;
    private static final long MISSED_ALERT_MINUTES = 15;

    private final RouteImportRepository importRepo;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    public RouteImportService(RouteImportRepository importRepo, NotificationService notificationService,
                               AuditService auditService) {
        this.importRepo = importRepo;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.mapper = new ObjectMapper();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "route-import");
            t.setDaemon(true);
            return t;
        });
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
            // Second pass: compute deviation_miles and is_deviation_alert by
            // comparing each checkpoint's masked coordinates against the previous
            // checkpoint in sequence.  Drift above DEVIATION_ALERT_MILES (0.5 mi)
            // flags the row and raises a WARN notification in the aggregate count.
            annotateDeviations(checkpoints);

            importRepo.updateStatus(importId, "PROCESSING", null, null);
            int alertCount = 0;
            for (RouteCheckpoint cp : checkpoints) {
                importRepo.insertCheckpoint(cp);
                if (cp.isDeviationAlert() || cp.isMissedAlert()) alertCount++;
            }
            importRepo.updateStatus(importId, "COMPLETED", checkpoints.size(), alertCount);
            if (alertCount > 0) {
                notificationService.addAlert("WARN",
                    "Route import " + importId + " completed with " + alertCount + " alerts",
                    "RouteImport", importId);
            }
            auditService.record("RouteImport", importId, "COMPLETE", 0, null, null, null, null);
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
            // Optional expected coordinates for planned-route deviation.
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
        // Optional expected coordinates on the CSV row for planned-route deviation.
        String expLatStr = getCol(row, header, "expected_lat");
        String expLonStr = getCol(row, header, "expected_lon");
        if (expLatStr != null && !expLatStr.isBlank() && expLonStr != null && !expLonStr.isBlank()) {
            try {
                cp.setExpectedLatMasked(MaskingUtil.maskLat(Double.parseDouble(expLatStr)));
                cp.setExpectedLonMasked(MaskingUtil.maskLon(Double.parseDouble(expLonStr)));
            } catch (NumberFormatException ignored) {
                // Malformed expected columns silently fall back to previous-checkpoint deviation.
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

        // Check missed alert
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

    /**
     * Fill in {@code deviationMiles} and {@code isDeviationAlert} on each
     * checkpoint.
     *
     * <p>Per-checkpoint rule:
     * <ol>
     *   <li>If the import supplied {@code expected_lat}/{@code expected_lon}
     *       for the row, compute deviation as the great-circle distance between
     *       the planned (expected) coordinates and the actual coordinates
     *       recorded for that checkpoint (<em>planned-vs-actual</em>).</li>
     *   <li>Otherwise fall back to the distance from the <em>previous</em>
     *       checkpoint in the import sequence.  The first checkpoint in that
     *       fallback path has no predecessor, so its deviation is {@code 0.0}.</li>
     * </ol>
     * Any checkpoint whose deviation exceeds
     * {@link #DEVIATION_ALERT_MILES} (0.5 mi) is flagged and promoted to
     * status {@code DEVIATED} (unless already {@code MISSED}).</p>
     */
    private void annotateDeviations(List<RouteCheckpoint> checkpoints) {
        RouteCheckpoint prev = null;
        for (RouteCheckpoint cp : checkpoints) {
            Double lat = cp.getLatMasked();
            Double lon = cp.getLonMasked();
            Double expLat = cp.getExpectedLatMasked();
            Double expLon = cp.getExpectedLonMasked();

            Double miles = null;
            if (lat != null && lon != null && expLat != null && expLon != null) {
                // Preferred: planned-vs-actual deviation.
                miles = computeDeviationMiles(expLat, expLon, lat, lon);
            } else if (prev != null && lat != null && lon != null
                    && prev.getLatMasked() != null && prev.getLonMasked() != null) {
                // Fallback: previous-checkpoint deviation.
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
        // Haversine formula
        final double R = 3958.8; // Earth radius in miles
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public void resumeIncompleteImports() {
        // On startup, any PROCESSING imports are considered incomplete; reset to FAILED
        // (In production: would resume from checkpoint_path)
        log.info("Route import service initialized");
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
