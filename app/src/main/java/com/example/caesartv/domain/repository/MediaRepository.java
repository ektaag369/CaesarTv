package com.example.caesartv.domain.repository;

import com.example.caesartv.domain.model.MediaItem;
import java.util.List;

public interface MediaRepository {
    void fetchMedia(OnMediaFetchedListener listener, Runnable onBlocked, Runnable onError);
    List<MediaItem> getCachedMedia();
    void disconnectWebSocket();

    void disconnect();

    int countCachedMedia();

    interface OnMediaFetchedListener {
        void onMediaFetched(List<MediaItem> mediaItems);
    }
}