package com.eaglepoint.console.model;

public class Scorecard {
    private long id;
    private long cycleId;
    private long templateId;
    private long evaluateeId;
    private long evaluatorId;
    private String type;
    private String status;
    private String submittedAt;
    private String createdAt;
    private String updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getCycleId() { return cycleId; }
    public void setCycleId(long cycleId) { this.cycleId = cycleId; }
    public long getTemplateId() { return templateId; }
    public void setTemplateId(long templateId) { this.templateId = templateId; }
    public long getEvaluateeId() { return evaluateeId; }
    public void setEvaluateeId(long evaluateeId) { this.evaluateeId = evaluateeId; }
    public long getEvaluatorId() { return evaluatorId; }
    public void setEvaluatorId(long evaluatorId) { this.evaluatorId = evaluatorId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
