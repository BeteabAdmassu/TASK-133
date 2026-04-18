package com.eaglepoint.console.service;

import com.eaglepoint.console.config.AppConfig;
import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.UpdateHistoryEntry;
import com.eaglepoint.console.model.UpdatePackage;
import com.eaglepoint.console.repository.UpdateHistoryRepository;
import com.eaglepoint.console.security.UpdateSignatureVerifier;
import com.eaglepoint.console.service.updater.InstallerArgValidator;
import com.eaglepoint.console.service.updater.InstallerExecutor;
import com.eaglepoint.console.service.updater.InstallerExecutorFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Offline update workflow for signed Windows Installer ({@code .msi})
 * packages with one-click rollback.
 *
 * <p>Lifecycle of a package drop:
 * <ol>
 *   <li><strong>Discovery.</strong>
 *       {@link #listAvailablePackages()} scans
 *       {@code ${updater.dir}/incoming/} and parses each {@code manifest.json}.
 *   </li>
 *   <li><strong>Verification.</strong> {@link #verifyPackage(String)}
 *       runs the Ed25519 manifest signature check and the payload /
 *       installer SHA-256 check.  No filesystem promotion occurs during
 *       verification.</li>
 *   <li><strong>Apply.</strong> {@link #applyPackage(String, long)}
 *       verifies, archives the current {@code installed/} folder into
 *       {@code backups/}, promotes the new package into {@code installed/},
 *       and — for {@code installerType=MSI} packages — launches
 *       {@code msiexec /i} through {@link InstallerExecutor}.  The exit
 *       code, log path, and executor mode are persisted on the history
 *       row.</li>
 *   <li><strong>Rollback.</strong> {@link #rollback(long)} performs
 *       "one-click rollback": it looks up the prior INSTALLED history
 *       row, runs {@code msiexec /x &lt;currentProductCode&gt;} to
 *       uninstall the current release, then {@code msiexec /i} against
 *       the previous MSI and finally restores the filesystem
 *       {@code installed/} directory.</li>
 * </ol>
 *
 * <p>Every state transition writes a row to {@code update_history} and
 * emits an audit event (entityType {@code UpdatePackage}).  Signature
 * bytes and installer command lines are never logged verbatim.</p>
 */
public class UpdateService {

    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);
    private static final String PACKAGE_ENTITY = "UpdatePackage";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** {@code msiexec} argument + any properties the manifest declared. */
    public enum FailureReason {
        SIGNATURE_INVALID,
        SIGNATURE_UNTRUSTED,
        MANIFEST_INVALID,
        PAYLOAD_HASH_MISMATCH,
        INSTALLER_EXECUTION_FAILED,
        ROLLBACK_TARGET_MISSING,
        UNEXPECTED
    }

    private final Path incomingDir;
    private final Path installedDir;
    private final Path backupsDir;
    private final Path installerLogDir;
    private final UpdateSignatureVerifier verifier;
    private final UpdateHistoryRepository historyRepo;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final InstallerExecutor installer;
    private final Duration installerTimeout;
    /**
     * When {@code false} (production default), apply rejects any package
     * whose {@code installerType} is not {@code MSI}.  The legacy
     * payload-only path is only available when the operator explicitly
     * opts in via {@code -Dupdater.allow.non.msi=true} or
     * {@code UPDATER_ALLOW_NON_MSI=true}.
     */
    private final boolean allowNonMsi;

    public UpdateService(UpdateSignatureVerifier verifier,
                         UpdateHistoryRepository historyRepo,
                         AuditService auditService,
                         NotificationService notificationService) {
        this(defaultRoot(), verifier, historyRepo, auditService, notificationService,
            InstallerExecutorFactory.resolve(), Duration.ofMinutes(15), resolveAllowNonMsi());
    }

    /** Backwards-compatible overload — installer picked via factory, strict-MSI from env/sysprop. */
    public UpdateService(Path root,
                         UpdateSignatureVerifier verifier,
                         UpdateHistoryRepository historyRepo,
                         AuditService auditService,
                         NotificationService notificationService) {
        this(root, verifier, historyRepo, auditService, notificationService,
            InstallerExecutorFactory.resolve(), Duration.ofMinutes(15), resolveAllowNonMsi());
    }

    public UpdateService(Path root,
                         UpdateSignatureVerifier verifier,
                         UpdateHistoryRepository historyRepo,
                         AuditService auditService,
                         NotificationService notificationService,
                         InstallerExecutor installer) {
        this(root, verifier, historyRepo, auditService, notificationService,
            installer, Duration.ofMinutes(15), resolveAllowNonMsi());
    }

    public UpdateService(Path root,
                         UpdateSignatureVerifier verifier,
                         UpdateHistoryRepository historyRepo,
                         AuditService auditService,
                         NotificationService notificationService,
                         InstallerExecutor installer,
                         Duration installerTimeout) {
        this(root, verifier, historyRepo, auditService, notificationService,
            installer, installerTimeout, resolveAllowNonMsi());
    }

    public UpdateService(Path root,
                         UpdateSignatureVerifier verifier,
                         UpdateHistoryRepository historyRepo,
                         AuditService auditService,
                         NotificationService notificationService,
                         InstallerExecutor installer,
                         Duration installerTimeout,
                         boolean allowNonMsi) {
        this.incomingDir  = root.resolve("incoming");
        this.installedDir = root.resolve("installed");
        this.backupsDir   = root.resolve("backups");
        this.installerLogDir = root.resolve("logs");
        this.verifier = verifier;
        this.historyRepo = historyRepo;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.installer = installer;
        this.installerTimeout = installerTimeout;
        this.allowNonMsi = allowNonMsi;
        if (allowNonMsi) {
            log.warn("UpdateService is running with allowNonMsi=true — NON-MSI packages will be accepted. "
                + "This override must only be used for dev/CI, not production.");
        }
        try {
            Files.createDirectories(incomingDir);
            Files.createDirectories(installedDir);
            Files.createDirectories(backupsDir);
            Files.createDirectories(installerLogDir);
        } catch (IOException e) {
            log.warn("Could not create updater directories under {}: {}", root, e.getMessage());
        }
    }

    /** Recovery-state constants persisted on failed apply rows. */
    public static final String RECOVERY_AUTO_REVERTED = "AUTO_REVERTED";
    public static final String RECOVERY_NEEDS_MANUAL  = "NEEDS_MANUAL_RECOVERY";

    /**
     * Reads {@code updater.allow.non.msi} sysprop (then
     * {@code UPDATER_ALLOW_NON_MSI} env var).  Default is {@code false}
     * so a production container is strict-MSI out-of-the-box.
     */
    static boolean resolveAllowNonMsi() {
        String prop = System.getProperty("updater.allow.non.msi");
        if (prop == null || prop.isBlank()) prop = System.getenv("UPDATER_ALLOW_NON_MSI");
        return "true".equalsIgnoreCase(prop == null ? "" : prop.trim());
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
        // A manifest must reference either a legacy payload or an installer file
        // — it's the one piece that gets SHA-256 checked during verification.
        String referenced = referencedBinary(manifest);
        if (referenced == null) {
            throw new IOException("manifest must declare payloadFilename or installerFile");
        }
        if (manifest.getPayloadSha256() == null) {
            throw new IOException("manifest.payloadSha256 is required");
        }
        String manifestSha = DigestUtils.sha256Hex(manifestBytes);
        return new UpdatePackage(
            dir.getFileName().toString(),
            dir.toAbsolutePath().toString(),
            manifest,
            manifestSha,
            Files.isRegularFile(sigFile));
    }

    private static String referencedBinary(UpdatePackage.Manifest m) {
        if (m.getInstallerFile() != null && !m.getInstallerFile().isBlank()) {
            return m.getInstallerFile();
        }
        if (m.getPayloadFilename() != null && !m.getPayloadFilename().isBlank()) {
            return m.getPayloadFilename();
        }
        return null;
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
                    result.getStatus(), null, null, result.getReason(), null, null, null, null);
                return result;
            }
            // Verify hash of the real binary (installerFile OR payloadFilename).
            String binary = referencedBinary(pkg.getManifest());
            Path target = dir.resolve(binary);
            if (!Files.isRegularFile(target)) {
                UpdateSignatureVerifier.Result miss = UpdateSignatureVerifier.Result.failure(
                    "INVALID", "referenced binary missing: " + binary);
                recordHistory(pkg, null, "VERIFIED", "FAILED",
                    "INVALID", null, null, miss.getReason(), null, null, null, null);
                return miss;
            }
            String targetHash;
            try (InputStream is = Files.newInputStream(target)) {
                targetHash = DigestUtils.sha256Hex(is);
            }
            if (!targetHash.equalsIgnoreCase(pkg.getManifest().getPayloadSha256())) {
                UpdateSignatureVerifier.Result mismatch = UpdateSignatureVerifier.Result.failure(
                    "INVALID", "binary SHA-256 does not match manifest.payloadSha256");
                recordHistory(pkg, targetHash, "VERIFIED", "FAILED",
                    "INVALID", null, null, mismatch.getReason(), null, null, null, null);
                return mismatch;
            }
            recordHistory(pkg, targetHash, "VERIFIED", "SUCCESS",
                "VALID", null, null, null, null, null,
                null, pkg.getManifest().getInstallerType());
            return result;
        } catch (IOException e) {
            UpdateSignatureVerifier.Result io = UpdateSignatureVerifier.Result.failure(
                "INVALID", "read error: " + e.getMessage());
            recordHistory(pkg, null, "VERIFIED", "FAILED",
                "INVALID", null, null, io.getReason(), null, null, null, null);
            return io;
        }
    }

    // ─── Apply ───────────────────────────────────────────────────────────────

    public UpdateHistoryEntry applyPackage(String packageName, long initiatedBy) {
        UpdatePackage pkg = getPackage(packageName);
        String fromVersion = currentVersion();

        // 1. Signature + hash — the manifest must be cryptographically trusted
        //    BEFORE any installer command is launched.
        UpdateSignatureVerifier.Result result = verifyPackage(packageName);
        if (!result.isValid()) {
            FailureReason reason = "UNTRUSTED".equals(result.getStatus())
                ? FailureReason.SIGNATURE_UNTRUSTED : FailureReason.SIGNATURE_INVALID;
            rejectApply(pkg, initiatedBy, reason, result.getStatus(), result.getReason());
            throw new ValidationException("signature",
                reason.name() + ": " + result.getReason());
        }

        // 2. Manifest is cryptographically valid — now validate installer
        //    semantics.  Production apply is strict-MSI: a missing or
        //    non-MSI installerType is rejected with MANIFEST_INVALID unless
        //    the operator has explicitly opted in to the legacy path via
        //    updater.allow.non.msi / UPDATER_ALLOW_NON_MSI.  That override
        //    is intended for CI / dev only and is NEVER set in production
        //    containers.
        UpdatePackage.Manifest manifest = pkg.getManifest();
        String installerType = manifest.getInstallerType() == null ? "NONE" : manifest.getInstallerType();
        List<String> properties;
        try {
            if ("MSI".equals(installerType)) {
                if (manifest.getInstallerFile() == null || manifest.getInstallerFile().isBlank()) {
                    throw new ValidationException("installerFile",
                        "installerType=MSI requires installerFile");
                }
                if (manifest.getProductCode() != null) {
                    InstallerArgValidator.validateProductCode(manifest.getProductCode());
                }
            } else if ("NONE".equals(installerType)) {
                if (!allowNonMsi) {
                    throw new ValidationException("installerType",
                        "Production apply requires installerType=MSI; got NONE. "
                            + "Set updater.allow.non.msi=true only for dev/CI.");
                }
            } else {
                // Anything else (EXE, legacy strings, typos) is a hard reject
                // — strict-MSI means the wire protocol is closed.
                throw new ValidationException("installerType",
                    "Unsupported installerType '" + installerType + "' (allowed: MSI)");
            }
            properties = InstallerArgValidator.sanitize(manifest.getInstallArgs());
        } catch (ValidationException ve) {
            rejectApply(pkg, initiatedBy, FailureReason.MANIFEST_INVALID, "VALID", ve.getMessage());
            throw ve;
        }

        // 3. Promote the package directory into installed/ so rollback can
        //    find the MSI on disk later.  Move the current installed tree
        //    into backups/ first.
        Path source = Path.of(pkg.getPackagePath());
        Path installTarget = installedDir.resolve(pkg.getPackageName());
        Path backupPath = null;
        try {
            Path current = latestInstalled();
            if (current != null) {
                String backupName = current.getFileName().toString() + "-" + System.currentTimeMillis();
                backupPath = backupsDir.resolve(backupName);
                Files.move(current, backupPath, StandardCopyOption.ATOMIC_MOVE);
            }
            copyTree(source, installTarget);
        } catch (Exception e) {
            log.error("Filesystem promotion of package {} failed: {}", packageName, e.getMessage(), e);
            // Best-effort rollback of the backup move so we don't lose the
            // prior install just because the new copy failed.
            try {
                if (backupPath != null && Files.exists(backupPath) && !Files.exists(installTarget)) {
                    Files.move(backupPath, installTarget, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (IOException ignored) {}
            UpdateHistoryEntry failed = recordHistory(pkg, null, "FAILED", "FAILED",
                "VALID", null, backupPath == null ? null : backupPath.toString(),
                "filesystem promotion failed: " + e.getMessage(),
                initiatedBy, "fromVersion=" + fromVersion, null, installerType);
            if (notificationService != null) {
                notificationService.addAlert("ERROR",
                    "Update " + manifest.getVersion() + " failed to promote files: " + e.getMessage(),
                    PACKAGE_ENTITY, failed.getId());
            }
            auditService.record(PACKAGE_ENTITY, failed.getId(), "UPDATE_FAILED", initiatedBy,
                null, null, Map.of("package", packageName,
                    "reason", FailureReason.UNEXPECTED.name(), "detail", e.getMessage()), null);
            throw new ValidationException("apply", "Update apply failed: " + e.getMessage());
        }

        // 4. For MSI packages, actually run msiexec /i against the installer
        //    inside the promoted installed/ directory.
        Path logFile = null;
        Integer exitCode = null;
        if ("MSI".equals(installerType)) {
            Path msi = installTarget.resolve(manifest.getInstallerFile());
            logFile = installerLogPath(pkg, "install");
            InstallerExecutor.Result run = installer.install(msi, properties, logFile, installerTimeout);
            exitCode = run.getExitCode();
            if (!run.isSuccess()) {
                String detail = "installer exit=" + run.getExitCode()
                    + ", mode=" + installer.mode() + ", log=" + logFile;
                log.error("Installer failed for {}: {}", packageName, detail);
                // AUTO-REVERT: the filesystem was already promoted to the new
                // package before msiexec ran.  A failed installer leaves that
                // tree half-installed, so we actively delete it and move the
                // prior backup back into installed/ so the machine is not left
                // in limbo.  We persist the recoveryState so operators can
                // audit what happened without having to walk the filesystem.
                String recoveryState = attemptAutoRevert(installTarget, backupPath);

                UpdateHistoryEntry failed = recordHistory(pkg,
                    manifest.getPayloadSha256(),
                    "FAILED", "FAILED", "VALID",
                    null, backupPath == null ? null : backupPath.toString(),
                    FailureReason.INSTALLER_EXECUTION_FAILED.name() + ": " + run.getSummary(),
                    initiatedBy, "fromVersion=" + fromVersion,
                    exitCode, installerType, logFile.toString(), recoveryState);
                if (notificationService != null) {
                    String severity = RECOVERY_NEEDS_MANUAL.equals(recoveryState) ? "ERROR" : "WARN";
                    String msg = "MSI install failed (exit " + run.getExitCode() + ") for "
                        + manifest.getVersion()
                        + " — recoveryState=" + recoveryState;
                    notificationService.addAlert(severity, msg, PACKAGE_ENTITY, failed.getId());
                }
                auditService.record(PACKAGE_ENTITY, failed.getId(), "UPDATE_FAILED", initiatedBy,
                    null, null,
                    Map.of("package", packageName,
                        "reason", FailureReason.INSTALLER_EXECUTION_FAILED.name(),
                        "exitCode", run.getExitCode(),
                        "installerMode", installer.mode(),
                        "logPath", logFile.toString(),
                        "recoveryState", recoveryState),
                    null);
                throw new ValidationException("install",
                    FailureReason.INSTALLER_EXECUTION_FAILED.name()
                        + ": exit=" + run.getExitCode()
                        + " recoveryState=" + recoveryState);
            }
        }

        // 5. Success — write the INSTALLED history row and emit audit/alert.
        UpdateHistoryEntry entry = recordHistory(pkg,
            manifest.getPayloadSha256(),
            "INSTALLED", "SUCCESS", "VALID",
            installTarget.toString(),
            backupPath == null ? null : backupPath.toString(),
            null, initiatedBy, "fromVersion=" + fromVersion,
            exitCode, installerType,
            logFile == null ? null : logFile.toString());
        auditService.record(PACKAGE_ENTITY, entry.getId(), "UPDATE_APPLIED", initiatedBy,
            null, Map.of("fromVersion", fromVersion),
            Map.of("toVersion", manifest.getVersion(),
                "installerType", installerType,
                "installerMode", installer.mode(),
                "exitCode", exitCode == null ? "n/a" : exitCode.toString()),
            null);
        if (notificationService != null) {
            notificationService.addAlert("INFO",
                "Update " + manifest.getVersion() + " applied ("
                    + installerType + " via " + installer.mode() + ")",
                PACKAGE_ENTITY, entry.getId());
        }
        return entry;
    }

    private void rejectApply(UpdatePackage pkg, long initiatedBy, FailureReason reason,
                              String signatureStatus, String detail) {
        UpdateHistoryEntry rejected = recordHistory(pkg, null, "FAILED", "FAILED",
            signatureStatus, null, null,
            reason.name() + ": " + detail, initiatedBy, null, null,
            pkg.getManifest().getInstallerType());
        if (notificationService != null) {
            notificationService.addAlert("ERROR",
                "Update " + pkg.getPackageName() + " rejected: " + reason.name(),
                PACKAGE_ENTITY, rejected.getId());
        }
        auditService.record(PACKAGE_ENTITY, rejected.getId(), "UPDATE_REJECTED", initiatedBy,
            null, null,
            Map.of("package", pkg.getPackageName(),
                "reason", reason.name(),
                "detail", detail),
            null);
    }

    // ─── Rollback ────────────────────────────────────────────────────────────

    /**
     * One-click rollback.  The flow is:
     * <ol>
     *   <li>Find the latest INSTALLED row and the prior INSTALLED row in
     *       {@code update_history}.  409 if either is missing.</li>
     *   <li>If the current row was an MSI install with a
     *       {@code productCode}, run {@code msiexec /x &lt;currentProductCode&gt;}.</li>
     *   <li>Restore the prior {@code installed/} directory from
     *       {@code backups/}.</li>
     *   <li>If the prior row was also an MSI install and its installer
     *       file is still present in the restored directory, run
     *       {@code msiexec /i &lt;priorMsi&gt;} so the rollback leaves a
     *       proper Windows Installer state.</li>
     *   <li>Write a ROLLED_BACK row with exit codes + log paths.</li>
     * </ol>
     */
    public UpdateHistoryEntry rollback(long initiatedBy) {
        UpdateHistoryEntry current = historyRepo.findCurrentInstalled()
            .orElseThrow(() -> new ConflictException("No installed update to roll back from"));
        UpdateHistoryEntry previous = historyRepo.findPreviousInstalledBefore(current.getId())
            .orElseThrow(() -> new ConflictException("No previous version on record to roll back to"));

        // Locate the backup folder that holds the previous version's files.
        Path backup = resolveRollbackBackupPath(current, previous);
        if (backup == null || !Files.exists(backup)) {
            ConflictException ce = new ConflictException(
                "Rollback backup path no longer exists: " + backup);
            recordRollbackFailure(current, previous, initiatedBy,
                FailureReason.ROLLBACK_TARGET_MISSING, ce.getMessage(),
                null, null, null);
            throw ce;
        }

        // Load the manifest for the current install so we can find its
        // productCode — required for msiexec /x.
        UpdatePackage.Manifest currentManifest = readInstalledManifest(current.getInstalledPath());

        Path uninstallLog = null;
        Integer uninstallExit = null;
        String installerType = current.getInstallerType() != null ? current.getInstallerType()
            : (currentManifest != null && currentManifest.getInstallerType() != null
                ? currentManifest.getInstallerType() : "NONE");

        // Step 1: uninstall current MSI if applicable.
        if ("MSI".equals(installerType) && currentManifest != null
                && currentManifest.getProductCode() != null
                && !currentManifest.getProductCode().isBlank()) {
            try {
                InstallerArgValidator.validateProductCode(currentManifest.getProductCode());
            } catch (ValidationException ve) {
                recordRollbackFailure(current, previous, initiatedBy,
                    FailureReason.MANIFEST_INVALID,
                    "current productCode invalid: " + ve.getMessage(), null, null, null);
                throw ve;
            }
            uninstallLog = installerLogPath(current.getPackageName(), "uninstall");
            InstallerExecutor.Result un = installer.uninstall(
                currentManifest.getProductCode(), uninstallLog, installerTimeout);
            uninstallExit = un.getExitCode();
            if (!un.isSuccess()) {
                recordRollbackFailure(current, previous, initiatedBy,
                    FailureReason.INSTALLER_EXECUTION_FAILED,
                    "uninstall exit=" + un.getExitCode(),
                    uninstallExit, uninstallLog.toString(), installerType);
                if (notificationService != null) {
                    notificationService.addAlert("ERROR",
                        "Rollback uninstall failed (exit " + un.getExitCode() + ")",
                        PACKAGE_ENTITY, null);
                }
                throw new ValidationException("rollback",
                    FailureReason.INSTALLER_EXECUTION_FAILED.name()
                        + ": uninstall exit=" + un.getExitCode());
            }
        }

        // Step 2: filesystem restore.
        Path currentInstalled = latestInstalled();
        Path restoredTarget;
        try {
            if (currentInstalled != null) {
                Path supersededBackup = backupsDir.resolve(
                    currentInstalled.getFileName().toString() + "-rollback-" + System.currentTimeMillis());
                Files.move(currentInstalled, supersededBackup, StandardCopyOption.ATOMIC_MOVE);
            }
            restoredTarget = installedDir.resolve(backup.getFileName().toString());
            Files.move(backup, restoredTarget, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            recordRollbackFailure(current, previous, initiatedBy,
                FailureReason.UNEXPECTED, "filesystem restore failed: " + e.getMessage(),
                uninstallExit, uninstallLog == null ? null : uninstallLog.toString(), installerType);
            throw new ValidationException("rollback",
                "Rollback filesystem restore failed: " + e.getMessage());
        }

        // Step 3: re-install the previous MSI if it exists in the restored tree.
        UpdatePackage.Manifest previousManifest = readInstalledManifest(restoredTarget.toString());
        Path reinstallLog = null;
        Integer reinstallExit = null;
        if (previousManifest != null && "MSI".equals(previousManifest.getInstallerType())
                && previousManifest.getInstallerFile() != null) {
            Path msi = restoredTarget.resolve(previousManifest.getInstallerFile());
            if (Files.isRegularFile(msi)) {
                reinstallLog = installerLogPath(previous.getPackageName(), "reinstall");
                List<String> props = InstallerArgValidator.sanitize(previousManifest.getInstallArgs());
                InstallerExecutor.Result re = installer.install(msi, props, reinstallLog, installerTimeout);
                reinstallExit = re.getExitCode();
                if (!re.isSuccess()) {
                    recordRollbackFailure(current, previous, initiatedBy,
                        FailureReason.INSTALLER_EXECUTION_FAILED,
                        "reinstall of previous version failed exit=" + re.getExitCode(),
                        reinstallExit, reinstallLog.toString(), installerType);
                    throw new ValidationException("rollback",
                        FailureReason.INSTALLER_EXECUTION_FAILED.name()
                            + ": reinstall exit=" + re.getExitCode());
                }
            }
        }

        // Step 4: success row.
        UpdateHistoryEntry entry = new UpdateHistoryEntry();
        entry.setPackageName(previous.getPackageName());
        entry.setFromVersion(current.getToVersion());
        entry.setToVersion(previous.getToVersion());
        entry.setAction("ROLLED_BACK");
        entry.setStatus("SUCCESS");
        entry.setSignatureStatus(previous.getSignatureStatus());
        entry.setInstalledPath(restoredTarget.toString());
        entry.setInitiatedBy(initiatedBy);
        entry.setInstallerType(installerType);
        entry.setExitCode(reinstallExit != null ? reinstallExit : uninstallExit);
        entry.setLogPath(reinstallLog != null ? reinstallLog.toString()
            : (uninstallLog == null ? null : uninstallLog.toString()));
        entry.setNotes("one-click rollback via " + installer.mode());
        long id = historyRepo.insert(entry);
        entry.setId(id);

        auditService.record(PACKAGE_ENTITY, id, "UPDATE_ROLLBACK", initiatedBy,
            null, Map.of("fromVersion", current.getToVersion()),
            Map.of("toVersion", previous.getToVersion(),
                "installerMode", installer.mode(),
                "uninstallExit", uninstallExit == null ? "n/a" : uninstallExit.toString(),
                "reinstallExit", reinstallExit == null ? "n/a" : reinstallExit.toString()),
            null);
        if (notificationService != null) {
            notificationService.addAlert("WARN",
                "Rolled back from " + current.getToVersion() + " to " + previous.getToVersion(),
                PACKAGE_ENTITY, id);
        }
        return entry;
    }

    private Path resolveRollbackBackupPath(UpdateHistoryEntry current, UpdateHistoryEntry previous) {
        // Most recent apply stored the PRIOR version's directory in its own
        // backupPath column, so prefer that.  Fallback to the previous row's
        // backupPath for legacy rows.
        if (current.getBackupPath() != null) return Path.of(current.getBackupPath());
        if (previous.getBackupPath() != null) return Path.of(previous.getBackupPath());
        return null;
    }

    private void recordRollbackFailure(UpdateHistoryEntry current, UpdateHistoryEntry previous,
                                        long initiatedBy, FailureReason reason, String detail,
                                        Integer exitCode, String logPath, String installerType) {
        UpdateHistoryEntry entry = new UpdateHistoryEntry();
        entry.setPackageName(previous.getPackageName());
        entry.setFromVersion(current.getToVersion());
        entry.setToVersion(previous.getToVersion());
        entry.setAction("ROLLED_BACK");
        entry.setStatus("FAILED");
        entry.setErrorMessage(reason.name() + ": " + detail);
        entry.setInitiatedBy(initiatedBy);
        entry.setExitCode(exitCode);
        entry.setLogPath(logPath);
        entry.setInstallerType(installerType);
        long id = historyRepo.insert(entry);
        auditService.record(PACKAGE_ENTITY, id, "UPDATE_ROLLBACK_FAILED", initiatedBy,
            null, null, Map.of("reason", reason.name(), "detail", detail), null);
    }

    private UpdatePackage.Manifest readInstalledManifest(String installedPath) {
        if (installedPath == null) return null;
        Path file = Path.of(installedPath, "manifest.json");
        if (!Files.isRegularFile(file)) return null;
        try {
            return MAPPER.readValue(Files.readAllBytes(file), UpdatePackage.Manifest.class);
        } catch (IOException e) {
            log.warn("Could not parse installed manifest at {}: {}", file, e.getMessage());
            return null;
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

    private Path installerLogPath(UpdatePackage pkg, String action) {
        return installerLogPath(pkg.getPackageName(), action);
    }

    private Path installerLogPath(String packageName, String action) {
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC).format(Instant.now());
        return installerLogDir.resolve(packageName + "-" + action + "-" + ts + ".log");
    }

    private UpdateHistoryEntry recordHistory(UpdatePackage pkg, String sha, String action, String status,
                                              String signatureStatus,
                                              String installedPath, String backupPath,
                                              String error, Long initiatedBy, String notes,
                                              Integer exitCode, String installerType) {
        return recordHistory(pkg, sha, action, status, signatureStatus,
            installedPath, backupPath, error, initiatedBy, notes,
            exitCode, installerType, null, null);
    }

    private UpdateHistoryEntry recordHistory(UpdatePackage pkg, String sha, String action, String status,
                                              String signatureStatus,
                                              String installedPath, String backupPath,
                                              String error, Long initiatedBy, String notes,
                                              Integer exitCode, String installerType, String logPath) {
        return recordHistory(pkg, sha, action, status, signatureStatus,
            installedPath, backupPath, error, initiatedBy, notes,
            exitCode, installerType, logPath, null);
    }

    private UpdateHistoryEntry recordHistory(UpdatePackage pkg, String sha, String action, String status,
                                              String signatureStatus,
                                              String installedPath, String backupPath,
                                              String error, Long initiatedBy, String notes,
                                              Integer exitCode, String installerType, String logPath,
                                              String recoveryState) {
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
        e.setExitCode(exitCode);
        e.setInstallerType(installerType);
        e.setLogPath(logPath);
        e.setRecoveryState(recoveryState);
        long id = historyRepo.insert(e);
        e.setId(id);
        return e;
    }

    /**
     * Best-effort revert after an installer failure.
     *
     * <ol>
     *   <li>Delete the half-promoted {@code installTarget} directory.</li>
     *   <li>Move the previous {@code backupPath} tree back into
     *       {@code installed/} under its original package directory name.
     *       The backup folder name carries a {@code -{epochMillis}} suffix
     *       that we strip so rollback + {@link #latestInstalled()} can find
     *       the right payload.</li>
     * </ol>
     *
     * @return {@link #RECOVERY_AUTO_REVERTED} on success or
     *         {@link #RECOVERY_NEEDS_MANUAL} if any step fails.
     */
    private String attemptAutoRevert(Path installTarget, Path backupPath) {
        try {
            if (installTarget != null && Files.exists(installTarget)) {
                deleteTree(installTarget);
            }
            if (backupPath != null && Files.exists(backupPath)) {
                String restoredName = stripTimestampSuffix(backupPath.getFileName().toString());
                Path restoreTarget = installedDir.resolve(restoredName);
                if (Files.exists(restoreTarget)) deleteTree(restoreTarget);
                Files.move(backupPath, restoreTarget, StandardCopyOption.ATOMIC_MOVE);
            }
            return RECOVERY_AUTO_REVERTED;
        } catch (IOException e) {
            log.error("Auto-revert failed for {}: {}", installTarget, e.getMessage(), e);
            return RECOVERY_NEEDS_MANUAL;
        }
    }

    /**
     * Backup folders are named {@code {package}-{epochMillis}}; strip the
     * trailing {@code -\d+} so we can restore to the package's original
     * {@code installed/} location.  Also tolerates {@code -rollback-\d+}
     * produced by the rollback path.
     */
    private static String stripTimestampSuffix(String backupName) {
        String n = backupName.replaceFirst("-rollback-\\d+$", "");
        n = n.replaceFirst("-\\d+$", "");
        return n;
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
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
    public Path installerLogDir() { return installerLogDir; }
    public InstallerExecutor installer() { return installer; }
}
