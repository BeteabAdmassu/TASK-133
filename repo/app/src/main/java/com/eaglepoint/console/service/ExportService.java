package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.ForbiddenException;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.export.CsvExporter;
import com.eaglepoint.console.export.ExcelExporter;
import com.eaglepoint.console.export.FingerprintUtil;
import com.eaglepoint.console.export.PdfExporter;
import com.eaglepoint.console.model.*;
import com.eaglepoint.console.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Async export service.
 *
 * <p>Builds a row set for the requested {@code entityType} from the real
 * repositories and writes it to disk as CSV / Excel / PDF.  Sensitive fields
 * (encrypted staff ids, resident ids, pickup addresses, password hashes) are
 * <strong>always masked</strong> in the output — callers do not control this.
 * The resulting artefact is written alongside a SHA-256 sidecar for
 * tamper-evident archival.</p>
 */
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    /** Hard cap per export — keeps the SQLite pool-of-1 scan bounded. */
    private static final int MAX_ROWS = 10_000;
    private static final String MASKED = "[MASKED]";

    private final ExportJobRepository exportRepo;
    private final CommunityRepository communityRepo;
    private final ServiceAreaRepository serviceAreaRepo;
    private final PickupPointRepository pickupPointRepo;
    private final BedRepository bedRepo;
    private final BedBuildingRepository buildingRepo;
    private final BedRoomRepository roomRepo;
    private final UserRepository userRepo;
    private final KpiRepository kpiRepo;
    private final GeozoneRepository geozoneRepo;
    private final ExecutorService executor;

    public ExportService(ExportJobRepository exportRepo,
                         CommunityRepository communityRepo,
                         ServiceAreaRepository serviceAreaRepo,
                         PickupPointRepository pickupPointRepo,
                         BedRepository bedRepo,
                         BedBuildingRepository buildingRepo,
                         BedRoomRepository roomRepo,
                         UserRepository userRepo,
                         KpiRepository kpiRepo,
                         GeozoneRepository geozoneRepo) {
        this.exportRepo = exportRepo;
        this.communityRepo = communityRepo;
        this.serviceAreaRepo = serviceAreaRepo;
        this.pickupPointRepo = pickupPointRepo;
        this.bedRepo = bedRepo;
        this.buildingRepo = buildingRepo;
        this.roomRepo = roomRepo;
        this.userRepo = userRepo;
        this.kpiRepo = kpiRepo;
        this.geozoneRepo = geozoneRepo;
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
        job.setEntityType(entityType != null ? entityType : "COMMUNITIES");
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

            List<Map<String, Object>> rows = buildRows(job.getEntityType());
            List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());

            switch (job.getType()) {
                case "EXCEL" -> new ExcelExporter().write(rows, columns, outputPath);
                case "PDF" -> new PdfExporter().write(rows, columns, job.getEntityType() + " Export", outputPath);
                case "CSV" -> new CsvExporter().write(rows, columns, outputPath);
            }

            String sha256 = FingerprintUtil.writeSha256Sidecar(outputPath);
            exportRepo.updateStatus(jobId, "COMPLETED", outputPath.toString(), sha256, null);
            log.info("Export job {} completed: {} ({} rows)", jobId, outputPath, rows.size());
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

    // ─── Entity-driven row builders ──────────────────────────────────────────

    /**
     * Dispatch to the right row builder for this entityType.  Unknown types
     * throw so the job is marked FAILED instead of silently emitting garbage.
     */
    private List<Map<String, Object>> buildRows(String entityType) {
        String type = entityType == null ? "" : entityType.toUpperCase();
        return switch (type) {
            case "COMMUNITIES"      -> communityRows();
            case "SERVICE_AREAS"    -> serviceAreaRows();
            case "PICKUP_POINTS"    -> pickupPointRows();
            case "BEDS"             -> bedRows();
            case "BED_BUILDINGS"    -> buildingRows();
            case "ROOMS"            -> roomRows();
            case "USERS"            -> userRows();
            case "KPIS"             -> kpiRows();
            case "KPI_SCORES"       -> kpiScoreRows();
            case "GEOZONES"         -> geozoneRows();
            default -> throw new com.eaglepoint.console.exception.ValidationException(
                "entityType", "Unsupported export entityType: " + entityType);
        };
    }

    private List<Map<String, Object>> communityRows() {
        return collect(page -> communityRepo.findAll(page, 500).getData(), c -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", c.getId());
            r.put("name", c.getName());
            r.put("description", c.getDescription());
            r.put("status", c.getStatus());
            r.put("createdAt", c.getCreatedAt());
            return r;
        });
    }

    private List<Map<String, Object>> serviceAreaRows() {
        return collect(page -> serviceAreaRepo.findAll(page, 500).getData(), sa -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", sa.getId());
            r.put("communityId", sa.getCommunityId());
            r.put("name", sa.getName());
            r.put("description", sa.getDescription());
            r.put("status", sa.getStatus());
            r.put("createdAt", sa.getCreatedAt());
            return r;
        });
    }

    private List<Map<String, Object>> pickupPointRows() {
        return collect(page -> pickupPointRepo.findAll(page, 500).getData(), pp -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", pp.getId());
            r.put("communityId", pp.getCommunityId());
            r.put("address", MASKED); // address is AES-GCM encrypted; always masked in exports
            r.put("zipCode", pp.getZipCode());
            r.put("streetRangeStart", pp.getStreetRangeStart());
            r.put("streetRangeEnd", pp.getStreetRangeEnd());
            r.put("capacity", pp.getCapacity());
            r.put("status", pp.getStatus());
            r.put("geozoneId", pp.getGeozoneId());
            return r;
        });
    }

    private List<Map<String, Object>> bedRows() {
        return collect(page -> bedRepo.findAllBeds(page, 500, null).getData(), b -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", b.getId());
            r.put("roomId", b.getRoomId());
            r.put("bedLabel", b.getBedLabel());
            r.put("state", b.getState());
            // residentId is AES-GCM encrypted PII; masked in exports
            r.put("residentId", b.getResidentIdEncrypted() == null ? null : MASKED);
            r.put("updatedAt", b.getUpdatedAt());
            return r;
        });
    }

    private List<Map<String, Object>> buildingRows() {
        return collect(page -> buildingRepo.findAll(page, 500).getData(), bb -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", bb.getId());
            r.put("name", bb.getName());
            r.put("address", bb.getAddress());
            r.put("serviceAreaId", bb.getServiceAreaId());
            return r;
        });
    }

    private List<Map<String, Object>> roomRows() {
        return collect(page -> roomRepo.findAll(null, page, 500).getData(), rm -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", rm.getId());
            r.put("buildingId", rm.getBuildingId());
            r.put("roomNumber", rm.getRoomNumber());
            r.put("floor", rm.getFloor());
            r.put("roomType", rm.getRoomType());
            return r;
        });
    }

    private List<Map<String, Object>> userRows() {
        return collect(page -> userRepo.findAll(page, 500).getData(), u -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", u.getId());
            r.put("username", u.getUsername());
            r.put("displayName", u.getDisplayName());
            r.put("role", u.getRole());
            r.put("isActive", u.isActive());
            r.put("lastLogin", u.getLastLogin());
            // passwordHash and staffIdEncrypted are never included.
            r.put("staffId", MASKED);
            return r;
        });
    }

    private List<Map<String, Object>> kpiRows() {
        return collect(page -> kpiRepo.findAllKpis(page, 500).getData(), k -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", k.getId());
            r.put("name", k.getName());
            r.put("unit", k.getUnit());
            r.put("category", k.getCategory());
            r.put("formula", k.getFormula());
            r.put("isActive", k.isActive());
            return r;
        });
    }

    private List<Map<String, Object>> kpiScoreRows() {
        return collect(page -> kpiRepo.findScores(null, null, null, null, page, 500).getData(), s -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", s.getId());
            r.put("kpiId", s.getKpiId());
            r.put("serviceAreaId", s.getServiceAreaId());
            r.put("value", s.getValue());
            r.put("scoreDate", s.getScoreDate());
            r.put("computedBy", s.getComputedBy());
            return r;
        });
    }

    private List<Map<String, Object>> geozoneRows() {
        return collect(page -> geozoneRepo.findAll(page, 500).getData(), g -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", g.getId());
            r.put("name", g.getName());
            r.put("zipCodes", g.getZipCodes());
            r.put("streetRanges", g.getStreetRanges());
            return r;
        });
    }

    /** Page-walking helper that stops at an empty page or the MAX_ROWS cap. */
    private <T> List<Map<String, Object>> collect(Function<Integer, List<T>> pageFetcher,
                                                   Function<T, Map<String, Object>> mapper) {
        List<Map<String, Object>> out = new ArrayList<>();
        int page = 1;
        while (out.size() < MAX_ROWS) {
            List<T> data = pageFetcher.apply(page);
            if (data == null || data.isEmpty()) break;
            for (T item : data) {
                out.add(mapper.apply(item));
                if (out.size() >= MAX_ROWS) break;
            }
            if (data.size() < 500) break; // last page
            page++;
        }
        return out;
    }
}
