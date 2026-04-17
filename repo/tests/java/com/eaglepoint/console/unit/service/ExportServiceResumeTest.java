package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.model.Community;
import com.eaglepoint.console.model.ExportJob;
import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.repository.*;
import com.eaglepoint.console.service.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Crash-safe resume coverage for {@link ExportService}.
 *
 * <p>An export that crashed mid-write leaves a {@code .part} temp file on
 * disk and a DB row in RUNNING with {@code checkpoint_path} pointing at it.
 * Resume must delete the stale partial, reset the job, re-execute it, and
 * leave the final artefact intact with a SHA-256 sidecar.</p>
 */
@ExtendWith(MockitoExtension.class)
class ExportServiceResumeTest {

    @Mock private ExportJobRepository exportRepo;
    @Mock private CommunityRepository communityRepo;
    @Mock private ServiceAreaRepository serviceAreaRepo;
    @Mock private PickupPointRepository pickupPointRepo;
    @Mock private BedRepository bedRepo;
    @Mock private BedBuildingRepository buildingRepo;
    @Mock private BedRoomRepository roomRepo;
    @Mock private UserRepository userRepo;
    @Mock private KpiRepository kpiRepo;
    @Mock private GeozoneRepository geozoneRepo;

    @TempDir Path tempDir;

    private ExportService service;

    @BeforeEach
    void setUp() {
        service = new ExportService(
            exportRepo, communityRepo, serviceAreaRepo, pickupPointRepo,
            bedRepo, buildingRepo, roomRepo, userRepo, kpiRepo, geozoneRepo);
    }

    @Test
    void executeJobWritesAtomicallyAndSetsCheckpoint() throws Exception {
        ExportJob job = new ExportJob();
        job.setId(1L);
        job.setType("CSV");
        job.setEntityType("COMMUNITIES");
        job.setDestinationPath(tempDir.toString());
        job.setStatus("RUNNING");

        Community c = new Community();
        c.setId(10L);
        c.setName("Demo");
        c.setDescription("d");
        c.setStatus("ACTIVE");

        when(exportRepo.findById(1L)).thenReturn(Optional.of(job));
        when(communityRepo.findAll(anyInt(), anyInt())).thenReturn(new PagedResult<>(List.of(c), 1, 1, 500));

        service.executeJob(1L);

        // Checkpoint was set at start
        verify(exportRepo, atLeastOnce()).updateCheckpointPath(eq(1L), argThat(p ->
            p != null && p.endsWith(".part")));
        // Checkpoint was cleared after completion
        verify(exportRepo).updateCheckpointPath(1L, null);
        verify(exportRepo).updateStatus(eq(1L), eq("COMPLETED"), argThat(p ->
            p != null && !p.endsWith(".part")), notNull(), isNull());

        // No .part files should remain in the destination folder.
        try (var stream = Files.list(tempDir)) {
            long partCount = stream.filter(p -> p.toString().endsWith(".part")).count();
            assertEquals(0, partCount, ".part temp files must be cleaned up on success");
        }
    }

    @Test
    void resumeDeletesStalePartAndRetriesJob() throws Exception {
        // Simulate a crashed job: RUNNING, checkpoint_path points at a
        // leftover .part file.
        Path destDir = tempDir;
        Path stalePart = destDir.resolve("COMMUNITIES_export_stale.csv.part");
        Files.writeString(stalePart, "partial garbage");
        assertTrue(Files.exists(stalePart));

        ExportJob job = new ExportJob();
        job.setId(2L);
        job.setType("CSV");
        job.setEntityType("COMMUNITIES");
        job.setDestinationPath(destDir.toString());
        job.setStatus("RUNNING");
        job.setCheckpointPath(stalePart.toString());

        when(exportRepo.findIncomplete()).thenReturn(List.of(job));
        when(exportRepo.findById(2L)).thenReturn(Optional.of(job));
        when(communityRepo.findAll(anyInt(), anyInt())).thenReturn(new PagedResult<>(List.of(), 0, 1, 500));

        service.resumeIncompleteJobs();

        // Give the async worker a moment to run.
        Thread.sleep(400);

        assertFalse(Files.exists(stalePart),
            "Stale .part must be removed before retrying the job");
        verify(exportRepo).updateCheckpointPath(2L, null);
        verify(exportRepo).updateStarted(2L);
    }

    @Test
    void completedJobIsNotReRun() {
        ExportJob job = new ExportJob();
        job.setId(3L);
        job.setStatus("COMPLETED");
        job.setType("CSV");
        job.setEntityType("COMMUNITIES");
        job.setDestinationPath(tempDir.toString());

        when(exportRepo.findById(3L)).thenReturn(Optional.of(job));

        service.executeJob(3L);

        verify(exportRepo, never()).updateStatus(anyLong(), eq("COMPLETED"), any(), any(), any());
        verify(exportRepo, never()).updateCheckpointPath(eq(3L), anyString());
    }
}
