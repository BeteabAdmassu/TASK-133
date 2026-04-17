package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.Review;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.repository.EvaluationRepository;
import com.eaglepoint.console.repository.UserRepository;
import com.eaglepoint.console.service.AuditService;
import com.eaglepoint.console.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceSecondReviewerTest {

    @Mock private EvaluationRepository evalRepo;
    @Mock private UserRepository userRepo;
    @Mock private AuditService auditService;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(evalRepo, userRepo, auditService);
    }

    @Test
    void rejectsSecondReviewerWithNonReviewerRole() {
        Review review = baseReview();
        when(evalRepo.findReviewById(100L)).thenReturn(Optional.of(review));

        User candidate = new User();
        candidate.setId(99L);
        candidate.setActive(true);
        candidate.setRole("OPS_MANAGER"); // not REVIEWER / SYSTEM_ADMIN
        when(userRepo.findById(99L)).thenReturn(Optional.of(candidate));

        ValidationException e = assertThrows(ValidationException.class,
            () -> service.assignSecondReviewer(100L, 1L, 99L));
        assertTrue(e.getMessage().contains("REVIEWER"));
    }

    @Test
    void rejectsDeactivatedSecondReviewer() {
        Review review = baseReview();
        when(evalRepo.findReviewById(100L)).thenReturn(Optional.of(review));

        User candidate = new User();
        candidate.setId(77L);
        candidate.setActive(false);
        candidate.setRole("REVIEWER");
        when(userRepo.findById(77L)).thenReturn(Optional.of(candidate));

        ValidationException e = assertThrows(ValidationException.class,
            () -> service.assignSecondReviewer(100L, 1L, 77L));
        assertTrue(e.getMessage().toLowerCase().contains("deactivat"));
    }

    @Test
    void rejectsSecondReviewerEqualToPrimary() {
        Review review = baseReview();
        when(evalRepo.findReviewById(100L)).thenReturn(Optional.of(review));

        assertThrows(ValidationException.class,
            () -> service.assignSecondReviewer(100L, 1L, review.getReviewerId()));
    }

    @Test
    void acceptsValidReviewerRole() {
        Review review = baseReview();
        when(evalRepo.findReviewById(100L)).thenReturn(Optional.of(review));

        User candidate = new User();
        candidate.setId(42L);
        candidate.setActive(true);
        candidate.setRole("REVIEWER");
        when(userRepo.findById(42L)).thenReturn(Optional.of(candidate));

        doNothing().when(evalRepo).updateReview(any());
        doNothing().when(auditService).record(any(), anyLong(), anyString(), anyLong(), any(), any(), any(), any());

        service.assignSecondReviewer(100L, 1L, 42L);

        verify(evalRepo).updateReview(argThat(r -> r.getSecondReviewerId() != null
            && r.getSecondReviewerId() == 42L));
    }

    @Test
    void acceptsSystemAdminAsSecondReviewer() {
        Review review = baseReview();
        when(evalRepo.findReviewById(100L)).thenReturn(Optional.of(review));

        User candidate = new User();
        candidate.setId(51L);
        candidate.setActive(true);
        candidate.setRole("SYSTEM_ADMIN");
        when(userRepo.findById(51L)).thenReturn(Optional.of(candidate));

        doNothing().when(evalRepo).updateReview(any());
        doNothing().when(auditService).record(any(), anyLong(), anyString(), anyLong(), any(), any(), any(), any());

        service.assignSecondReviewer(100L, 1L, 51L);

        verify(evalRepo).updateReview(any());
    }

    private Review baseReview() {
        Review r = new Review();
        r.setId(100L);
        r.setScorecardId(200L);
        r.setReviewerId(10L);
        r.setStatus("PENDING");
        return r;
    }
}
