package com.example.caesartv.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "media")
public class MediaEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String title;
    public String description;
    public String mediaType;
    public String url;
    public String localFilePath;
    public String thumbnailUrl;
    public int duration;
    public int displayOrder;
    public boolean isActive;
    public String createdAt;
    public String updatedAt;

    public MediaEntity(@NonNull String id, String title, String description, String mediaType,
                       String url, String localFilePath, String thumbnailUrl, int duration,
                       int displayOrder, boolean isActive, String createdAt, String updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.mediaType = mediaType;
        this.url = url;
        this.localFilePath = localFilePath;
        this.thumbnailUrl = thumbnailUrl;
        this.duration = duration;
        this.displayOrder = displayOrder;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}