package com.eaglepoint.console.model;

public class PickupPoint {
    private long id;
    private long communityId;
    private Long serviceAreaId;
    private Long geozoneId;
    private String addressEncrypted;
    private String zipCode;
    private String streetRangeStart;
    private String streetRangeEnd;
    private String hoursJson;
    private int capacity;
    private String status;
    private String pausedUntil;
    private String pauseReason;
    private boolean manualOverride;
    private String overrideNotes;
    private String activeDate;
    private String createdAt;
    private String updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getCommunityId() { return communityId; }
    public void setCommunityId(long communityId) { this.communityId = communityId; }
    public Long getServiceAreaId() { return serviceAreaId; }
    public void setServiceAreaId(Long serviceAreaId) { this.serviceAreaId = serviceAreaId; }
    public Long getGeozoneId() { return geozoneId; }
    public void setGeozoneId(Long geozoneId) { this.geozoneId = geozoneId; }
    public String getAddressEncrypted() { return addressEncrypted; }
    public void setAddressEncrypted(String addressEncrypted) { this.addressEncrypted = addressEncrypted; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    public String getStreetRangeStart() { return streetRangeStart; }
    public void setStreetRangeStart(String streetRangeStart) { this.streetRangeStart = streetRangeStart; }
    public String getStreetRangeEnd() { return streetRangeEnd; }
    public void setStreetRangeEnd(String streetRangeEnd) { this.streetRangeEnd = streetRangeEnd; }
    public String getHoursJson() { return hoursJson; }
    public void setHoursJson(String hoursJson) { this.hoursJson = hoursJson; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPausedUntil() { return pausedUntil; }
    public void setPausedUntil(String pausedUntil) { this.pausedUntil = pausedUntil; }
    public String getPauseReason() { return pauseReason; }
    public void setPauseReason(String pauseReason) { this.pauseReason = pauseReason; }
    public boolean isManualOverride() { return manualOverride; }
    public void setManualOverride(boolean manualOverride) { this.manualOverride = manualOverride; }
    public String getOverrideNotes() { return overrideNotes; }
    public void setOverrideNotes(String overrideNotes) { this.overrideNotes = overrideNotes; }
    public String getActiveDate() { return activeDate; }
    public void setActiveDate(String activeDate) { this.activeDate = activeDate; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
