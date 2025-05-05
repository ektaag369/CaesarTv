package com.example.caesartv.data.local;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.ArrayList;
import java.util.List;

public class MediaWithUrls {
    @Embedded
    public MediaEntity media;
    @Relation(
            parentColumn = "id",
            entityColumn = "mediaId",
            entity = MediaUrlEntity.class
    )
    public List<MediaUrlEntity> urls;

    public MediaWithUrls(MediaEntity media, List<MediaUrlEntity> urls) {
        this.media = media;
        this.urls = urls;
    }
}