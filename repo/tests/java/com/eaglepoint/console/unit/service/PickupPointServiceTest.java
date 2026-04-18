package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.config.SecurityConfig;
import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.Community;
import com.eaglepoint.console.model.PickupPoint;
import com.eaglepoint.console.repository.CommunityRepository;
import com.eaglepoint.console.repository.PickupPointRepository;
import com.eaglepoint.console.service.AuditService;
import com.eaglepoint.console.service.PickupPointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class PickupPointServiceTest {

    @Mock private PickupPointRepository ppRepo;
    @Mock private CommunityRepository communityRepo;
    @Mock private AuditService auditService;

    private PickupPointService service;

    @BeforeEach
    void setUp() {
        SecurityConfig.initHeadless();
        service = new PickupPointService(ppRepo, communityRepo, SecurityConfig.getInstance(), auditService);
    }

    @Test
    void pausePickupPointSucceeds() {
        PickupPoint pp = new PickupPoint();
        pp.setId(1L);
        pp.setCommunityId(5L);
        pp.setStatus("ACTIVE");

        String futureTime = Instant.now().plus(1, ChronoUnit.DAYS).toString();

        when(ppRepo.findById(1L)).thenReturn(Optional.of(pp));
        doNothing().when(ppRepo).update(any());
        doNothing().when(auditService).record(any(), anyLong(), anyString(), anyLong(), any(), any(), any(), any());
        when(ppRepo.findById(1L)).thenReturn(Optional.of(pp));

        PickupPoint result = service.pausePickupPoint(1L, "Maintenance scheduled", futureTime, 2L, "trace-123");
        assertNotNull(result);
    }

    @Test
    void pauseClosedPointThrows() {
        PickupPoint pp = new PickupPoint();
        pp.setId(1L);
        pp.setStatus("CLOSED");

        String futureTime = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        when(ppRepo.findById(1L)).thenReturn(Optional.of(pp));

        assertThrows(ConflictException.class, () ->
            service.pausePickupPoint(1L, "Another pause reason here", futureTime, 2L, "trace"));
    }

    @Test
    void resumePickupPointSucceeds() {
        PickupPoint pp = new PickupPoint();
        pp.setId(1L);
        pp.setCommunityId(5L);
        pp.setStatus("PAUSED");

        when(ppRepo.findById(1L)).thenReturn(Optional.of(pp));
        // Day-scoped check: no OTHER pickup point in this community was active today
        when(ppRepo.countActiveOrActiveTodayByCommunityExcluding(eq(5L), anyString(), eq(1L))).thenReturn(0);
        doNothing().when(ppRepo).update(any());
        doNothing().when(auditService).record(any(), anyLong(), anyString(), anyLong(), any(), any(), any(), any());

        PickupPoint result = service.resumePickupPoint(1L, 2L, "trace-456");
        assertNotNull(result);
    }

    @Test
    void resumeActivePointThrows() {
        PickupPoint pp = new PickupPoint();
        pp.setId(1L);
        pp.setStatus("ACTIVE");

        when(ppRepo.findById(1L)).thenReturn(Optional.of(pp));

        assertThrows(ConflictException.class, () ->
            service.resumePickupPoint(1L, 2L, "trace"));
    }

    @Test
    void createPickupPointBlockedWhenAnotherWasActiveTodayInSameCommunity() {
        Community community = new Community();
        community.setId(10L);
        when(communityRepo.findById(10L)).thenReturn(Optional.of(community));
        // Simulates: this community already had a pickup point active today
        when(ppRepo.countActiveOrActiveTodayByCommunity(eq(10L), anyString())).thenReturn(1);

        assertThrows(ConflictException.class, () ->
            service.createPickupPoint(10L, "100 Main St", "12345", "100", "199", "{}", 5, null));
    }

    @Test
    void applyManualOverrideSucceeds() {
        PickupPoint pp = new PickupPoint();
        pp.setId(7L);
        pp.setStatus("ACTIVE");
        pp.setManualOverride(false);

        when(ppRepo.findById(7L)).thenReturn(Optional.of(pp));
        doNothing().when(ppRepo).update(any());
        doNothing().when(auditService).record(any(), anyLong(), anyString(), anyLong(), any(), any(), any(), any());

        PickupPoint result = service.applyManualOverride(7L, true, "Road closure — ticket #123", 1L, "trace-ov");
        assertNotNull(result);
        assertTrue(result.isManualOverride());
    }

    @Test
    void applyManualOverrideRequiresNotesWhenTrue() {
        PickupPoint pp = new PickupPoint();
        pp.setId(8L);
        when(ppRepo.findById(8L)).thenReturn(Optional.of(pp));

        assertThrows(com.eaglepoint.console.exception.ValidationException.class, () ->
            service.applyManualOverride(8L, true, null, 1L, "trace"));
    }

    @Test
    void applyManualOverrideClearSucceedsWithoutNotes() {
        PickupPoint pp = new PickupPoint();
        pp.setId(9L);
        pp.setManualOverride(true);
        pp.setOverrideNotes("Old note");

        when(ppRepo.findById(9L)).thenReturn(Optional.of(pp));
        doNothing().when(ppRepo).update(any());
        doNothing().when(auditService).record(any(), anyLong(), anyString(), anyLong(), any(), any(), any(), any());

        PickupPoint result = service.applyManualOverride(9L, false, null, 1L, "trace-clear");
        assertNotNull(result);
        assertFalse(result.isManualOverride());
    }

    @Test
    void createPickupPointFailsWithInvalidZip() {
        Community community = new Community();
        community.setId(100L);
        when(communityRepo.findById(100L)).thenReturn(Optional.of(community));

        assertThrows(ValidationException.class, () ->
            service.createPickupPoint(100L, "123 Main St", "INVALID_ZIP",
                "100", "199", "{}", 10, null));
    }

    @Test
    void createPickupPointFailsWithNegativeCapacity() {
        Community community = new Community();
        community.setId(100L);
        when(communityRepo.findById(100L)).thenReturn(Optional.of(community));

        assertThrows(ValidationException.class, () ->
            service.createPickupPoint(100L, "123 Main St", "12345",
                "100", "199", "{}", 0, null));
    }
}
