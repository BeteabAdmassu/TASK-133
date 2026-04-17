package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.UpdateHistoryEntry;
import com.eaglepoint.console.repository.UpdateHistoryRepository;
import com.eaglepoint.console.security.UpdateSignatureVerifier;
import com.eaglepoint.console.service.AuditService;
import com.eaglepoint.console.service.NotificationService;
import com.eaglepoint.console.service.UpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * End-to-end (unit level) coverage for {@link UpdateService}:
 * <ul>
 *   <li>Valid Ed25519-signed package is accepted</li>
 *   <li>Tampered payload (hash mismatch) is rejected</li>
 *   <li>Invalid signature is rejected</li>
 *   <li>Apply moves previous payload to backups/</li>
 *   <li>Rollback restores the backup and writes a ROLLED_BACK history row</li>
 *   <li>Audit events are emitted for apply, reject, rollback</li>
 * </ul>
 */
class UpdateServiceTest {

    @TempDir Path tempDir;
    private KeyPair signingKeys;
    private UpdateSignatureVerifier verifier;
    private AuditService audit;
    private NotificationService notifications;
    private StubHistoryRepo historyRepo;
    private UpdateService service;

    @BeforeEach
    void setUp() throws Exception {
        signingKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        verifier = new UpdateSignatureVerifier(signingKeys.getPublic(), "test-key");
        audit = mock(AuditService.class);
        notifications = mock(NotificationService.class);
        historyRepo = new StubHistoryRepo();
        service = new UpdateService(tempDir, verifier, historyRepo, audit, notifications);
    }

    @Test
    void listsWellFormedPackages() throws Exception {
        createSignedPackage("eaglepoint-1.0.0", "1.0.0", "payload 1.0.0");
        createSignedPackage("eaglepoint-1.1.0", "1.1.0", "payload 1.1.0");

        var packages = service.listAvailablePackages();
        assertEquals(2, packages.size());
        assertEquals("eaglepoint-1.0.0", packages.get(0).getPackageName());
        assertEquals("1.1.0", packages.get(1).getManifest().getVersion());
    }

    @Test
    void verifyRejectsUnsignedPackage() throws Exception {
        Path pkg = tempDir.resolve("incoming").resolve("eaglepoint-1.2.0");
        Files.createDirectories(pkg);
        Map<String, Object> manifest = Map.of(
            "version", "1.2.0",
            "payloadFilename", "payload.zip",
            "payloadSha256", "deadbeef");
        Files.writeString(pkg.resolve("manifest.json"), new ObjectMapper().writeValueAsString(manifest));
        Files.writeString(pkg.resolve("payload.zip"), "whatever");

        UpdateSignatureVerifier.Result r = service.verifyPackage("eaglepoint-1.2.0");
        assertFalse(r.isValid());
        assertEquals("INVALID", r.getStatus()); // no sig => treated as invalid
    }

    @Test
    void verifyRejectsTamperedManifest() throws Exception {
        createSignedPackage("eaglepoint-1.3.0", "1.3.0", "payload 1.3.0");
        // Mutate manifest AFTER signing so signature no longer matches
        Path manifest = tempDir.resolve("incoming/eaglepoint-1.3.0/manifest.json");
        String body = Files.readString(manifest);
        Files.writeString(manifest, body.replace("1.3.0", "9.9.9"));

        UpdateSignatureVerifier.Result r = service.verifyPackage("eaglepoint-1.3.0");
        assertFalse(r.isValid());
        assertEquals("INVALID", r.getStatus());
    }

    @Test
    void verifyRejectsPayloadShaMismatch() throws Exception {
        createSignedPackage("eaglepoint-1.4.0", "1.4.0", "payload");
        // Replace payload with different bytes (signature over manifest is still valid,
        // but manifest's payloadSha256 no longer matches).
        Files.writeString(tempDir.resolve("incoming/eaglepoint-1.4.0/payload.zip"), "tampered");
        UpdateSignatureVerifier.Result r = service.verifyPackage("eaglepoint-1.4.0");
        assertFalse(r.isValid());
        assertTrue(r.getReason().contains("payload"));
    }

    @Test
    void applyInstallsPackageAndMovesPreviousToBackups() throws Exception {
        createSignedPackage("eaglepoint-1.0.0", "1.0.0", "payload-1.0.0");
        UpdateHistoryEntry first = service.applyPackage("eaglepoint-1.0.0", 99L);
        assertEquals("INSTALLED", first.getAction());
        assertEquals("SUCCESS", first.getStatus());

        // Second apply must move the 1.0.0 payload into backups/
        createSignedPackage("eaglepoint-1.1.0", "1.1.0", "payload-1.1.0");
        UpdateHistoryEntry second = service.applyPackage("eaglepoint-1.1.0", 99L);
        assertEquals("1.1.0", second.getToVersion());
        assertEquals("1.0.0", second.getFromVersion());
        assertNotNull(second.getBackupPath());
        assertTrue(Files.exists(Path.of(second.getBackupPath())),
            "Previous installed payload should be preserved in backups/");
        assertTrue(Files.exists(Path.of(second.getInstalledPath())),
            "New package should be promoted into installed/");

        verify(audit).record(eq("UpdatePackage"), anyLong(), eq("UPDATE_APPLIED"),
            eq(99L), any(), any(), any(), any());
    }

