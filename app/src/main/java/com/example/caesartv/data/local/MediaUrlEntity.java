package com.example.caesartv.data.local;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "media_url",
        foreignKeys = @ForeignKey(entity = MediaEntity.class,
                parentColumns = "id",
                childColumns = "mediaId",
                onDelete = ForeignKey.CASCADE))
public class MediaUrlEntity {
    @PrimaryKey(autoGenerate = true)
    public long dbId;
    public String mediaId;
    public String urlType;
    public String url;
    public String id;
    public String localFilePath; // New field for local file path

    public MediaUrlEntity(String urlType, String url, String id, String mediaId, String localFilePath) {
        this.urlType = urlType;
        this.url = url;
        this.id = id;
        this.mediaId = mediaId;
        this.localFilePath = localFilePath;
    }
}