package com.example.caesartv.domain.model;

public class MediaUrl {
    private String urlType;
    private String url;
    private String id;

    public MediaUrl(String urlType, String url, String id) {
        this.urlType = urlType;
        this.url = url;
        this.id = id;
    }

    public String getUrlType() { return urlType; }
    public String getUrl() { return url; }
    public String getId() { return id; }
}