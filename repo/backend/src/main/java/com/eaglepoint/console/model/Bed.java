package com.eaglepoint.console.model;

public class Bed {
    private long id;
    private long roomId;
    private String bedLabel;
    private BedState state;
    private String residentIdEncrypted;
    private String admittedAt;
    private String createdAt;
    private String updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getRoomId() { return roomId; }
    public void setRoomId(long roomId) { this.roomId = roomId; }
    public String getBedLabel() { return bedLabel; }
    public void setBedLabel(String bedLabel) { this.bedLabel = bedLabel; }
    public BedState getState() { return state; }
    public void setState(BedState state) { this.state = state; }
    public String getResidentIdEncrypted() { return residentIdEncrypted; }
    public void setResidentIdEncrypted(String residentIdEncrypted) { this.residentIdEncrypted = residentIdEncrypted; }
    public String getAdmittedAt() { return admittedAt; }
    public void setAdmittedAt(String admittedAt) { this.admittedAt = admittedAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
