package com.eaglepoint.console.model;

public class RouteImport {
    private long id;
    private String filename;
    private long importedBy;
    private String importedAt;
    private String status;
    private Integer recordCount;
    private Integer errorCount;
    private String checkpointPath;
    private String createdAt;
    private String updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public long getImportedBy() { return importedBy; }
    public void setImportedBy(long importedBy) { this.importedBy = importedBy; }
    public String getImportedAt() { return importedAt; }
    public void setImportedAt(String importedAt) { this.importedAt = importedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRecordCount() { return recordCount; }
    public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }
    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }
    public String getCheckpointPath() { return checkpointPath; }
    public void setCheckpointPath(String checkpointPath) { this.checkpointPath = checkpointPath; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
