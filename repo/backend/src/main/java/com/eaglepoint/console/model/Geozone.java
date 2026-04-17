package com.eaglepoint.console.model;

public class Geozone {
    private long id;
    private String name;
    private String zipCodes;
    private String streetRanges;
    private String createdAt;
    private String updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getZipCodes() { return zipCodes; }
    public void setZipCodes(String zipCodes) { this.zipCodes = zipCodes; }
    public String getStreetRanges() { return streetRanges; }
    public void setStreetRanges(String streetRanges) { this.streetRanges = streetRanges; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
