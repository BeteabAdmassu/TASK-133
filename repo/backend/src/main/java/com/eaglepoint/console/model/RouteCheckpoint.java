package com.eaglepoint.console.model;

public class RouteCheckpoint {
    private long id;
    private long importId;
    private String checkpointName;
    private String expectedAt;
    private String actualAt;
    private Double latMasked;
    private Double lonMasked;
    private Double deviationMiles;
    private boolean isDeviationAlert;
    private boolean isMissedAlert;
    private String status;
    private String createdAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getImportId() { return importId; }
    public void setImportId(long importId) { this.importId = importId; }
    public String getCheckpointName() { return checkpointName; }
    public void setCheckpointName(String checkpointName) { this.checkpointName = checkpointName; }
    public String getExpectedAt() { return expectedAt; }
    public void setExpectedAt(String expectedAt) { this.expectedAt = expectedAt; }
    public String getActualAt() { return actualAt; }
    public void setActualAt(String actualAt) { this.actualAt = actualAt; }
    public Double getLatMasked() { return latMasked; }
    public void setLatMasked(Double latMasked) { this.latMasked = latMasked; }
    public Double getLonMasked() { return lonMasked; }
    public void setLonMasked(Double lonMasked) { this.lonMasked = lonMasked; }
    public Double getDeviationMiles() { return deviationMiles; }
    public void setDeviationMiles(Double deviationMiles) { this.deviationMiles = deviationMiles; }
    public boolean isDeviationAlert() { return isDeviationAlert; }
    public void setDeviationAlert(boolean deviationAlert) { isDeviationAlert = deviationAlert; }
    public boolean isMissedAlert() { return isMissedAlert; }
    public void setMissedAlert(boolean missedAlert) { isMissedAlert = missedAlert; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
