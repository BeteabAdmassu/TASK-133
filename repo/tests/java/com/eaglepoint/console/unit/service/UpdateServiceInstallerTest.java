package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.UpdateHistoryEntry;
import com.eaglepoint.console.repository.UpdateHistoryRepository;
import com.eaglepoint.console.security.UpdateSignatureVerifier;
import com.eaglepoint.console.service.AuditService;
import com.eaglepoint.console.service.NotificationService;
import com.eaglepoint.console.service.UpdateService;
import com.eaglepoint.console.service.updater.TestModeInstallerExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Focused coverage for the real {@code .msi} apply/rollback orchestration
 * layer introduced alongside {@code InstallerExecutor}:
 *
 * <ul>
 *   <li>Valid signed package → installer invoked → INSTALLED row</li>
 *   <li>Invalid signature → installer NEVER invoked, UPDATE_REJECTED row + audit</li>
 *   <li>Installer non-zero exit → FAILED row + error reason; exit code and
 *       log path persisted</li>
 *   <li>Rollback path runs {@code msiexec /x} against prior productCode
 *       and writes a ROLLED_BACK row</li>
 *   <li>Rollback failure (missing backup) surfaces as 409 CONFLICT</li>
 *   <li>{@code installArgs} with shell metacharacters are rejected before
 *       any installer command runs</li>
 * </ul>
 */
class UpdateServiceInstallerTest {

    private static final String PRODUCT_GUID_A = "{12345678-1234-1234-1234-1234567890AB}";
    private static final String PRODUCT_GUID_B = "{ABCDEF01-2345-6789-ABCD-EF0123456789}";

    @TempDir Path tempDir;

    private KeyPair signingKeys;
    private UpdateSignatureVerifier verifier;
    private TestModeInstallerExecutor installer;
    private AuditService audit;
    private NotificationService notifications;
    private StubHistoryRepo history;
    private UpdateService service;

    @BeforeEach
    void setUp() throws Exception {
        signingKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        verifier = new UpdateSignatureVerifier(signingKeys.getPublic(), "test-key");
        installer = new TestModeInstallerExecutor();
        audit = mock(AuditService.class);
        notifications = mock(NotificationService.class);
        history = new StubHistoryRepo();
        service = new UpdateService(tempDir, verifier, history, audit, notifications, installer);
    }

    @Test
    void applyWithValidMsiPackageInvokesInstallerAndPersistsExitCode() throws Exception {
        createSignedMsiPackage("eaglepoint-1.2.0", "1.2.0", PRODUCT_GUID_A,
            List.of("INSTALLDIR=C:\\EaglePoint", "ENVIRONMENT=prod"));

        UpdateHistoryEntry entry = service.applyPackage("eaglepoint-1.2.0", 7L);

        assertEquals("INSTALLED", entry.getAction());
        assertEquals("SUCCESS", entry.getStatus());
        assertEquals("MSI", entry.getInstallerType());
        assertEquals(Integer.valueOf(0), entry.getExitCode());
        assertNotNull(entry.getLogPath(), "installer log path must be persisted");
        assertTrue(Files.exists(Path.of(entry.getLogPath())),
            "test-mode executor should write a simulated log");

        List<TestModeInstallerExecutor.Invocation> invs = installer.getInvocations();
        assertEquals(1, invs.size());
        assertEquals(TestModeInstallerExecutor.InvocationKind.INSTALL, invs.get(0).kind);
        assertEquals(2, invs.get(0).properties.size());
        assertTrue(invs.get(0).target.endsWith(".msi"));

        verify(audit).record(eq("UpdatePackage"), anyLong(), eq("UPDATE_APPLIED"),
            eq(7L), any(), any(), any(), any());
    }

    @Test
    void applyWithInvalidSignatureSkipsInstaller() throws Exception {
        createSignedMsiPackage("eaglepoint-1.3.0", "1.3.0", PRODUCT_GUID_A, List.of());
        // Mutate manifest post-signing
        Path mf = tempDir.resolve("incoming/eaglepoint-1.3.0/manifest.json");
        Files.writeString(mf, Files.readString(mf).replace("1.3.0", "9.9.9"));

        ValidationException ve = assertThrows(ValidationException.class,
            () -> service.applyPackage("eaglepoint-1.3.0", 3L));
        assertTrue(ve.getMessage().contains("SIGNATURE_INVALID"));
        assertEquals(0, installer.getInvocations().size(),
            "installer must not run when signature verification fails");
        verify(audit).record(eq("UpdatePackage"), anyLong(), eq("UPDATE_REJECTED"),
            eq(3L), any(), any(), any(), any());
    }

