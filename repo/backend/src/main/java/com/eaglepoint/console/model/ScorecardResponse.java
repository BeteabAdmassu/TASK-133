package com.eaglepoint.console.model;

public class ScorecardResponse {
    private long id;
    private long scorecardId;
    private long metricId;
    private double score;
    private String comments;
    private String createdAt;
    private String updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getScorecardId() { return scorecardId; }
    public void setScorecardId(long scorecardId) { this.scorecardId = scorecardId; }
    public long getMetricId() { return metricId; }
    public void setMetricId(long metricId) { this.metricId = metricId; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
