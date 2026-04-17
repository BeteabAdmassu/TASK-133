package com.eaglepoint.console.model;

public class BedStateHistory {
    private long id;
    private long bedId;
    private String fromState;
    private String toState;
    private long changedBy;
    private String changedAt;
    private String reason;
    private String residentIdEncrypted;
    private String notes;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getBedId() { return bedId; }
    public void setBedId(long bedId) { this.bedId = bedId; }
    public String getFromState() { return fromState; }
    public void setFromState(String fromState) { this.fromState = fromState; }
    public String getToState() { return toState; }
    public void setToState(String toState) { this.toState = toState; }
    public long getChangedBy() { return changedBy; }
    public void setChangedBy(long changedBy) { this.changedBy = changedBy; }
    public String getChangedAt() { return changedAt; }
    public void setChangedAt(String changedAt) { this.changedAt = changedAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getResidentIdEncrypted() { return residentIdEncrypted; }
    public void setResidentIdEncrypted(String residentIdEncrypted) { this.residentIdEncrypted = residentIdEncrypted; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