    @Test
    void applyWithInstallerNonZeroExitRecordsFailure() throws Exception {
        createSignedMsiPackage("eaglepoint-1.4.0", "1.4.0", PRODUCT_GUID_A, List.of());
        installer.setNextExitCode(1603); // classic MSI "fatal error during installation"

        ValidationException ve = assertThrows(ValidationException.class,
            () -> service.applyPackage("eaglepoint-1.4.0", 8L));
        assertTrue(ve.getMessage().contains("INSTALLER_EXECUTION_FAILED"));
        assertTrue(ve.getMessage().contains("1603"));

        // A FAILED row should be present with the installer exit code captured.
        UpdateHistoryEntry failed = history.findAll(10).stream()
            .filter(e -> "FAILED".equals(e.getAction()) && "FAILED".equals(e.getStatus()))
            .findFirst().orElseThrow();
        assertEquals(Integer.valueOf(1603), failed.getExitCode());
        assertEquals("MSI", failed.getInstallerType());
        verify(notifications).addAlert(eq("ERROR"), anyString(), eq("UpdatePackage"), any());
    }

    @Test
    void applyWithUnsafeInstallArgsIsRejectedBeforeInstall() throws Exception {
        createSignedMsiPackage("eaglepoint-1.5.0", "1.5.0", PRODUCT_GUID_A,
            List.of("INSTALLDIR=C:\\ok; shutdown /s")); // unsafe metacharacter

        ValidationException ve = assertThrows(ValidationException.class,
            () -> service.applyPackage("eaglepoint-1.5.0", 1L));
        assertTrue(ve.getMessage().toLowerCase().contains("installarg"));
        assertEquals(0, installer.getInvocations().size(),
            "unsafe install args must short-circuit before the installer runs");
    }

    @Test
    void rollbackInvokesUninstallOnCurrentProductCode() throws Exception {
        // Apply two MSI versions so history has a rollback target.
        createSignedMsiPackage("eaglepoint-1.0.0", "1.0.0", PRODUCT_GUID_A, List.of());
        service.applyPackage("eaglepoint-1.0.0", 4L);
        createSignedMsiPackage("eaglepoint-1.1.0", "1.1.0", PRODUCT_GUID_B, List.of());
        service.applyPackage("eaglepoint-1.1.0", 4L);
        installer.clear(); // ignore apply invocations

        UpdateHistoryEntry rolled = service.rollback(4L);
        assertEquals("ROLLED_BACK", rolled.getAction());
        assertEquals("SUCCESS", rolled.getStatus());
        assertEquals("1.0.0", rolled.getToVersion());
        assertEquals("1.1.0", rolled.getFromVersion());

        // Should have triggered at least one UNINSTALL against the current product code,
        // followed by an INSTALL against the restored previous MSI.
        List<TestModeInstallerExecutor.Invocation> invs = installer.getInvocations();
        assertTrue(invs.stream().anyMatch(i ->
            i.kind == TestModeInstallerExecutor.InvocationKind.UNINSTALL
                && PRODUCT_GUID_B.equals(i.target)),
            "rollback must uninstall the current productCode");
        assertTrue(invs.stream().anyMatch(i ->
            i.kind == TestModeInstallerExecutor.InvocationKind.INSTALL
                && i.target.endsWith(".msi")),
            "rollback must reinstall the previous MSI once files are restored");

        verify(audit).record(eq("UpdatePackage"), anyLong(), eq("UPDATE_ROLLBACK"),
            eq(4L), any(), any(), any(), any());
    }

    @Test
    void rollbackWithoutPriorInstallReturns409() {
        assertThrows(ConflictException.class, () -> service.rollback(1L));
    }

