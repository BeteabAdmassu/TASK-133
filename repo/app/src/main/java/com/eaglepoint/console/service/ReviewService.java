package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.ForbiddenException;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.model.Review;
import com.eaglepoint.console.repository.EvaluationRepository;

import java.time.Instant;

public class ReviewService {
    private final EvaluationRepository evalRepo;
    private final AuditService auditService;

    public ReviewService(EvaluationRepository evalRepo, AuditService auditService) {
        this.evalRepo = evalRepo;
        this.auditService = auditService;
    }

    public Review createReview(long scorecardId, long reviewerId) {
        evalRepo.findScorecardById(scorecardId)
            .orElseThrow(() -> new NotFoundException("Scorecard", scorecardId));
        Review r = new Review();
        r.setScorecardId(scorecardId);
        r.setReviewerId(reviewerId);
        r.setStatus("PENDING");
        long id = evalRepo.insertReview(r);
        auditService.record("Review", id, "CREATE", reviewerId, null, null, null, null);
        return evalRepo.findReviewById(id).orElseThrow();
    }

    public Review getReview(long id) {
        return evalRepo.findReviewById(id).orElseThrow(() -> new NotFoundException("Review", id));
    }

    public PagedResult<Review> listReviews(int page, int pageSize) {
        return evalRepo.findAllReviews(page, pageSize);
    }

    public Review approveReview(long reviewId, long actingUserId, String comments) {
        Review r = evalRepo.findReviewById(reviewId).orElseThrow(() -> new NotFoundException("Review", reviewId));
        if (!"PENDING".equals(r.getStatus()) && !"IN_REVIEW".equals(r.getStatus())) {
            throw new ConflictException("Review cannot be approved from status: " + r.getStatus());
        }
        assertCanActOnReview(r, actingUserId, "approve");
        r.setStatus("APPROVED");
        r.setReviewedAt(Instant.now().toString());
        r.setComments(comments);
        evalRepo.updateReview(r);
        // Approve the scorecard too
        evalRepo.updateScorecardStatus(r.getScorecardId(), "APPROVED", null);
        auditService.record("Review", reviewId, "APPROVE", actingUserId, null, null, null, comments);
        return evalRepo.findReviewById(reviewId).orElseThrow();
    }

    public Review rejectReview(long reviewId, long actingUserId, String comments) {
        Review r = evalRepo.findReviewById(reviewId).orElseThrow(() -> new NotFoundException("Review", reviewId));
        if (!"PENDING".equals(r.getStatus()) && !"IN_REVIEW".equals(r.getStatus())) {
            throw new ConflictException("Review cannot be rejected from status: " + r.getStatus());
        }
        assertCanActOnReview(r, actingUserId, "reject");
        if (comments == null || comments.isBlank()) {
            throw new ValidationException("comments", "Comments are required when rejecting a review");
        }
        r.setStatus("REJECTED");
        r.setReviewedAt(Instant.now().toString());
        r.setComments(comments);
        evalRepo.updateReview(r);
        auditService.record("Review", reviewId, "REJECT", actingUserId, null, null, null, comments);
        return evalRepo.findReviewById(reviewId).orElseThrow();
    }

    /**
     * Object-level authorisation for review approve/reject actions.
     *
     * <p>Rules:
     * <ul>
     *   <li>When a conflict has been flagged on the review, only the
     *       <em>second</em> reviewer may approve/reject — the original
     *       reviewer has recused themselves and must not be able to
     *       rubber-stamp the scorecard.</li>
     *   <li>Otherwise, the action is allowed for either the assigned
     *       reviewer or (if one has been assigned) the second reviewer.</li>
     * </ul>
     */
    private void assertCanActOnReview(Review r, long actingUserId, String action) {
        boolean isPrimary = r.getReviewerId() == actingUserId;
        boolean isSecond  = r.getSecondReviewerId() != null && r.getSecondReviewerId() == actingUserId;

        if (r.isConflictFlagged()) {
            if (r.getSecondReviewerId() == null) {
                throw new ConflictException(
                    "A second reviewer must be assigned before this review can be " + action + "d");
            }
            if (!isSecond) {
                throw new ForbiddenException(
                    "Only the assigned second reviewer may " + action + " this re-review");
            }
            return;
        }

        if (!isPrimary && !isSecond) {
            throw new ForbiddenException(
                "Only the assigned reviewer can " + action + " this review");
        }
    }

    public Review flagConflict(long reviewId, long actingUserId, String reason) {
        Review r = evalRepo.findReviewById(reviewId).orElseThrow(() -> new NotFoundException("Review", reviewId));
        if (r.getReviewerId() != actingUserId) {
            throw new ForbiddenException("Only the assigned reviewer can flag a conflict");
        }
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new ValidationException("reason", "Recusal reason must be 1-1000 characters");
        }
        r.setConflictFlagged(true);
        r.setRecusalReason(reason);
        r.setRecusedAt(Instant.now().toString());
        r.setStatus("RECUSED");
        evalRepo.updateReview(r);
        evalRepo.updateScorecardStatus(r.getScorecardId(), "RECUSED", null);
        auditService.record("Review", reviewId, "FLAG_CONFLICT", actingUserId, null, null, null, reason);
        return evalRepo.findReviewById(reviewId).orElseThrow();
    }

    public Review assignSecondReviewer(long reviewId, long actingUserId, long secondReviewerId) {
        Review r = evalRepo.findReviewById(reviewId).orElseThrow(() -> new NotFoundException("Review", reviewId));
        if (secondReviewerId == r.getReviewerId()) {
            throw new ValidationException("reviewerId", "Second reviewer cannot be the same as the original reviewer");
        }
        r.setSecondReviewerId(secondReviewerId);
        r.setStatus("IN_REVIEW");
        evalRepo.updateReview(r);
        auditService.record("Review", reviewId, "ASSIGN_SECOND", actingUserId, null, null, null, null);
        return evalRepo.findReviewById(reviewId).orElseThrow();
    }
}
