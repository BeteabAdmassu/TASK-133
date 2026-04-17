package com.eaglepoint.console.model;

public class AuditTrail {
    private long id;
    private String entityType;
    private long entityId;
    private String action;
    private long userId;
    private String traceId;
    private String occurredAt;
    private String oldValuesJson;
    private String newValuesJson;
    private String notes;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public long getEntityId() { return entityId; }
    public void setEntityId(long entityId) { this.entityId = entityId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }
    public String getOldValuesJson() { return oldValuesJson; }
    public void setOldValuesJson(String oldValuesJson) { this.oldValuesJson = oldValuesJson; }
    public String getNewValuesJson() { return newValuesJson; }
    public void setNewValuesJson(String newValuesJson) { this.newValuesJson = newValuesJson; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
