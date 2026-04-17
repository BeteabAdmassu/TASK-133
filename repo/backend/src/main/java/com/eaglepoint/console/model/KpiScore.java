package com.eaglepoint.console.model;

public class KpiScore {
    private long id;
    private long kpiId;
    private Long serviceAreaId;
    private Long cycleId;
    private String scoreDate;
    private double value;
    private Long computedBy;
    private String notes;
    private String createdAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getKpiId() { return kpiId; }
    public void setKpiId(long kpiId) { this.kpiId = kpiId; }
    public Long getServiceAreaId() { return serviceAreaId; }
    public void setServiceAreaId(Long serviceAreaId) { this.serviceAreaId = serviceAreaId; }
    public Long getCycleId() { return cycleId; }
    public void setCycleId(Long cycleId) { this.cycleId = cycleId; }
    public String getScoreDate() { return scoreDate; }
    public void setScoreDate(String scoreDate) { this.scoreDate = scoreDate; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public Long getComputedBy() { return computedBy; }
    public void setComputedBy(Long computedBy) { this.computedBy = computedBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
