package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.ForbiddenException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.Appeal;
import com.eaglepoint.console.model.Scorecard;
import com.eaglepoint.console.repository.EvaluationRepository;
import com.eaglepoint.console.service.AppealService;
import com.eaglepoint.console.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppealServiceTest {

    @Mock private EvaluationRepository evalRepo;
    @Mock private AuditService auditService;

    private AppealService service;

    @BeforeEach
    void setUp() {
        service = new AppealService(evalRepo, auditService);
    }

    @Test
    void fileAppealSucceedsWithinDeadline() {
        Scorecard sc = new Scorecard();
        sc.setId(1L);
        sc.setEvaluateeId(10L);
        sc.setStatus("SUBMITTED");
        sc.setSubmittedAt(Instant.now().minus(5, ChronoUnit.DAYS).toString());

        Appeal appeal = new Appeal();
        appeal.setId(1L);

        when(evalRepo.findScorecardById(1L)).thenReturn(Optional.of(sc));
        when(evalRepo.findOpenAppealsByScorecard(1L)).thenReturn(List.of());
        when(evalRepo.insertAppeal(any())).thenReturn(1L);
        when(evalRepo.findAppealById(1L)).thenReturn(Optional.of(appeal));
        doNothing().when(evalRepo).updateScorecardStatus(anyLong(), anyString(), any());
        doNothing().when(auditService).record(any(), anyLong(), anyString(), anyLong(), any(), any(), any(), any());

        Appeal result = service.fileAppeal(1L, 10L, "I disagree with the score because it lacks detail.");
        assertNotNull(result);
    }

    @Test
    void fileAppealFailsAfterDeadline() {
        Scorecard sc = new Scorecard();
        sc.setId(1L);
        sc.setEvaluateeId(10L);
        sc.setStatus("SUBMITTED");
        sc.setSubmittedAt(Instant.now().minus(10, ChronoUnit.DAYS).toString());

        when(evalRepo.findScorecardById(1L)).thenReturn(Optional.of(sc));

        assertThrows(ValidationException.class, () ->
            service.fileAppeal(1L, 10L, "Too late to appeal now"));
    }

    @Test
    void fileAppealFailsWhenNotEvaluatee() {
        Scorecard sc = new Scorecard();
        sc.setId(1L);
        sc.setEvaluateeId(10L);
        sc.setStatus("SUBMITTED");
        sc.setSubmittedAt(Instant.now().minus(1, ChronoUnit.DAYS).toString());

        when(evalRepo.findScorecardById(1L)).thenReturn(Optional.of(sc));

        assertThrows(ForbiddenException.class, () ->
            service.fileAppeal(1L, 99L, "I disagree with this evaluation result."));
    }

    @Test
    void fileAppealFailsForDraftScorecard() {
        Scorecard sc = new Scorecard();
        sc.setId(1L);
        sc.setEvaluateeId(10L);
        sc.setStatus("DRAFT");
        sc.setSubmittedAt(Instant.now().minus(1, ChronoUnit.DAYS).toString());

        when(evalRepo.findScorecardById(1L)).thenReturn(Optional.of(sc));

        assertThrows(ConflictException.class, () ->
            service.fileAppeal(1L, 10L, "This reason has more than ten chars."));
    }

    @Test
    void fileAppealFailsWhenAlreadyAppealed() {
        Scorecard sc = new Scorecard();
        sc.setId(1L);
        sc.setEvaluateeId(10L);
        sc.setStatus("SUBMITTED");
        sc.setSubmittedAt(Instant.now().minus(1, ChronoUnit.DAYS).toString());

        Appeal existing = new Appeal();
        existing.setId(99L);

        when(evalRepo.findScorecardById(1L)).thenReturn(Optional.of(sc));
        when(evalRepo.findOpenAppealsByScorecard(1L)).thenReturn(List.of(existing));

        assertThrows(ConflictException.class, () ->
            service.fileAppeal(1L, 10L, "Duplicate appeal reason here."));
    }
}
