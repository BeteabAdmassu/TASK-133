package com.eaglepoint.console.service;

import com.eaglepoint.console.config.AppConfig;
import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.UpdateHistoryEntry;
import com.eaglepoint.console.model.UpdatePackage;
import com.eaglepoint.console.repository.UpdateHistoryRepository;
import com.eaglepoint.console.security.UpdateSignatureVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Concrete offline update workflow.
 *
 * <p>Layout on disk:
 * <pre>
 *   ${updater.dir}/incoming/       ← operators drop signed .msi packages here
 *       eaglepoint-1.2.0/
 *           manifest.json
 *           manifest.sig            (raw detached Ed25519 signature)
 *           payload.zip             (installer bundle — SHA-256 referenced in manifest)
 *   ${updater.dir}/installed/       ← currently-running version payloads
 *       eaglepoint-1.2.0/...
 *   ${updater.dir}/backups/         ← previous version payloads, used on rollback
 *       eaglepoint-1.1.0/...
 * </pre>
 *
 * <p>A full lifecycle is:
 * <ol>
 *   <li>{@link #listAvailablePackages()} — enumerate incoming/ and parse each manifest.</li>
 *   <li>{@link #verifyPackage(String)} — require the manifest.sig to match a
 *       trusted Ed25519 key AND the payload SHA-256 to match the manifest.</li>
 *   <li>{@link #applyPackage(String, long)} — atomically move the current
 *       installed copy into backups/ and promote the incoming payload into
 *       installed/.  Writes a SUCCESS {@code INSTALLED} history row.</li>
 *   <li>{@link #rollback(long)} — restore the most recent prior installed
 *       payload; writes a {@code ROLLED_BACK} history row.</li>
 * </ol>
 *
 * <p>Every state transition is audited through {@link AuditService} so the
 * compliance trail survives independent of {@code update_history} and every
 * failure surfaces as a NotificationService alert.</p>
 */
public class UpdateService {

    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);
    private static final String PACKAGE_ENTITY = "UpdatePackage";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path incomingDir;
    private final Path installedDir;
    private final Path backupsDir;
    private final UpdateSignatureVerifier verifier;
    private final UpdateHistoryRepository historyRepo;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public UpdateService(UpdateSignatureVerifier verifier,
                         UpdateHistoryRepository historyRepo,
                         AuditService auditService,
                         NotificationService notificationService) {
        this(defaultRoot(), verifier, historyRepo, auditService, notificationService);
    }

    public UpdateService(Path root,
                         UpdateSignatureVerifier verifier,
                         UpdateHistoryRepository historyRepo,
                         AuditService auditService,
                         NotificationService notificationService) {
        this.incomingDir  = root.resolve("incoming");
        this.installedDir = root.resolve("installed");
        this.backupsDir   = root.resolve("backups");
        this.verifier = verifier;
        this.historyRepo = historyRepo;
        this.auditService = auditService;
        this.notificationService = notificationService;
        try {
            Files.createDirectories(incomingDir);
            Files.createDirectories(installedDir);
            Files.createDirectories(backupsDir);
        } catch (IOException e) {
            log.warn("Could not create updater directories under {}: {}", root, e.getMessage());
        }
    }

    private static Path defaultRoot() {
        String configured = System.getProperty("updater.dir", System.getenv("UPDATER_DIR"));
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        String dbPath;
        try { dbPath = AppConfig.getInstance().getDbPath(); }
        catch (Exception e) { dbPath = "data/console.db"; }
        Path parent = Path.of(dbPath).toAbsolutePath().getParent();
        if (parent == null) parent = Path.of(".").toAbsolutePath();
        return parent.resolve("updater");
    }

    // ─── Discovery ───────────────────────────────────────────────────────────

    /** Enumerate every well-formed package directory under {@code incoming/}. */
    public List<UpdatePackage> listAvailablePackages() {
        List<UpdatePackage> out = new ArrayList<>();
        if (!Files.exists(incomingDir)) return out;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(incomingDir)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) continue;
                try {
                    out.add(readPackage(dir));
                } catch (Exception e) {
                    log.warn("Skipping invalid package {}: {}", dir.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to enumerate updater incoming dir {}: {}", incomingDir, e.getMessage());
        }
        out.sort(Comparator.comparing(UpdatePackage::getPackageName));
        return out;
    }

    public UpdatePackage getPackage(String packageName) {
        Path dir = incomingDir.resolve(sanitize(packageName));
        if (!Files.isDirectory(dir)) {
            throw new NotFoundException(PACKAGE_ENTITY, 0);
        }
        try {
            return readPackage(dir);
        } catch (IOException e) {
            throw new ValidationException("package",
                "Package " + packageName + " is malformed: " + e.getMessage());
        }
    }

    private UpdatePackage readPackage(Path dir) throws IOException {
        Path manifestFile = dir.resolve("manifest.json");
        Path sigFile = dir.resolve("manifest.sig");
        if (!Files.isRegularFile(manifestFile)) {
            throw new IOException("missing manifest.json");
        }
        byte[] manifestBytes = Files.readAllBytes(manifestFile);
        UpdatePackage.Manifest manifest = MAPPER.readValue(manifestBytes, UpdatePackage.Manifest.class);
        if (manifest.getVersion() == null || manifest.getVersion().isBlank()) {
            throw new IOException("manifest.version is required");
        }
        if (manifest.getPayloadFilename() == null || manifest.getPayloadSha256() == null) {
            throw new IOException("manifest.payloadFilename and payloadSha256 are required");
        }
        String manifestSha = DigestUtils.sha256Hex(manifestBytes);
        return new UpdatePackage(
            dir.getFileName().toString(),
            dir.toAbsolutePath().toString(),
            manifest,
            manifestSha,
            Files.isRegularFile(sigFile));
    }

    // ─── Verification ────────────────────────────────────────────────────────

    public UpdateSignatureVerifier.Result verifyPackage(String packageName) {
        UpdatePackage pkg = getPackage(packageName);
        Path dir = Path.of(pkg.getPackagePath());
        try {
            byte[] manifestBytes = Files.readAllBytes(dir.resolve("manifest.json"));
            Path sigFile = dir.resolve("manifest.sig");
            byte[] sig = Files.isRegularFile(sigFile) ? Files.readAllBytes(sigFile) : new byte[0];
            UpdateSignatureVerifier.Result result = verifier.verify(manifestBytes, sig);
            if (!result.isValid()) {
                recordHistory(pkg, null, "VERIFIED", "FAILED",
                    result.getStatus(), null, null, result.getReason(), null, null);
                return result;
            }
            // Additionally verify the payload hash matches the manifest claim.
            Path payload = dir.resolve(pkg.getManifest().getPayloadFilename());
            if (!Files.isRegularFile(payload)) {
                UpdateSignatureVerifier.Result miss = UpdateSignatureVerifier.Result.failure(
                    "INVALID", "payload file missing: " + pkg.getManifest().getPayloadFilename());
                recordHistory(pkg, null, "VERIFIED", "FAILED",
                    "INVALID", null, null, miss.getReason(), null, null);
                return miss;
            }
            String payloadHash;
            try (InputStream is = Files.newInputStream(payload)) {
                payloadHash = DigestUtils.sha256Hex(is);
            }
            if (!payloadHash.equalsIgnoreCase(pkg.getManifest().getPayloadSha256())) {
                UpdateSignatureVerifier.Result mismatch = UpdateSignatureVerifier.Result.failure(
                    "INVALID", "payload SHA-256 does not match manifest");
                recordHistory(pkg, payloadHash, "VERIFIED", "FAILED",
                    "INVALID", null, null, mismatch.getReason(), null, null);
                return mismatch;
            }
            recordHistory(pkg, payloadHash, "VERIFIED", "SUCCESS",
                "VALID", null, null, null, null, null);
            return result;
        } catch (IOException e) {
            UpdateSignatureVerifier.Result io = UpdateSignatureVerifier.Result.failure(
                "INVALID", "read error: " + e.getMessage());
            recordHistory(pkg, null, "VERIFIED", "FAILED",
                "INVALID", null, null, io.getReason(), null, null);
            return io;
        }
    }

    // ─── Apply ───────────────────────────────────────────────────────────────

    public UpdateHistoryEntry applyPackage(String packageName, long initiatedBy) {
        UpdatePackage pkg = getPackage(packageName);
        String fromVersion = currentVersion();

        UpdateSignatureVerifier.Result result = verifyPackage(packageName);
        if (!result.isValid()) {
            String msg = "Cannot apply package " + packageName + ": " + result.getReason();
            recordHistory(pkg, null, "FAILED", "FAILED",
                result.getStatus(), null, null, msg, initiatedBy, null);
            if (notificationService != null) {
                notificationService.addAlert("ERROR",
                    "Update " + packageName + " rejected: " + result.getReason(),
                    PACKAGE_ENTITY, null);
            }
            auditService.record(PACKAGE_ENTITY, 0, "UPDATE_REJECTED", initiatedBy,
                null, null, Map.of("package", packageName, "reason", result.getReason()), null);
            throw new ValidationException("signature", msg);
        }

        Path source = Path.of(pkg.getPackagePath());
        Path installTarget = installedDir.resolve(pkg.getPackageName());
        Path backupPath = null;
        try {
            // 1. If there's an existing installed payload, move it to backups/
            //    under the current version name so rollback can find it.
            Path current = latestInstalled();
            if (current != null) {
                String backupName = current.getFileName().toString() + "-"
                    + System.currentTimeMillis();
                backupPath = backupsDir.resolve(backupName);
                Files.move(current, backupPath, StandardCopyOption.ATOMIC_MOVE);
            }
            // 2. Promote the incoming package into installed/.
            copyTree(source, installTarget);

            UpdateHistoryEntry entry = recordHistory(pkg,
                pkg.getManifest().getPayloadSha256(),
                "INSTALLED", "SUCCESS", "VALID",
                installTarget.toString(),
                backupPath == null ? null : backupPath.toString(),
                null, initiatedBy, null);
            auditService.record(PACKAGE_ENTITY, entry.getId(), "UPDATE_APPLIED", initiatedBy,
                null, Map.of("fromVersion", fromVersion),
                Map.of("toVersion", pkg.getManifest().getVersion()), null);
            if (notificationService != null) {
                notificationService.addAlert("INFO",
                    "Update " + pkg.getManifest().getVersion() + " applied successfully",
                    PACKAGE_ENTITY, entry.getId());
            }
            return entry;
        } catch (Exception e) {
            log.error("Apply of package {} failed: {}", packageName, e.getMessage(), e);
            // Best-effort recovery: if we managed to move the current payload
            // into backups/ but the new copy failed, restore it.
            try {
                if (backupPath != null && Files.exists(backupPath) && !Files.exists(installTarget)) {
                    Files.move(backupPath, installTarget, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (IOException ignored) {}
            UpdateHistoryEntry failed = recordHistory(pkg, null, "FAILED", "FAILED",
                "VALID", null, backupPath == null ? null : backupPath.toString(),
                e.getMessage(), initiatedBy, null);
            if (notificationService != null) {
                notificationService.addAlert("ERROR",
                    "Update " + pkg.getManifest().getVersion() + " failed: " + e.getMessage(),
                    PACKAGE_ENTITY, failed.getId());
            }
            auditService.record(PACKAGE_ENTITY, failed.getId(), "UPDATE_FAILED", initiatedBy,
                null, null, Map.of("package", packageName, "reason", e.getMessage()), null);
            throw new ValidationException("apply", "Update apply failed: " + e.getMessage());
        }
    }

    // ─── Rollback ────────────────────────────────────────────────────────────

    /** One-click rollback: restore the most recent SUCCESS INSTALLED backup. */
    public UpdateHistoryEntry rollback(long initiatedBy) {
        UpdateHistoryEntry current = historyRepo.findCurrentInstalled()
            .orElseThrow(() -> new ConflictException("No installed update to roll back from"));
        UpdateHistoryEntry previous = historyRepo.findPreviousInstalledBefore(current.getId())
            .orElseThrow(() -> new ConflictException("No previous version on record to roll back to"));
        if (previous.getBackupPath() == null) {
            // The current row is actually what saved the previous payload in its backup_path.
            if (current.getBackupPath() == null) {
                throw new ConflictException("Previous payload backup is missing on disk");
            }
            previous.setBackupPath(current.getBackupPath());
        }
        Path backup = Path.of(previous.getBackupPath() == null ? current.getBackupPath() : previous.getBackupPath());
        Path currentInstalled = latestInstalled();

        try {
            if (!Files.exists(backup)) {
                throw new ConflictException("Rollback backup path no longer exists: " + backup);
            }
            if (currentInstalled != null) {
                // Keep the just-removed install in a rollback-forward slot.
                Path supersededBackup = backupsDir.resolve(
                    currentInstalled.getFileName().toString() + "-rollback-" + System.currentTimeMillis());
                Files.move(currentInstalled, supersededBackup, StandardCopyOption.ATOMIC_MOVE);
            }
            Path restoredTarget = installedDir.resolve(backup.getFileName().toString());
            Files.move(backup, restoredTarget, StandardCopyOption.ATOMIC_MOVE);

            UpdateHistoryEntry entry = new UpdateHistoryEntry();
            entry.setPackageName(previous.getPackageName());
            entry.setFromVersion(current.getToVersion());
            entry.setToVersion(previous.getToVersion());
            entry.setAction("ROLLED_BACK");
            entry.setStatus("SUCCESS");
            entry.setSignatureStatus(previous.getSignatureStatus());
            entry.setInstalledPath(restoredTarget.toString());
            entry.setInitiatedBy(initiatedBy);
            entry.setNotes("one-click rollback");
            long id = historyRepo.insert(entry);
            entry.setId(id);

            auditService.record(PACKAGE_ENTITY, id, "UPDATE_ROLLBACK", initiatedBy,
                null, Map.of("fromVersion", current.getToVersion()),
                Map.of("toVersion", previous.getToVersion()), null);
            if (notificationService != null) {
                notificationService.addAlert("WARN",
                    "Rolled back from " + current.getToVersion() + " to " + previous.getToVersion(),
                    PACKAGE_ENTITY, id);
            }
            return entry;
        } catch (IOException | ConflictException e) {
            log.error("Rollback failed: {}", e.getMessage(), e);
            UpdateHistoryEntry entry = new UpdateHistoryEntry();
            entry.setPackageName(previous.getPackageName());
            entry.setFromVersion(current.getToVersion());
            entry.setToVersion(previous.getToVersion());
            entry.setAction("ROLLED_BACK");
            entry.setStatus("FAILED");
            entry.setErrorMessage(e.getMessage());
            entry.setInitiatedBy(initiatedBy);
            historyRepo.insert(entry);
            if (notificationService != null) {
                notificationService.addAlert("ERROR",
                    "Rollback failed: " + e.getMessage(),
                    PACKAGE_ENTITY, null);
            }
            if (e instanceof ConflictException ce) throw ce;
            throw new ValidationException("rollback", "Rollback failed: " + e.getMessage());
        }
    }

    public List<UpdateHistoryEntry> history(int limit) {
        if (limit < 1) limit = 20;
        if (limit > 200) limit = 200;
        return historyRepo.findAll(limit);
    }

    public Optional<UpdateHistoryEntry> currentInstalled() {
        return historyRepo.findCurrentInstalled();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private String currentVersion() {
        return historyRepo.findCurrentInstalled()
            .map(UpdateHistoryEntry::getToVersion)
            .orElse(AppConfig.getInstance().getVersion());
    }

    private Path latestInstalled() {
        if (!Files.exists(installedDir)) return null;
        try (DirectoryStream<Path> s = Files.newDirectoryStream(installedDir)) {
            Path best = null;
            long bestMillis = Long.MIN_VALUE;
            for (Path p : s) {
                if (!Files.isDirectory(p)) continue;
                long m = Files.readAttributes(p, BasicFileAttributes.class).lastModifiedTime().toMillis();
                if (m > bestMillis) { bestMillis = m; best = p; }
            }
            return best;
        } catch (IOException e) {
            return null;
        }
    }

    private UpdateHistoryEntry recordHistory(UpdatePackage pkg, String sha, String action, String status,
                                              String signatureStatus,
                                              String installedPath, String backupPath,
                                              String error, Long initiatedBy, String notes) {
        UpdateHistoryEntry e = new UpdateHistoryEntry();
        e.setPackageName(pkg.getPackageName());
        e.setFromVersion(currentVersion());
        e.setToVersion(pkg.getManifest().getVersion());
        e.setAction(action);
        e.setStatus(status);
        e.setSha256Hash(sha);
        e.setSignatureStatus(signatureStatus);
        e.setInstalledPath(installedPath);
        e.setBackupPath(backupPath);
        e.setErrorMessage(error);
        e.setInitiatedBy(initiatedBy);
        e.setNotes(notes);
        long id = historyRepo.insert(e);
        e.setId(id);
        return e;
    }

    private void copyTree(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, dst.resolve(src.relativize(file).toString()),
                    StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private String sanitize(String packageName) {
        if (packageName == null || packageName.contains("..") || packageName.contains("/") || packageName.contains("\\")) {
            throw new ValidationException("packageName", "Invalid package name");
        }
        return packageName;
    }

    // exposed for tests
    public Path incomingDir() { return incomingDir; }
    public Path installedDir() { return installedDir; }
    public Path backupsDir() { return backupsDir; }
}
