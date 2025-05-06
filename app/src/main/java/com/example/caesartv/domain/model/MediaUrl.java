package com.example.caesartv.domain.model;

public class MediaUrl {
    private final String urlType;
    private final String url;
    private final String id;
    private final String localFilePath;

    public MediaUrl(String urlType, String url, String id, String localFilePath) {
        this.urlType = urlType;
        this.url = url;
        this.id = id;
        this.localFilePath = localFilePath;
    }

    public MediaUrl(String urlType, String url, String id) {
        this(urlType, url, id, null);
    }

    public String getUrlType() {
        return urlType;
    }

    public String getUrl() {
        return url;
    }

    public String getId() {
        return id;
    }

    public String getLocalFilePath() {
        return localFilePath;
    }
}