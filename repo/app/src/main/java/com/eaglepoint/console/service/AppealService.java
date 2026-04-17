package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.ForbiddenException;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.Appeal;
import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.model.Scorecard;
import com.eaglepoint.console.repository.EvaluationRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class AppealService {
    private final EvaluationRepository evalRepo;
    private final AuditService auditService;

    public AppealService(EvaluationRepository evalRepo, AuditService auditService) {
        this.evalRepo = evalRepo;
        this.auditService = auditService;
    }

    public Appeal fileAppeal(long scorecardId, long filedBy, String reason) {
        Scorecard s = evalRepo.findScorecardById(scorecardId)
            .orElseThrow(() -> new NotFoundException("Scorecard", scorecardId));

        // Only evaluatee can file
        if (s.getEvaluateeId() != filedBy) {
            throw new ForbiddenException("Only the evaluatee can file an appeal");
        }
        // Only against SUBMITTED or APPROVED scorecards
        if (!"SUBMITTED".equals(s.getStatus()) && !"APPROVED".equals(s.getStatus())) {
            throw new ConflictException("Appeals can only be filed against SUBMITTED or APPROVED scorecards");
        }
        // Check deadline: within 7 days of submission
        if (s.getSubmittedAt() != null) {
            Instant deadline = Instant.parse(s.getSubmittedAt()).plus(7, ChronoUnit.DAYS);
            if (Instant.now().isAfter(deadline)) {
                throw new ValidationException("deadline", "Appeal deadline has passed. Appeals must be filed within 7 calendar days of submission.");
            }
        }
        // No duplicate open appeals
        List<Appeal> existing = evalRepo.findOpenAppealsByScorecard(scorecardId);
        if (!existing.isEmpty()) {
            throw new ConflictException("An open appeal already exists for this scorecard");
        }
        if (reason == null || reason.length() < 10 || reason.length() > 2000) {
            throw new ValidationException("reason", "Appeal reason must be 10-2000 characters");
        }
        String now = Instant.now().toString();
        String deadline = Instant.now().plus(7, ChronoUnit.DAYS).toString();

        Appeal a = new Appeal();
        a.setScorecardId(scorecardId);
        a.setFiledBy(filedBy);
        a.setDeadline(deadline);
        a.setReason(reason);
        a.setStatus("PENDING");

        long id = evalRepo.insertAppeal(a);
        evalRepo.updateScorecardStatus(scorecardId, "APPEALED", s.getSubmittedAt());
        auditService.record("Appeal", id, "FILE", filedBy, null, null, null, reason);
        return evalRepo.findAppealById(id).orElseThrow();
    }

    public Appeal resolveAppeal(long appealId, long actingUserId, String resolutionNotes) {
        Appeal a = evalRepo.findAppealById(appealId).orElseThrow(() -> new NotFoundException("Appeal", appealId));
        if (!"PENDING".equals(a.getStatus()) && !"UNDER_REVIEW".equals(a.getStatus())) {
            throw new ConflictException("Appeal cannot be resolved from status: " + a.getStatus());
        }
        if (resolutionNotes == null || resolutionNotes.isBlank()) {
            throw new ValidationException("resolutionNotes", "Resolution notes are required");
        }
        a.setStatus("RESOLVED");
        a.setResolvedAt(Instant.now().toString());
        a.setResolutionNotes(resolutionNotes);
        evalRepo.updateAppeal(a);
        auditService.record("Appeal", appealId, "RESOLVE", actingUserId, null, null, null, resolutionNotes);
        return evalRepo.findAppealById(appealId).orElseThrow();
    }

    public Appeal rejectAppeal(long appealId, long actingUserId, String resolutionNotes) {
        Appeal a = evalRepo.findAppealById(appealId).orElseThrow(() -> new NotFoundException("Appeal", appealId));
        if (!"PENDING".equals(a.getStatus()) && !"UNDER_REVIEW".equals(a.getStatus())) {
            throw new ConflictException("Appeal cannot be rejected from status: " + a.getStatus());
        }
        if (resolutionNotes == null || resolutionNotes.isBlank()) {
            throw new ValidationException("resolutionNotes", "Resolution notes are required");
        }
        a.setStatus("REJECTED");
        a.setResolvedAt(Instant.now().toString());
        a.setResolutionNotes(resolutionNotes);
        evalRepo.updateAppeal(a);
        auditService.record("Appeal", appealId, "REJECT", actingUserId, null, null, null, resolutionNotes);
        return evalRepo.findAppealById(appealId).orElseThrow();
    }

    public PagedResult<Appeal> listAppeals(int page, int pageSize) {
        return evalRepo.findAllAppeals(page, pageSize);
    }

    public Appeal getAppeal(long id) {
        return evalRepo.findAppealById(id).orElseThrow(() -> new NotFoundException("Appeal", id));
    }
}
