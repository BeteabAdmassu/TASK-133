package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.config.SecurityConfig;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.model.Community;
import com.eaglepoint.console.model.PickupPoint;
import com.eaglepoint.console.repository.CommunityRepository;
import com.eaglepoint.console.repository.PickupPointRepository;
import com.eaglepoint.console.service.AuditService;
import com.eaglepoint.console.service.PickupPointService;
import com.eaglepoint.console.service.PickupPointService.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for override-aware pickup-point matching logic.
 *
 * These tests operate at the service layer so they can inject multiple
 * override candidates — something the one-active-per-day integration
 * constraint prevents at the HTTP level.
 */
@ExtendWith(MockitoExtension.class)
class PickupPointOverrideMatchServiceTest {

    @Mock private PickupPointRepository ppRepo;
    @Mock private CommunityRepository communityRepo;
    @Mock private AuditService auditService;

    private PickupPointService service;

    @BeforeEach
    void setUp() {
        SecurityConfig.initHeadless();
        service = new PickupPointService(ppRepo, communityRepo, SecurityConfig.getInstance(), auditService);
    }

    private Community community(long id) {
        Community c = new Community();
        c.setId(id);
        return c;
    }

    private PickupPoint activeOverride(long id, long communityId) {
        PickupPoint pp = new PickupPoint();
        pp.setId(id);
        pp.setCommunityId(communityId);
        pp.setStatus("ACTIVE");
        pp.setManualOverride(true);
        pp.setOverrideNotes("Test override");
        pp.setZipCode("99999");
        return pp;
    }

    private PickupPoint activeNormal(long id, long communityId, String zipCode) {
        PickupPoint pp = new PickupPoint();
        pp.setId(id);
        pp.setCommunityId(communityId);
        pp.setStatus("ACTIVE");
        pp.setManualOverride(false);
        pp.setZipCode(zipCode);
        return pp;
    }

    // ─── Test 1: override selected before normal path ─────────────────────────

    @Test
    void overrideCandidateSelectedWhenPresent() {
        long communityId = 1L;
        PickupPoint overridePp = activeOverride(10L, communityId);

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community(communityId)));
        when(ppRepo.findActiveOverridesByCommunity(communityId)).thenReturn(List.of(overridePp));

        MatchResult result = service.matchPickupPoint("12345", "100 Any St", communityId);

        assertTrue(result.matchedViaOverride, "Must report override-driven match");
        assertEquals(10L, result.pickupPoint.getId());
        // Normal matching methods must NOT be called when override fires
        verify(ppRepo, never()).findByZipCode(anyString(), anyLong());
    }

    // ─── Test 2: PAUSED override not eligible ─────────────────────────────────

    @Test
    void pausedOverrideNotEligible_fallsBackToNormalMatch() {
        long communityId = 2L;
        PickupPoint normalPp = activeNormal(20L, communityId, "54321");

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community(communityId)));
        // No ACTIVE override candidates (paused point excluded by SQL query)
        when(ppRepo.findActiveOverridesByCommunity(communityId)).thenReturn(List.of());
        // Normal matching via ZIP
        when(ppRepo.findByZipCode("54321", communityId)).thenReturn(List.of(normalPp));

        MatchResult result = service.matchPickupPoint("54321", "150 Normal St", communityId);

        assertFalse(result.matchedViaOverride, "Should not be an override-driven match");
        assertEquals(20L, result.pickupPoint.getId());
    }

    @Test
    void noCandidatesAndNoOverrideThrowsNotFound() {
        long communityId = 3L;
        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community(communityId)));
        when(ppRepo.findActiveOverridesByCommunity(communityId)).thenReturn(List.of());
        when(ppRepo.findByZipCode(anyString(), eq(communityId))).thenReturn(List.of());

        assertThrows(NotFoundException.class, () ->
            service.matchPickupPoint("99999", "any", communityId));
    }

    // ─── Test 3: multiple override candidates → lowest id wins ────────────────

    @Test
    void multipleOverrideCandidatesLowestIdWins() {
        long communityId = 4L;
        PickupPoint lower = activeOverride(5L, communityId);
        PickupPoint higher = activeOverride(9L, communityId);

        // Repository returns ordered by id ASC (as per SQL in findActiveOverridesByCommunity)
        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community(communityId)));
        when(ppRepo.findActiveOverridesByCommunity(communityId)).thenReturn(List.of(lower, higher));

        MatchResult result = service.matchPickupPoint("any", "any address", communityId);

        assertTrue(result.matchedViaOverride);
        assertEquals(5L, result.pickupPoint.getId(),
            "Lowest id (5) must win over id 9 — deterministic tie-break");
        verify(ppRepo, never()).findByZipCode(anyString(), anyLong());
    }

    @Test
    void multipleOverrideCandidatesHigherIdNotReturned() {
        long communityId = 5L;
        PickupPoint lower = activeOverride(3L, communityId);
        PickupPoint higher = activeOverride(7L, communityId);

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community(communityId)));
        when(ppRepo.findActiveOverridesByCommunity(communityId)).thenReturn(List.of(lower, higher));

        MatchResult result = service.matchPickupPoint("zip", "addr", communityId);

        assertNotEquals(7L, result.pickupPoint.getId(),
            "Higher id (7) must never win when lower id (3) exists");
        assertEquals(3L, result.pickupPoint.getId());
    }

    // ─── Test 5: response structure (matchedViaOverride always present) ───────

    @Test
    void normalMatchSetsMatchedViaOverrideFalse() {
        long communityId = 6L;
        PickupPoint pp = activeNormal(30L, communityId, "11111");

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community(communityId)));
        when(ppRepo.findActiveOverridesByCommunity(communityId)).thenReturn(List.of());
        when(ppRepo.findByZipCode("11111", communityId)).thenReturn(List.of(pp));

        MatchResult result = service.matchPickupPoint("11111", "100 Normal Ave", communityId);

        assertFalse(result.matchedViaOverride, "Normal match must report matchedViaOverride=false");
        assertNotNull(result.pickupPoint);
        assertEquals(30L, result.pickupPoint.getId());
    }

    @Test
    void overrideMatchSetsMatchedViaOverrideTrue() {
        long communityId = 7L;
        PickupPoint pp = activeOverride(40L, communityId);

        when(communityRepo.findById(communityId)).thenReturn(Optional.of(community(communityId)));
        when(ppRepo.findActiveOverridesByCommunity(communityId)).thenReturn(List.of(pp));

        MatchResult result = service.matchPickupPoint("12345", "any", communityId);

        assertTrue(result.matchedViaOverride, "Override match must report matchedViaOverride=true");
        assertEquals(40L, result.pickupPoint.getId());
    }
}
