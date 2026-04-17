package com.eaglepoint.console.model;

public class ExportJob {
    private long id;
    private String type;
    private String entityType;
    private String filtersJson;
    private String destinationPath;
    private String status;
    private String outputFilePath;
    private String sha256Hash;
    private long initiatedBy;
    private String startedAt;
    private String completedAt;
    private String errorMessage;
    private String checkpointPath;
    private String createdAt;
    private String updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getFiltersJson() { return filtersJson; }
    public void setFiltersJson(String filtersJson) { this.filtersJson = filtersJson; }
    public String getDestinationPath() { return destinationPath; }
    public void setDestinationPath(String destinationPath) { this.destinationPath = destinationPath; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOutputFilePath() { return outputFilePath; }
    public void setOutputFilePath(String outputFilePath) { this.outputFilePath = outputFilePath; }
    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }
    public long getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(long initiatedBy) { this.initiatedBy = initiatedBy; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getCheckpointPath() { return checkpointPath; }
    public void setCheckpointPath(String checkpointPath) { this.checkpointPath = checkpointPath; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
