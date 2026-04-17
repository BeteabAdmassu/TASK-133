package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.EvaluationCycle;
import com.eaglepoint.console.model.ScorecardMetric;
import com.eaglepoint.console.model.ScorecardTemplate;
import com.eaglepoint.console.repository.EvaluationRepository;
import com.eaglepoint.console.service.AuditService;
import com.eaglepoint.console.service.EvaluationService;
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
class EvaluationServiceTest {

    @Mock private EvaluationRepository evalRepo;
    @Mock private AuditService auditService;

    private EvaluationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(evalRepo, auditService);
    }

    @Test
    void createCycleSucceeds() {
        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setId(1L);
        cycle.setName("Cycle 2024 Q1");

        when(evalRepo.insertCycle(any(EvaluationCycle.class))).thenReturn(1L);
        when(evalRepo.findCycleById(1L)).thenReturn(Optional.of(cycle));

        EvaluationCycle result = service.createCycle("Cycle 2024 Q1", "2024-01-01", "2024-03-31", 1L);
        assertNotNull(result);
        assertEquals("Cycle 2024 Q1", result.getName());
    }

    @Test
    void createCycleFailsForInvalidName() {
        assertThrows(ValidationException.class, () ->
            service.createCycle("", "2024-01-01", "2024-03-31", 1L));
    }

    @Test
    void createCycleFailsForInvalidDates() {
        assertThrows(ValidationException.class, () ->
            service.createCycle("Valid Name", "2024-06-30", "2024-01-01", 1L));
    }

    @Test
    void addMetricFailsWhenWeightExceedsLimit() {
        ScorecardTemplate template = new ScorecardTemplate();
        template.setId(1L);

        when(evalRepo.findTemplateById(1L)).thenReturn(Optional.of(template));
        when(evalRepo.sumWeightsByTemplate(1L)).thenReturn(90.0);

        // Adding weight=15 would push total to 105, exceeding 100
        assertThrows(ValidationException.class, () ->
            service.addMetric(1L, "New Metric", 15.0, 100.0, "desc"));
    }

    @Test
    void addMetricSucceedsWhenWeightAtLimit() {
        ScorecardTemplate template = new ScorecardTemplate();
        template.setId(1L);

        ScorecardMetric metric = new ScorecardMetric();
        metric.setId(1L);

        when(evalRepo.findTemplateById(1L)).thenReturn(Optional.of(template));
        when(evalRepo.sumWeightsByTemplate(1L)).thenReturn(90.0);
        when(evalRepo.insertMetric(any(ScorecardMetric.class))).thenReturn(1L);
        when(evalRepo.findMetricById(1L)).thenReturn(Optional.of(metric));

        // Adding weight=10 brings total to 100 — exactly at limit
        assertDoesNotThrow(() -> service.addMetric(1L, "New Metric", 10.0, 100.0, "desc"));
    }

    @Test
    void addMetricFailsForInvalidWeight() {
        ScorecardTemplate template = new ScorecardTemplate();
        template.setId(1L);
        when(evalRepo.findTemplateById(1L)).thenReturn(Optional.of(template));

        assertThrows(ValidationException.class, () ->
            service.addMetric(1L, "Metric", 0.0, 100.0, "desc"));
    }
}
