package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.model.RouteCheckpoint;
import com.eaglepoint.console.model.RouteImport;
import com.eaglepoint.console.repository.RouteImportRepository;
import com.eaglepoint.console.service.AuditService;
import com.eaglepoint.console.service.NotificationService;
import com.eaglepoint.console.service.RouteImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Crash-safe resume coverage for {@link RouteImportService}.
 *
 * <p>The scenarios here simulate a JVM interruption after the parse phase
 * has persisted a checkpoint JSON sidecar but before every checkpoint row
 * has been inserted into the database.  The test asserts that the resume
 * path:
 * <ol>
 *   <li>Finds the persisted sidecar.</li>
 *   <li>Skips checkpoints already present in the DB (idempotency — no duplicates).</li>
 *   <li>Inserts only the missing rows.</li>
 *   <li>Marks the import COMPLETED and clears the checkpoint path.</li>
 *   <li>Deletes the sidecar file.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class RouteImportResumeTest {

    @Mock private RouteImportRepository importRepo;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;

    @TempDir Path tempDir;

    private RouteImportService service;

    @BeforeEach
    void setUp() {
        service = new RouteImportService(importRepo, notificationService, auditService, tempDir);
    }

    @Test
    void commitFromCheckpointResumesWithoutDuplicatingRows() throws IOException {
        // Arrange: a persisted checkpoint file with 3 parsed checkpoints,
        // and the DB already has the first 2 rows inserted (simulating a
        // crash after row 2 had been committed).
        long importId = 42L;
        List<RouteCheckpoint> checkpoints = buildThreeCheckpoints(importId);
        Path checkpointFile = tempDir.resolve("import-42.json");
        new ObjectMapper().writeValue(checkpointFile.toFile(), checkpoints);

        RouteImport ri = new RouteImport();
        ri.setId(importId);
        ri.setStatus("PROCESSING");
        ri.setCheckpointPath(checkpointFile.toString());

        when(importRepo.findById(importId)).thenReturn(Optional.of(ri));
        when(importRepo.countCheckpoints(importId)).thenReturn(2);
        when(importRepo.countAlertCheckpoints(importId)).thenReturn(0);

        // Act
        AtomicInteger insertCount = new AtomicInteger();
        when(importRepo.insertCheckpoint(any())).thenAnswer(inv -> {
            insertCount.incrementAndGet();
            return 99L;
        });
        service.commitFromCheckpoint(importId);

        // Assert: only the uncommitted row 3 was inserted.
        assertEquals(1, insertCount.get(),
            "Resume must skip rows already present in the DB (idempotent)");
        verify(importRepo).updateStatus(eq(importId), eq("COMPLETED"), eq(3), anyInt());
        verify(importRepo).updateCheckpointPath(importId, null);
        assertFalse(Files.exists(checkpointFile),
            "Checkpoint sidecar should be deleted after successful commit");
    }

    @Test
    void commitFromCheckpointWithNoPriorRowsInsertsAll() throws IOException {
        long importId = 7L;
        List<RouteCheckpoint> checkpoints = buildThreeCheckpoints(importId);
        Path checkpointFile = tempDir.resolve("import-7.json");
        new ObjectMapper().writeValue(checkpointFile.toFile(), checkpoints);

        RouteImport ri = new RouteImport();
        ri.setId(importId);
        ri.setStatus("PROCESSING");
        ri.setCheckpointPath(checkpointFile.toString());

        when(importRepo.findById(importId)).thenReturn(Optional.of(ri));
        when(importRepo.countCheckpoints(importId)).thenReturn(0);
        when(importRepo.countAlertCheckpoints(importId)).thenReturn(0);

        AtomicInteger insertCount = new AtomicInteger();
        when(importRepo.insertCheckpoint(any())).thenAnswer(inv -> {
            insertCount.incrementAndGet();
            return 99L;
        });

        service.commitFromCheckpoint(importId);

        assertEquals(3, insertCount.get());
        verify(importRepo).updateStatus(eq(importId), eq("COMPLETED"), eq(3), anyInt());
    }

    @Test
    void commitFromCheckpointMarksFailedWhenSidecarMissing() {
        long importId = 11L;
        RouteImport ri = new RouteImport();
        ri.setId(importId);
        ri.setStatus("PROCESSING");
        ri.setCheckpointPath(tempDir.resolve("does-not-exist.json").toString());

        when(importRepo.findById(importId)).thenReturn(Optional.of(ri));

        service.commitFromCheckpoint(importId);

        verify(importRepo).updateStatus(importId, "FAILED", null, null);
        verify(notificationService).addAlert(eq("ERROR"), anyString(), eq("RouteImport"), eq(importId));
    }

    @Test
    void startImportParsesAndCommitsViaCheckpoint() throws Exception {
        long importId = 55L;

        // Mirror a real repo: when updateCheckpointPath is called, subsequent
        // findById calls must return an import that carries that path so
        // commitFromCheckpoint can re-read the checkpoint sidecar.
        java.util.concurrent.atomic.AtomicReference<String> cpPath =
            new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(inv -> {
            cpPath.set(inv.getArgument(1));
            return null;
        }).when(importRepo).updateCheckpointPath(eq(importId), any());

        when(importRepo.findById(importId)).thenAnswer(inv -> {
            RouteImport ri = new RouteImport();
            ri.setId(importId);
            ri.setStatus("PROCESSING");
            ri.setCheckpointPath(cpPath.get());
            return Optional.of(ri);
        });
        when(importRepo.countCheckpoints(importId)).thenReturn(0);
        when(importRepo.countAlertCheckpoints(importId)).thenReturn(0);

        String csv = "checkpoint_name,expected_at,actual_at,lat,lon\n"
            + "CP-1,2024-01-01T10:00:00Z,2024-01-01T10:01:00Z,37.7749,-122.4194\n"
            + "CP-2,2024-01-01T10:05:00Z,2024-01-01T10:06:00Z,37.7750,-122.4195\n";

        service.validateAndProcess(importId, csv.getBytes(StandardCharsets.UTF_8), false);

        verify(importRepo).updateCheckpointPath(eq(importId), argThat(p ->
            p != null && p.contains("import-" + importId)));
        verify(importRepo).updateCheckpointPath(importId, null);
        verify(importRepo, atLeastOnce()).insertCheckpoint(any());
        verify(importRepo).updateStatus(eq(importId), eq("COMPLETED"), anyInt(), anyInt());
    }

    private List<RouteCheckpoint> buildThreeCheckpoints(long importId) {
        RouteCheckpoint a = cp(importId, "CP-1", "ON_TIME", false, false);
        RouteCheckpoint b = cp(importId, "CP-2", "DEVIATED", true, false);
        RouteCheckpoint c = cp(importId, "CP-3", "MISSED", false, true);
        return new ArrayList<>(Arrays.asList(a, b, c));
    }

    private RouteCheckpoint cp(long importId, String name, String status,
                                boolean deviation, boolean missed) {
        RouteCheckpoint c = new RouteCheckpoint();
        c.setImportId(importId);
        c.setCheckpointName(name);
        c.setExpectedAt("2024-01-01T10:00:00Z");
        c.setActualAt("2024-01-01T10:01:00Z");
        c.setLatMasked(37.77);
        c.setLonMasked(-122.42);
        c.setDeviationMiles(0.1);
        c.setDeviationAlert(deviation);
        c.setMissedAlert(missed);
        c.setStatus(status);
        return c;
    }
}
