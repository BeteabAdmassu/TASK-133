package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.KpiDefinition;
import com.eaglepoint.console.model.KpiScore;
import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.repository.KpiRepository;

public class KpiService {
    private final KpiRepository kpiRepo;
    private final AuditService auditService;

    public KpiService(KpiRepository kpiRepo, AuditService auditService) {
        this.kpiRepo = kpiRepo;
        this.auditService = auditService;
    }

    public KpiDefinition createKpi(String name, String unit, String category, String formula, String description) {
        validateName(name);
        validateCategory(category);
        if (kpiRepo.findKpiByName(name.trim()).isPresent()) {
            throw new ConflictException("A KPI with this name already exists");
        }
        KpiDefinition k = new KpiDefinition();
        k.setName(name.trim());
        k.setUnit(unit);
        k.setCategory(category.trim());
        k.setFormula(formula);
        k.setDescription(description);
        k.setActive(true);
        long id = kpiRepo.insertKpi(k);
        return kpiRepo.findKpiById(id).orElseThrow();
    }

    public KpiDefinition getKpi(long id) {
        return kpiRepo.findKpiById(id).orElseThrow(() -> new NotFoundException("KpiDefinition", id));
    }

    public PagedResult<KpiDefinition> listKpis(int page, int pageSize) {
        return kpiRepo.findAllKpis(page, pageSize);
    }

    public KpiDefinition updateKpi(long id, String name, String unit, String category, String formula, Boolean isActive) {
        KpiDefinition k = kpiRepo.findKpiById(id).orElseThrow(() -> new NotFoundException("KpiDefinition", id));
        if (name != null) { validateName(name); k.setName(name.trim()); }
        if (unit != null) k.setUnit(unit);
        if (category != null) { validateCategory(category); k.setCategory(category.trim()); }
        if (formula != null) k.setFormula(formula);
        if (isActive != null) k.setActive(isActive);
        kpiRepo.updateKpi(k);
        return kpiRepo.findKpiById(id).orElseThrow();
    }

    public KpiScore recordScore(long kpiId, double value, String scoreDate, Long serviceAreaId,
                                 Long cycleId, String notes, long computedBy) {
        kpiRepo.findKpiById(kpiId).orElseThrow(() -> new NotFoundException("KpiDefinition", kpiId));
        if (scoreDate == null || scoreDate.isBlank()) {
            throw new ValidationException("scoreDate", "Score date is required (ISO-8601)");
        }
        KpiScore s = new KpiScore();
        s.setKpiId(kpiId);
        s.setValue(value);
        s.setScoreDate(scoreDate);
        s.setServiceAreaId(serviceAreaId);
        s.setCycleId(cycleId);
        s.setNotes(notes);
        s.setComputedBy(computedBy);
        long id = kpiRepo.insertScore(s);
        return kpiRepo.findScores(kpiId, serviceAreaId, null, null, 1, 1).getData().stream()
            .filter(sc -> sc.getId() == id).findFirst()
            .orElseGet(() -> { s.setId(id); return s; });
    }

    public PagedResult<KpiScore> listScores(Long kpiId, Long serviceAreaId, String from, String to,
                                             int page, int pageSize) {
        return kpiRepo.findScores(kpiId, serviceAreaId, from, to, page, pageSize);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new ValidationException("name", "KPI name must be 1-100 characters");
        }
    }

    private void validateCategory(String category) {
        if (category == null || category.isBlank() || category.trim().length() > 50) {
            throw new ValidationException("category", "KPI category must be 1-50 characters");
        }
    }
}
