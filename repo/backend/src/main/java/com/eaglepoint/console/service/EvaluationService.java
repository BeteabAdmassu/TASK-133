package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.*;
import com.eaglepoint.console.repository.EvaluationRepository;

import java.time.Instant;
import java.util.List;

public class EvaluationService {
    private final EvaluationRepository evalRepo;
    private final AuditService auditService;

    public EvaluationService(EvaluationRepository evalRepo, AuditService auditService) {
        this.evalRepo = evalRepo;
        this.auditService = auditService;
    }

    // ─── EvaluationCycle ───

    public EvaluationCycle createCycle(String name, String startDate, String endDate, long createdBy) {
        validateCycleName(name);
        validateDates(startDate, endDate);
        EvaluationCycle c = new EvaluationCycle();
        c.setName(name.trim());
        c.setStartDate(startDate);
        c.setEndDate(endDate);
        c.setStatus("DRAFT");
        c.setCreatedBy(createdBy);
        long id = evalRepo.insertCycle(c);
        return evalRepo.findCycleById(id).orElseThrow();
    }

    public EvaluationCycle getCycle(long id) {
        return evalRepo.findCycleById(id).orElseThrow(() -> new NotFoundException("EvaluationCycle", id));
    }

    public PagedResult<EvaluationCycle> listCycles(int page, int pageSize) {
        return evalRepo.findAllCycles(page, pageSize);
    }

    public EvaluationCycle updateCycle(long id, String name, String startDate, String endDate) {
        EvaluationCycle c = evalRepo.findCycleById(id).orElseThrow(() -> new NotFoundException("EvaluationCycle", id));
        if (!"DRAFT".equals(c.getStatus())) {
            throw new ConflictException("Only DRAFT cycles can be updated");
        }
        if (name != null) { validateCycleName(name); c.setName(name.trim()); }
        if (startDate != null) c.setStartDate(startDate);
        if (endDate != null) c.setEndDate(endDate);
        if (startDate != null || endDate != null) validateDates(c.getStartDate(), c.getEndDate());
        evalRepo.updateCycle(c);
        return evalRepo.findCycleById(id).orElseThrow();
    }

    public EvaluationCycle activateCycle(long id) {
        EvaluationCycle c = evalRepo.findCycleById(id).orElseThrow(() -> new NotFoundException("EvaluationCycle", id));
        if (!"DRAFT".equals(c.getStatus())) throw new ConflictException("Only DRAFT cycles can be activated");
        c.setStatus("ACTIVE");
        evalRepo.updateCycle(c);
        auditService.record("EvaluationCycle", id, "ACTIVATE", 0, null, null, null, null);
        return evalRepo.findCycleById(id).orElseThrow();
    }

    public EvaluationCycle closeCycle(long id) {
        EvaluationCycle c = evalRepo.findCycleById(id).orElseThrow(() -> new NotFoundException("EvaluationCycle", id));
        if (!"ACTIVE".equals(c.getStatus())) throw new ConflictException("Only ACTIVE cycles can be closed");
        c.setStatus("CLOSED");
        evalRepo.updateCycle(c);
        auditService.record("EvaluationCycle", id, "CLOSE", 0, null, null, null, null);
        return evalRepo.findCycleById(id).orElseThrow();
    }

    // ─── ScorecardTemplate ───

    public ScorecardTemplate createTemplate(long cycleId, String name, String type) {
        evalRepo.findCycleById(cycleId).orElseThrow(() -> new NotFoundException("EvaluationCycle", cycleId));
        if (name == null || name.isBlank()) throw new ValidationException("name", "Template name is required");
        if (!List.of("SELF", "PEER", "EXPERT").contains(type)) {
            throw new ValidationException("type", "Type must be SELF, PEER, or EXPERT");
        }
        ScorecardTemplate t = new ScorecardTemplate();
        t.setCycleId(cycleId);
        t.setName(name.trim());
        t.setType(type);
        long id = evalRepo.insertTemplate(t);
        return evalRepo.findTemplateById(id).orElseThrow();
    }

    public List<ScorecardTemplate> listTemplates(long cycleId) {
        return evalRepo.findTemplatesByCycle(cycleId);
    }

    // ─── ScorecardMetric ───

    public ScorecardMetric addMetric(long templateId, String name, double weight, double maxScore, String description) {
        evalRepo.findTemplateById(templateId).orElseThrow(() -> new NotFoundException("ScorecardTemplate", templateId));
        if (name == null || name.isBlank()) throw new ValidationException("name", "Metric name is required");
        if (weight <= 0) throw new ValidationException("weight", "Weight must be > 0");
        if (maxScore <= 0) throw new ValidationException("maxScore", "Max score must be > 0");
        double currentSum = evalRepo.sumWeightsByTemplate(templateId);
        if (currentSum + weight > 100.0 + 0.001) {
            throw new ValidationException("weight", "Total weights would exceed 100.0");
        }
        ScorecardMetric m = new ScorecardMetric();
        m.setTemplateId(templateId);
        m.setName(name.trim());
        m.setWeight(weight);
        m.setMaxScore(maxScore);
        m.setDescription(description);
        long id = evalRepo.insertMetric(m);
        return evalRepo.findMetricById(id).orElseThrow();
    }

    public ScorecardMetric updateMetric(long templateId, long metricId, String name, Double weight, Double maxScore) {
        ScorecardMetric m = evalRepo.findMetricById(metricId)
            .orElseThrow(() -> new NotFoundException("ScorecardMetric", metricId));
        if (m.getTemplateId() != templateId) throw new NotFoundException("Metric not found in template");
        if (name != null) m.setName(name.trim());
        if (weight != null) {
            if (weight <= 0) throw new ValidationException("weight", "Weight must be > 0");
            double currentSum = evalRepo.sumWeightsByTemplate(templateId) - m.getWeight();
            if (currentSum + weight > 100.0 + 0.001) {
                throw new ValidationException("weight", "Total weights would exceed 100.0");
            }
            m.setWeight(weight);
        }
        if (maxScore != null) {
            if (maxScore <= 0) throw new ValidationException("maxScore", "Max score must be > 0");
            m.setMaxScore(maxScore);
        }
        evalRepo.updateMetric(m);
        return evalRepo.findMetricById(metricId).orElseThrow();
    }