    @Test
    void applyRejectsTamperedPackageWithoutInstalling() throws Exception {
        createSignedPackage("eaglepoint-2.0.0", "2.0.0", "payload");
        // Tamper payload so the signature is fine but payload sha mismatches.
        Files.writeString(tempDir.resolve("incoming/eaglepoint-2.0.0/payload.zip"), "tampered");

        ValidationException e = assertThrows(ValidationException.class,
            () -> service.applyPackage("eaglepoint-2.0.0", 42L));
        assertTrue(e.getMessage().toLowerCase().contains("payload"));
        // Nothing got promoted into installed/
        Path installed = tempDir.resolve("installed");
        try (var s = Files.list(installed)) {
            assertEquals(0, s.count(), "Rejected package must not be installed");
        }
        verify(notifications).addAlert(eq("ERROR"), anyString(), eq("UpdatePackage"), any());
    }

    @Test
    void rollbackRestoresPreviousInstalledPayload() throws Exception {
        createSignedPackage("eaglepoint-1.0.0", "1.0.0", "payload-1.0.0");
        service.applyPackage("eaglepoint-1.0.0", 5L);

        createSignedPackage("eaglepoint-1.1.0", "1.1.0", "payload-1.1.0");
        UpdateHistoryEntry forward = service.applyPackage("eaglepoint-1.1.0", 5L);
        Path priorBackup = Path.of(forward.getBackupPath());
        assertTrue(Files.exists(priorBackup));

        UpdateHistoryEntry rolled = service.rollback(5L);
        assertEquals("ROLLED_BACK", rolled.getAction());
        assertEquals("SUCCESS", rolled.getStatus());
        assertEquals("1.0.0", rolled.getToVersion());
        assertEquals("1.1.0", rolled.getFromVersion());
        // The backup was restored into installed/
        assertTrue(Files.exists(Path.of(rolled.getInstalledPath())));
        verify(audit).record(eq("UpdatePackage"), anyLong(), eq("UPDATE_ROLLBACK"),
            eq(5L), any(), any(), any(), any());
    }

    @Test
    void rollbackWithoutPriorInstallReturns409() {
        assertThrows(ConflictException.class, () -> service.rollback(1L));
    }

    @Test
    void verifierWithNoTrustKeyIsUntrusted() {
        UpdateSignatureVerifier untrusted = new UpdateSignatureVerifier(null, "none");
        UpdateSignatureVerifier.Result r = untrusted.verify("{}".getBytes(), "abc".getBytes());
        assertFalse(r.isValid());
        assertEquals("UNTRUSTED", r.getStatus());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void createSignedPackage(String dirName, String version, String payloadContent) throws Exception {
        Path pkg = tempDir.resolve("incoming").resolve(dirName);
        Files.createDirectories(pkg);
        Path payload = pkg.resolve("payload.zip");
        Files.writeString(payload, payloadContent);
        String payloadSha = DigestUtils.sha256Hex(Files.readAllBytes(payload));

        // Use LinkedHashMap so JSON field order stays stable and the hash we
        // hand to the verifier matches what the service reads.
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", version);
        manifest.put("payloadFilename", "payload.zip");
        manifest.put("payloadSha256", payloadSha);
        manifest.put("payloadSize", Files.size(payload));
        manifest.put("signingKeyId", "test-key");
        byte[] manifestBytes = new ObjectMapper().writeValueAsBytes(manifest);
        Files.write(pkg.resolve("manifest.json"), manifestBytes);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(signingKeys.getPrivate());
        signer.update(manifestBytes);
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
            UpdateHistoryEntry copy = new UpdateHistoryEntry();
            copy.setId(id);
            copy.setPackageName(e.getPackageName());
            copy.setFromVersion(e.getFromVersion());
            copy.setToVersion(e.getToVersion());
            copy.setAction(e.getAction());
            copy.setStatus(e.getStatus());
            copy.setSha256Hash(e.getSha256Hash());
            copy.setSignatureStatus(e.getSignatureStatus());
            copy.setInstalledPath(e.getInstalledPath());
            copy.setBackupPath(e.getBackupPath());
            copy.setErrorMessage(e.getErrorMessage());
            copy.setInitiatedBy(e.getInitiatedBy());
            copy.setNotes(e.getNotes());
            rows.add(copy);
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
