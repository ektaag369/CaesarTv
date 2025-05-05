package com.example.caesartv.domain.model;

import java.util.List;

public class MediaItem {
    private String id;
    private String title;
    private String description;
    private String mediaType;
    private String url;
    private String localFilePath;
    private List<MediaUrl> multipleUrl;
    private String thumbnailUrl;
    private int duration;
    private int displayOrder;
    private boolean isActive;
    private String createdAt;
    private String updatedAt;

    // Constructor
    public MediaItem(String id, String title, String description, String mediaType, String url,
                     List<MediaUrl> multipleUrl, String thumbnailUrl, int duration,
                     int displayOrder, boolean isActive, String createdAt, String updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.mediaType = mediaType;
        this.url = url;
        this.multipleUrl = multipleUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.duration = duration;
        this.displayOrder = displayOrder;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and setters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getMediaType() { return mediaType; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getLocalFilePath() { return localFilePath; }
    public void setLocalFilePath(String localFilePath) { this.localFilePath = localFilePath; }
    public List<MediaUrl> getMultipleUrl() { return multipleUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public int getDuration() { return duration; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return isActive; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}