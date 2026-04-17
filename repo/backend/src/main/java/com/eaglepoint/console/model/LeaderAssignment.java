package com.eaglepoint.console.model;

public class LeaderAssignment {
    private long id;
    private long serviceAreaId;
    private long userId;
    private long assignedBy;
    private String assignedAt;
    private String unassignedAt;
    private String createdAt;
    private String updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getServiceAreaId() { return serviceAreaId; }
    public void setServiceAreaId(long serviceAreaId) { this.serviceAreaId = serviceAreaId; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public long getAssignedBy() { return assignedBy; }
    public void setAssignedBy(long assignedBy) { this.assignedBy = assignedBy; }
    public String getAssignedAt() { return assignedAt; }
    public void setAssignedAt(String assignedAt) { this.assignedAt = assignedAt; }
    public String getUnassignedAt() { return unassignedAt; }
    public void setUnassignedAt(String unassignedAt) { this.unassignedAt = unassignedAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
