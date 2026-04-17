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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Metric-weight exact-total rule: at scorecard creation (template usage)
 * the sum of metric weights must equal 100.0 — 99.9 and 100.1 are both
 * rejected.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationWeightRuleTest {

    @Mock private EvaluationRepository evalRepo;
    @Mock private AuditService auditService;

    private EvaluationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(evalRepo, auditService);
    }

    @Test
    void createScorecardRejectsTemplateWithWeightsBelow100() {
        mockTemplateAndCycle(1L, 10L, 99.9);
        assertThrows(ValidationException.class,
            () -> service.createScorecard(1L, 10L, 20L, 30L));
    }

    @Test
    void createScorecardRejectsTemplateWithWeightsAbove100() {
        mockTemplateAndCycle(1L, 10L, 100.1);
        assertThrows(ValidationException.class,
            () -> service.createScorecard(1L, 10L, 20L, 30L));
    }

    @Test
    void createScorecardAcceptsExact100() {
        mockTemplateAndCycle(1L, 10L, 100.0);
        when(evalRepo.insertScorecard(any())).thenReturn(999L);
        when(evalRepo.findScorecardById(999L)).thenReturn(Optional.of(new com.eaglepoint.console.model.Scorecard()));

        assertNotNull(service.createScorecard(1L, 10L, 20L, 30L));
    }

    @Test
    void assertTemplateWeightsFinalizedRejectsEmptyMetrics() {
        ScorecardTemplate t = new ScorecardTemplate();
        t.setId(99L);
        when(evalRepo.findTemplateById(99L)).thenReturn(Optional.of(t));
        when(evalRepo.findMetricsByTemplate(99L)).thenReturn(List.of());

        assertThrows(ValidationException.class,
            () -> service.assertTemplateWeightsFinalized(99L));
    }

    private void mockTemplateAndCycle(long cycleId, long templateId, double weightSum) {
        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setId(cycleId);
        cycle.setStatus("ACTIVE");

        ScorecardTemplate template = new ScorecardTemplate();
        template.setId(templateId);
        template.setType("PEER");

        ScorecardMetric metric = new ScorecardMetric();
        metric.setId(1L);
        metric.setTemplateId(templateId);
        metric.setWeight(weightSum);

        when(evalRepo.findCycleById(cycleId)).thenReturn(Optional.of(cycle));
        when(evalRepo.findTemplateById(templateId)).thenReturn(Optional.of(template));
        when(evalRepo.findMetricsByTemplate(templateId)).thenReturn(List.of(metric));
        when(evalRepo.sumWeightsByTemplate(templateId)).thenReturn(weightSum);
    }
}