    public void deleteMetric(long templateId, long metricId) {
        ScorecardMetric m = evalRepo.findMetricById(metricId)
            .orElseThrow(() -> new NotFoundException("ScorecardMetric", metricId));
        if (m.getTemplateId() != templateId) throw new NotFoundException("Metric not found in template");
        evalRepo.deleteMetric(metricId);
    }

    // ─── Scorecard ───

    public Scorecard createScorecard(long cycleId, long templateId, long evaluateeId, long evaluatorId) {
        EvaluationCycle cycle = evalRepo.findCycleById(cycleId)
            .orElseThrow(() -> new NotFoundException("EvaluationCycle", cycleId));
        ScorecardTemplate template = evalRepo.findTemplateById(templateId)
            .orElseThrow(() -> new NotFoundException("ScorecardTemplate", templateId));
        Scorecard s = new Scorecard();
        s.setCycleId(cycleId);
        s.setTemplateId(templateId);
        s.setEvaluateeId(evaluateeId);
        s.setEvaluatorId(evaluatorId);
        s.setType(template.getType());
        s.setStatus("PENDING");
        long id = evalRepo.insertScorecard(s);
        return evalRepo.findScorecardById(id).orElseThrow();
    }

    public Scorecard getScorecard(long id) {
        return evalRepo.findScorecardById(id).orElseThrow(() -> new NotFoundException("Scorecard", id));
    }

    public PagedResult<Scorecard> listScorecards(int page, int pageSize) {
        return evalRepo.findAllScorecards(page, pageSize);
    }

    public static class ResponseInput {
        public long metricId;
        public double score;
        public String comments;
    }

    public Scorecard saveResponses(long scorecardId, long actingUserId, List<ResponseInput> responses) {
        Scorecard s = evalRepo.findScorecardById(scorecardId)
            .orElseThrow(() -> new NotFoundException("Scorecard", scorecardId));
        if (!"PENDING".equals(s.getStatus()) && !"IN_PROGRESS".equals(s.getStatus())) {
            throw new ConflictException("Cannot save responses for a scorecard in status: " + s.getStatus());
        }
        List<ScorecardMetric> metrics = evalRepo.findMetricsByTemplate(s.getTemplateId());
        for (ResponseInput ri : responses) {
            ScorecardMetric metric = metrics.stream()
                .filter(m -> m.getId() == ri.metricId)
                .findFirst()
                .orElseThrow(() -> new ValidationException("responses", "Invalid metric ID: " + ri.metricId));
            if (ri.score < 0 || ri.score > metric.getMaxScore()) {
                throw new ValidationException("score", "Score must be between 0 and " + metric.getMaxScore());
            }
            ScorecardResponse r = new ScorecardResponse();
            r.setScorecardId(scorecardId);
            r.setMetricId(ri.metricId);
            r.setScore(ri.score);
            r.setComments(ri.comments);
            evalRepo.upsertResponse(r);
        }
        if ("PENDING".equals(s.getStatus())) {
            evalRepo.updateScorecardStatus(scorecardId, "IN_PROGRESS", null);
        }
        return evalRepo.findScorecardById(scorecardId).orElseThrow();
    }

    public Scorecard submitScorecard(long scorecardId, long actingUserId) {
        Scorecard s = evalRepo.findScorecardById(scorecardId)
            .orElseThrow(() -> new NotFoundException("Scorecard", scorecardId));
        if (s.getEvaluatorId() != actingUserId) {
            throw new com.eaglepoint.console.exception.ForbiddenException("Only the assigned evaluator can submit this scorecard");
        }
        if (!"IN_PROGRESS".equals(s.getStatus()) && !"PENDING".equals(s.getStatus())) {
            throw new ConflictException("Scorecard cannot be submitted from status: " + s.getStatus());
        }
        String now = Instant.now().toString();
        evalRepo.updateScorecardStatus(scorecardId, "SUBMITTED", now);
        auditService.record("Scorecard", scorecardId, "SUBMIT", actingUserId, null, null, null, null);
        return evalRepo.findScorecardById(scorecardId).orElseThrow();
    }

    public Scorecard recuseScorecard(long scorecardId, long actingUserId, String reason) {
        Scorecard s = evalRepo.findScorecardById(scorecardId)
            .orElseThrow(() -> new NotFoundException("Scorecard", scorecardId));
        if (s.getEvaluatorId() != actingUserId) {
            throw new com.eaglepoint.console.exception.ForbiddenException("Only the assigned evaluator can recuse from this scorecard");
        }
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new ValidationException("reason", "Recusal reason must be 1-1000 characters");
        }
        evalRepo.updateScorecardStatus(scorecardId, "RECUSED", null);
        auditService.record("Scorecard", scorecardId, "RECUSE", actingUserId, null, null, null, reason);
        return evalRepo.findScorecardById(scorecardId).orElseThrow();
    }

    private void validateCycleName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new ValidationException("name", "Cycle name must be 1-100 characters");
        }
    }

    private void validateDates(String startDate, String endDate) {
        if (startDate == null || endDate == null) {
            throw new ValidationException("dates", "Start date and end date are required");
        }
        if (endDate.compareTo(startDate) <= 0) {
            throw new ValidationException("endDate", "End date must be after start date");
        }
    }
}