    @Test
    void rollbackSurfacesInstallerUninstallFailureAsValidationError() throws Exception {
        createSignedMsiPackage("eaglepoint-1.0.0", "1.0.0", PRODUCT_GUID_A, List.of());
        service.applyPackage("eaglepoint-1.0.0", 5L);
        createSignedMsiPackage("eaglepoint-1.1.0", "1.1.0", PRODUCT_GUID_B, List.of());
        service.applyPackage("eaglepoint-1.1.0", 5L);
        installer.clear();
        installer.setNextExitCode(1605); // "The action is only valid for products that are currently installed"

        ValidationException ve = assertThrows(ValidationException.class, () -> service.rollback(5L));
        assertTrue(ve.getMessage().contains("INSTALLER_EXECUTION_FAILED"));
        assertTrue(ve.getMessage().contains("1605"));

        // History should record a FAILED rollback row with the exit code.
        UpdateHistoryEntry failed = history.findAll(20).stream()
            .filter(e -> "ROLLED_BACK".equals(e.getAction()) && "FAILED".equals(e.getStatus()))
            .findFirst().orElseThrow();
        assertEquals(Integer.valueOf(1605), failed.getExitCode());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void createSignedMsiPackage(String dirName, String version, String productCode,
                                         List<String> installArgs) throws Exception {
        Path pkg = tempDir.resolve("incoming").resolve(dirName);
        Files.createDirectories(pkg);
        Path msi = pkg.resolve(dirName + ".msi");
        Files.writeString(msi, "fake-msi-bytes-for-" + version);
        String sha = DigestUtils.sha256Hex(Files.readAllBytes(msi));

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", version);
        manifest.put("installerType", "MSI");
        manifest.put("installerFile", dirName + ".msi");
        manifest.put("payloadFilename", dirName + ".msi");
        manifest.put("payloadSha256", sha);
        manifest.put("payloadSize", Files.size(msi));
        manifest.put("productCode", productCode);
        manifest.put("signingKeyId", "test-key");
        manifest.put("installArgs", installArgs);
        byte[] bytes = new ObjectMapper().writeValueAsBytes(manifest);
        Files.write(pkg.resolve("manifest.json"), bytes);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(signingKeys.getPrivate());
        signer.update(bytes);
        Files.write(pkg.resolve("manifest.sig"), signer.sign());
    }

    private static final class StubHistoryRepo extends UpdateHistoryRepository {
        private final java.util.List<UpdateHistoryEntry> rows = new java.util.ArrayList<>();
        private final AtomicLong nextId = new AtomicLong(1);

        StubHistoryRepo() {
            super(null);
        }

        @Override public long insert(UpdateHistoryEntry e) {
            long id = nextId.getAndIncrement();
            e.setId(id);
            UpdateHistoryEntry c = new UpdateHistoryEntry();
            c.setId(id);
            c.setPackageName(e.getPackageName());
            c.setFromVersion(e.getFromVersion());
            c.setToVersion(e.getToVersion());
            c.setAction(e.getAction());
            c.setStatus(e.getStatus());
            c.setSha256Hash(e.getSha256Hash());
            c.setSignatureStatus(e.getSignatureStatus());
            c.setInstalledPath(e.getInstalledPath());
            c.setBackupPath(e.getBackupPath());
            c.setErrorMessage(e.getErrorMessage());
            c.setInitiatedBy(e.getInitiatedBy());
            c.setNotes(e.getNotes());
            c.setExitCode(e.getExitCode());
            c.setLogPath(e.getLogPath());
            c.setInstallerType(e.getInstallerType());
            rows.add(c);
            return id;
        }

        @Override public java.util.List<UpdateHistoryEntry> findAll(int limit) {
            java.util.List<UpdateHistoryEntry> reversed = new java.util.ArrayList<>(rows);
            java.util.Collections.reverse(reversed);
            return reversed.stream().limit(limit).toList();
        }

        @Override public java.util.Optional<UpdateHistoryEntry> findCurrentInstalled() {
            for (int i = rows.size() - 1; i >= 0; i--) {
                UpdateHistoryEntry r = rows.get(i);
                if ("INSTALLED".equals(r.getAction()) && "SUCCESS".equals(r.getStatus())) {
                    return java.util.Optional.of(r);
                }
            }
            return java.util.Optional.empty();
        }

        @Override public java.util.Optional<UpdateHistoryEntry> findPreviousInstalledBefore(long id) {
            for (int i = rows.size() - 1; i >= 0; i--) {
                UpdateHistoryEntry r = rows.get(i);
                if (r.getId() < id && "INSTALLED".equals(r.getAction()) && "SUCCESS".equals(r.getStatus())) {
                    return java.util.Optional.of(r);
                }
            }
            return java.util.Optional.empty();
        }
    }
}
