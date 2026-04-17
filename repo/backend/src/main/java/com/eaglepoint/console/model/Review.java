package com.eaglepoint.console.model;

public class Review {
    private long id;
    private long scorecardId;
    private long reviewerId;
    private Long secondReviewerId;
    private String status;
    private boolean conflictFlagged;
    private String recusalReason;
    private String recusedAt;
    private String reviewedAt;
    private String comments;
    private String createdAt;
    private String updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getScorecardId() { return scorecardId; }
    public void setScorecardId(long scorecardId) { this.scorecardId = scorecardId; }
    public long getReviewerId() { return reviewerId; }
    public void setReviewerId(long reviewerId) { this.reviewerId = reviewerId; }
    public Long getSecondReviewerId() { return secondReviewerId; }
    public void setSecondReviewerId(Long secondReviewerId) { this.secondReviewerId = secondReviewerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isConflictFlagged() { return conflictFlagged; }
    public void setConflictFlagged(boolean conflictFlagged) { this.conflictFlagged = conflictFlagged; }
    public String getRecusalReason() { return recusalReason; }
    public void setRecusalReason(String recusalReason) { this.recusalReason = recusalReason; }
    public String getRecusedAt() { return recusedAt; }
    public void setRecusedAt(String recusedAt) { this.recusedAt = recusedAt; }
    public String getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(String reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
