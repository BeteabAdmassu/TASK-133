package com.eaglepoint.console.model;

public class Appeal {
    private long id;
    private long scorecardId;
    private long filedBy;
    private String filedAt;
    private String deadline;
    private String reason;
    private String status;
    private String resolvedAt;
    private String resolutionNotes;
    private String createdAt;
    private String updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getScorecardId() { return scorecardId; }
    public void setScorecardId(long scorecardId) { this.scorecardId = scorecardId; }
    public long getFiledBy() { return filedBy; }
    public void setFiledBy(long filedBy) { this.filedBy = filedBy; }
    public String getFiledAt() { return filedAt; }
    public void setFiledAt(String filedAt) { this.filedAt = filedAt; }
    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
