package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.config.SecurityConfig;
import com.eaglepoint.console.model.PickupPoint;
import com.eaglepoint.console.repository.CommunityRepository;
import com.eaglepoint.console.repository.GeozoneRepository;
import com.eaglepoint.console.repository.PickupPointRepository;
import com.eaglepoint.console.service.AuditService;
import com.eaglepoint.console.service.NotificationService;
import com.eaglepoint.console.service.PickupPointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

/**
 * resumeExpiredPauses must honour the "one active pickup point per
 * community" invariant; otherwise the scheduled sweep will silently
 * conflict with manual re-activations.
 */
@ExtendWith(MockitoExtension.class)
class PickupPointAutoResumeTest {

    @Mock private PickupPointRepository ppRepo;
    @Mock private CommunityRepository communityRepo;
    @Mock private GeozoneRepository geozoneRepo;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;

    private PickupPointService service;

    @BeforeEach
    void setUp() {
        SecurityConfig.initHeadless();
        service = new PickupPointService(
            ppRepo, communityRepo, geozoneRepo,
            SecurityConfig.getInstance(), auditService, notificationService);
    }

    @Test
    void resumeExpiredPausesActivatesWhenCommunityHasNoActivePointToday() {
        PickupPoint pp = new PickupPoint();
        pp.setId(1L);
        pp.setCommunityId(42L);
        pp.setStatus("PAUSED");
        pp.setPausedUntil("2020-01-01T00:00:00Z");

        when(ppRepo.findPausedExpired(anyString())).thenReturn(List.of(pp));
        // Day-scoped check: no OTHER pickup point was active today
        when(ppRepo.countActiveOrActiveTodayByCommunityExcluding(eq(42L), anyString(), eq(1L))).thenReturn(0);

        service.resumeExpiredPauses();

        verify(ppRepo).update(argThat(updated ->
            "ACTIVE".equals(updated.getStatus())
                && updated.getPausedUntil() == null
                && updated.getPauseReason() == null
        ));
        verify(notificationService, never()).addAlert(any(), any(), any(), any());
    }

    @Test
    void resumeExpiredPausesSkipsWhenAnotherPickupPointWasActiveTodayInSameCommunity() {
        PickupPoint pp = new PickupPoint();
        pp.setId(2L);
        pp.setCommunityId(99L);
        pp.setStatus("PAUSED");
        pp.setPausedUntil("2020-01-01T00:00:00Z");

        when(ppRepo.findPausedExpired(anyString())).thenReturn(List.of(pp));
        // Day-scoped check: another pickup point in this community was active today
        when(ppRepo.countActiveOrActiveTodayByCommunityExcluding(eq(99L), anyString(), eq(2L))).thenReturn(1);

        service.resumeExpiredPauses();

        verify(ppRepo).update(argThat(updated ->
            !"ACTIVE".equals(updated.getStatus())
                && updated.getPauseReason() != null
                && updated.getPauseReason().contains("AUTO_RESUME_BLOCKED")
        ));
        verify(notificationService).addAlert(eq("WARN"), anyString(), eq("PickupPoint"), eq(2L));
    }
}
