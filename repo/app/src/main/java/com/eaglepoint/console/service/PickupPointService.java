package com.eaglepoint.console.service;

import com.eaglepoint.console.config.SecurityConfig;
import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.model.PickupPoint;
import com.eaglepoint.console.model.Geozone;
import com.eaglepoint.console.repository.CommunityRepository;
import com.eaglepoint.console.repository.GeozoneRepository;
import com.eaglepoint.console.repository.PickupPointRepository;
import com.eaglepoint.console.security.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PickupPointService {
    private static final Logger log = LoggerFactory.getLogger(PickupPointService.class);
    private static final Pattern ZIP_PATTERN = Pattern.compile("^\\d{5}(-\\d{4})?$");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*(\\d+)\\b");

    private final PickupPointRepository ppRepo;
    private final CommunityRepository communityRepo;
    private final GeozoneRepository geozoneRepo;
    private final EncryptionUtil encryptionUtil;
    private final AuditService auditService;
    private final NotificationService notificationService;

    /** Legacy ctor — no geozone lookup; match() falls back to direct ZIP only. */
    public PickupPointService(PickupPointRepository ppRepo, CommunityRepository communityRepo,
                               SecurityConfig securityConfig, AuditService auditService) {
        this(ppRepo, communityRepo, null, securityConfig, auditService);
    }

    public PickupPointService(PickupPointRepository ppRepo, CommunityRepository communityRepo,
                               GeozoneRepository geozoneRepo,
                               SecurityConfig securityConfig, AuditService auditService) {
        this(ppRepo, communityRepo, geozoneRepo, securityConfig, auditService,
            NotificationService.getInstance());
    }

    public PickupPointService(PickupPointRepository ppRepo, CommunityRepository communityRepo,
                               GeozoneRepository geozoneRepo,
                               SecurityConfig securityConfig, AuditService auditService,
                               NotificationService notificationService) {
        this.ppRepo = ppRepo;
        this.communityRepo = communityRepo;
        this.geozoneRepo = geozoneRepo;
        this.encryptionUtil = new EncryptionUtil(securityConfig.getEncryptionKey());
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    public PickupPoint createPickupPoint(long communityId, String address, String zipCode,
                                          String streetStart, String streetEnd, String hoursJson,
                                          int capacity, Long geozoneId) {
        communityRepo.findById(communityId).orElseThrow(() -> new NotFoundException("Community", communityId));
        validateAddress(address);
        validateZipCode(zipCode);
        validateCapacity(capacity);
        validateHoursJson(hoursJson);

        if (ppRepo.countActiveByCommunity(communityId) > 0) {
            throw new ConflictException("Community already has an active pickup point");
        }

        PickupPoint pp = new PickupPoint();
        pp.setCommunityId(communityId);
        pp.setAddressEncrypted(encryptionUtil.encrypt(address));
        pp.setZipCode(zipCode);
        pp.setStreetRangeStart(streetStart);
        pp.setStreetRangeEnd(streetEnd);
        pp.setHoursJson(hoursJson);
        pp.setCapacity(capacity);
        pp.setStatus("ACTIVE");
        pp.setGeozoneId(geozoneId);

        long id = ppRepo.insert(pp);
        return ppRepo.findById(id).orElseThrow();
    }

    public PickupPoint getPickupPoint(long id) {
        return ppRepo.findById(id).orElseThrow(() -> new NotFoundException("PickupPoint", id));
    }

    public PagedResult<PickupPoint> listPickupPoints(int page, int pageSize) {
        return ppRepo.findAll(page, pageSize);
    }

    public PickupPoint updatePickupPoint(long id, String address, String zipCode, Integer capacity,
                                          String hoursJson, Long geozoneId) {
        PickupPoint pp = ppRepo.findById(id).orElseThrow(() -> new NotFoundException("PickupPoint", id));
        if (address != null) { validateAddress(address); pp.setAddressEncrypted(encryptionUtil.encrypt(address)); }
        if (zipCode != null) { validateZipCode(zipCode); pp.setZipCode(zipCode); }
        if (capacity != null) { validateCapacity(capacity); pp.setCapacity(capacity); }
        if (hoursJson != null) { validateHoursJson(hoursJson); pp.setHoursJson(hoursJson); }
        if (geozoneId != null) pp.setGeozoneId(geozoneId);
        ppRepo.update(pp);
        return ppRepo.findById(id).orElseThrow();
    }

    public void deletePickupPoint(long id) {
        if (ppRepo.findById(id).isEmpty()) throw new NotFoundException("PickupPoint", id);
        ppRepo.delete(id);
    }

    public PickupPoint pausePickupPoint(long id, String reason, String pausedUntil,
                                         long actingUserId, String traceId) {
        PickupPoint pp = ppRepo.findById(id).orElseThrow(() -> new NotFoundException("PickupPoint", id));
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ValidationException("reason", "Pause reason must be 1-500 characters");
        }
        if (pausedUntil != null && !pausedUntil.isBlank()) {
            try {
                Instant until = Instant.parse(pausedUntil);
                if (!until.isAfter(Instant.now())) {
                    throw new ValidationException("pausedUntil", "Pause until must be in the future");
                }
            } catch (Exception e) {
                if (e instanceof ValidationException) throw e;
                throw new ValidationException("pausedUntil", "pausedUntil must be a valid ISO-8601 datetime");
            }
        }
        if (!"ACTIVE".equals(pp.getStatus()) && !"PAUSED".equals(pp.getStatus())) {
            throw new ConflictException("Only ACTIVE or PAUSED pickup points can be paused");
        }
        pp.setStatus("PAUSED");
        pp.setPauseReason(reason);
        pp.setPausedUntil(pausedUntil);
        ppRepo.update(pp);
        auditService.record("PickupPoint", id, "PAUSE", actingUserId, traceId, null, null, reason);
        return ppRepo.findById(id).orElseThrow();
    }

    public PickupPoint resumePickupPoint(long id, long actingUserId, String traceId) {
        PickupPoint pp = ppRepo.findById(id).orElseThrow(() -> new NotFoundException("PickupPoint", id));
        if (!"PAUSED".equals(pp.getStatus())) {
            throw new ConflictException("Only PAUSED pickup points can be resumed");
        }
        // Check if activating this would violate the one-active-per-community rule
        if (ppRepo.countActiveByCommunity(pp.getCommunityId()) > 0) {
            throw new ConflictException("Another pickup point in this community is already ACTIVE");
        }
        pp.setStatus("ACTIVE");
        pp.setPausedUntil(null);
        pp.setPauseReason(null);
        ppRepo.update(pp);
        auditService.record("PickupPoint", id, "RESUME", actingUserId, traceId, null, null, null);
        return ppRepo.findById(id).orElseThrow();
    }

    /**
     * Resolve a pickup point for a resident address using the community + ZIP +
     * street-range rules.
     *
     * <ol>
     *   <li>Verify the community exists.</li>
     *   <li>Collect candidates from the community's <strong>active</strong>
     *       pickup points whose {@code zipCode} equals the request ZIP
     *       <em>or</em> whose attached geozone covers the request ZIP.</li>
     *   <li>If the caller supplied a street address whose leading house
     *       number falls inside a candidate's {@code streetRangeStart}..
     *       {@code streetRangeEnd} range, return that candidate.</li>
     *   <li>Otherwise return the first candidate (one-active-per-community
     *       keeps this list small).</li>
     * </ol>
     *
     * Throws {@link NotFoundException} if no candidate satisfies the rules.
     */
    public PickupPoint matchPickupPoint(String zipCode, String streetAddress, long communityId) {
        communityRepo.findById(communityId)
            .orElseThrow(() -> new NotFoundException("Community", communityId));

        List<PickupPoint> candidates = collectActiveCandidates(zipCode, communityId);
        if (candidates.isEmpty()) {
            throw new NotFoundException(
                "No active pickup point matches this community/ZIP combination");
        }

        Integer streetNumber = parseLeadingHouseNumber(streetAddress);
        if (streetNumber != null) {
            for (PickupPoint pp : candidates) {
                if (isInStreetRange(streetNumber, pp.getStreetRangeStart(), pp.getStreetRangeEnd())) {
                    return pp;
                }
            }
        }
        return candidates.get(0);
    }

    private List<PickupPoint> collectActiveCandidates(String zipCode, long communityId) {
        List<PickupPoint> out = new ArrayList<>();
        // 1. Direct ZIP match on the pickup point row itself.
        for (PickupPoint pp : ppRepo.findByZipCode(zipCode, communityId)) {
            if ("ACTIVE".equals(pp.getStatus())) out.add(pp);
        }
        // 2. Geozone-mediated match — any geozone covering this ZIP pulls in
        //    pickup points in this community whose geozoneId matches.
        if (geozoneRepo != null) {
            List<Geozone> zones = geozoneRepo.findByZipCode(zipCode);
            if (!zones.isEmpty()) {
                List<PickupPoint> inCommunity =
                    ppRepo.findByCommunity(communityId, 1, 500).getData();
                for (PickupPoint pp : inCommunity) {
                    if (!"ACTIVE".equals(pp.getStatus())) continue;
                    if (pp.getGeozoneId() == null) continue;
                    for (Geozone gz : zones) {
                        if (gz.getId() == pp.getGeozoneId()) {
                            if (out.stream().noneMatch(x -> x.getId() == pp.getId())) {
                                out.add(pp);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return out;
    }

    private Integer parseLeadingHouseNumber(String streetAddress) {
        if (streetAddress == null) return null;
        Matcher m = LEADING_NUMBER.matcher(streetAddress);
        if (!m.find()) return null;
        try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { return null; }
    }

    private boolean isInStreetRange(int number, String rangeStart, String rangeEnd) {
        try {
            if (rangeStart == null || rangeEnd == null) return false;
            int lo = Integer.parseInt(rangeStart.trim());
            int hi = Integer.parseInt(rangeEnd.trim());
            return number >= Math.min(lo, hi) && number <= Math.max(lo, hi);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Auto-resume pickup points whose {@code paused_until} timestamp is in
     * the past.  Must honour the same one-active-pickup-point-per-community
     * invariant that {@link #resumePickupPoint} enforces; otherwise a manual
     * re-activation after a service pause would be blocked while the
     * scheduled sweep quietly activates a second pickup point in the same
     * community.
     *
     * <p>If the community already has an active pickup point, we leave the
     * row paused, extend {@code pause_reason} with a conflict note, log a
     * WARN notification so ops can resolve it manually, and move on.</p>
     */
    public void resumeExpiredPauses() {
        String now = Instant.now().toString();
        List<PickupPoint> expired = ppRepo.findPausedExpired(now);
        for (PickupPoint pp : expired) {
            try {
                // Enforce the same invariant as the manual resume path.
                if (ppRepo.countActiveByCommunity(pp.getCommunityId()) > 0) {
                    log.warn("Skipping auto-resume for pickup point {} — community {} already has an active pickup point",
                        pp.getId(), pp.getCommunityId());
                    if (notificationService != null) {
                        notificationService.addAlert("WARN",
                            "Pickup point " + pp.getId() + " could not be auto-resumed: community already has an active pickup point",
                            "PickupPoint", pp.getId());
                    }
                    // Clear the pausedUntil so we don't re-alert every minute,
                    // and flag the conflict in pause_reason so operators can see it.
                    pp.setPausedUntil(null);
                    pp.setPauseReason("AUTO_RESUME_BLOCKED: community already has an active pickup point");
                    ppRepo.update(pp);
                    continue;
                }
                pp.setStatus("ACTIVE");
                pp.setPausedUntil(null);
                pp.setPauseReason(null);
                ppRepo.update(pp);
                log.info("Auto-resumed expired pause for pickup point {}", pp.getId());
            } catch (Exception e) {
                log.error("Failed to auto-resume pickup point {}: {}", pp.getId(), e.getMessage());
            }
        }
    }

    private void validateAddress(String address) {
        if (address == null || address.isBlank() || address.length() > 255) {
            throw new ValidationException("address", "Address must be 1-255 characters");
        }
    }

    private void validateZipCode(String zipCode) {
        if (zipCode == null || !ZIP_PATTERN.matcher(zipCode).matches()) {
            throw new ValidationException("zipCode", "ZIP code must match format 12345 or 12345-6789");
        }
    }

    private void validateCapacity(int capacity) {
        if (capacity < 1) {
            throw new ValidationException("capacity", "Capacity must be at least 1");
        }
    }

    private void validateHoursJson(String hoursJson) {
        if (hoursJson == null || hoursJson.isBlank()) {
            throw new ValidationException("hoursJson", "Hours JSON is required");
        }
    }
}
