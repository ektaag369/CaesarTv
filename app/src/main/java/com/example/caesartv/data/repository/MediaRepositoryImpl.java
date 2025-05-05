package com.example.caesartv.data.repository;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import com.example.caesartv.data.local.MediaDao;
import com.example.caesartv.data.local.MediaEntity;
import com.example.caesartv.data.local.MediaUrlEntity;
import com.example.caesartv.data.local.MediaWithUrls;
import com.example.caesartv.data.remote.WebSocketDataSource;
import com.example.caesartv.domain.model.MediaItem;
import com.example.caesartv.domain.model.MediaUrl;
import com.example.caesartv.domain.repository.MediaRepository;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MediaRepositoryImpl implements MediaRepository {

    private static final String TAG = "MediaRepositoryImpl";
    private final WebSocketDataSource webSocketDataSource;
    private final MediaDao mediaDao;
    private final Context context;
    private final OkHttpClient client;
    private final ExecutorService executor;

    public MediaRepositoryImpl(WebSocketDataSource webSocketDataSource, MediaDao mediaDao, Context context, ExecutorService executor) {
        this.webSocketDataSource = webSocketDataSource;
        this.mediaDao = mediaDao;
        this.context = context;
        this.executor = executor;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void fetchMedia(OnMediaFetchedListener listener, Runnable onBlocked, Runnable onError) {
        webSocketDataSource.connect(
                mediaItems -> {
                    executor.execute(() -> {
                        try {
                            // Clear old data
                            mediaDao.deleteAll();
                            mediaDao.deleteAllUrls();
                            Log.d(TAG, "Cleared all previous media and URLs from database");

                            List<MediaEntity> entities = new ArrayList<>();
                            List<MediaUrlEntity> urlEntities = new ArrayList<>();
                            for (MediaItem item : mediaItems) {
                                String localFilePath = downloadVideo(item.getUrl(), item.getId());
                                Log.d(TAG, "Media ID: " + item.getId() + ", Local file path: " + localFilePath);
                                entities.add(toEntity(item, localFilePath));
                                for (MediaUrl url : item.getMultipleUrl()) {
                                    urlEntities.add(new MediaUrlEntity(url.getUrlType(), url.getUrl(), url.getId(), item.getId()));
                                }
                            }
                            mediaDao.insertAll(entities);
                            mediaDao.insertUrls(urlEntities);
                            Log.d(TAG, "Saved " + entities.size() + " media items and " + urlEntities.size() + " URLs to database");
                            listener.onMediaFetched(mediaItems);
                        } catch (Exception e) {
                            Log.e(TAG, "Error saving media to database", e);
                            onError.run();
                        }
                    });
                },
                onBlocked,
                onError
        );
    }

    @Override
    public List<MediaItem> getCachedMedia() {
        List<MediaWithUrls> mediaWithUrls = mediaDao.getAllMedia();
        List<MediaItem> mediaItems = new ArrayList<>();
        for (MediaWithUrls item : mediaWithUrls) {
            MediaItem mediaItem = toDomain(item);
            Log.d(TAG, "Cached media ID: " + mediaItem.getId() + ", URL: " + mediaItem.getUrl() + ", Local file path: " + mediaItem.getLocalFilePath());
            mediaItems.add(mediaItem);
        }
        Log.d(TAG, "Fetched " + mediaItems.size() + " cached media items");
        return mediaItems;
    }

    @Override
    public int countCachedMedia() {
        int count = mediaDao.countActiveMedia();
        Log.d(TAG, "Counted " + count + " active media items in database");
        return count;
    }

    @Override
    public void disconnectWebSocket() {
        webSocketDataSource.disconnect();
    }

    @Override
    public void disconnect() {
        webSocketDataSource.disconnect();
    }

    private MediaEntity toEntity(MediaItem item, String localFilePath) {
        Log.d(TAG, "Mapping MediaItem to MediaEntity, ID: " + item.getId() + ", Local file path: " + localFilePath);
        return new MediaEntity(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getMediaType(),
                item.getUrl(),
                localFilePath, // Set to null if download failed
                item.getThumbnailUrl(),
                item.getDuration(),
                item.getDisplayOrder(),
                item.isActive(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private MediaItem toDomain(MediaWithUrls mediaWithUrls) {
        MediaEntity entity = mediaWithUrls.media;
        List<MediaUrl> urls = new ArrayList<>();
        if (mediaWithUrls.urls != null) {
            for (MediaUrlEntity urlEntity : mediaWithUrls.urls) {
                urls.add(new MediaUrl(urlEntity.urlType, urlEntity.url, urlEntity.id));
            }
        }
        String finalUrl = entity.localFilePath != null && !entity.localFilePath.isEmpty() && new File(entity.localFilePath).exists() ? entity.localFilePath : entity.url;
        Log.d(TAG, "Mapping MediaEntity to MediaItem, ID: " + entity.id + ", Selected URL: " + finalUrl + ", Local file exists: " + (finalUrl.equals(entity.localFilePath) ? "yes" : "no"));
        MediaItem mediaItem = new MediaItem(
                entity.id,
                entity.title,
                entity.description,
                entity.mediaType,
                finalUrl,
                urls,
                entity.thumbnailUrl,
                entity.duration,
                entity.displayOrder,
                entity.isActive,
                entity.createdAt,
                entity.updatedAt
        );
        mediaItem.setLocalFilePath(entity.localFilePath); // Preserve localFilePath
        return mediaItem;
    }

    private String downloadVideo(String url, String mediaId) {
        if (url == null || url.isEmpty()) {
            Log.w(TAG, "No URL provided for media ID: " + mediaId);
            return null;
        }

        try {
            File dir = new File(context.getFilesDir(), "videos");
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "Failed to create videos directory: " + dir.getAbsolutePath());
                return null;
            }
            File file = new File(dir, mediaId + ".mp4");
            if (file.exists() && file.length() > 1024 && file.canRead()) {
                Log.d(TAG, "Video already cached: " + file.getAbsolutePath() + ", Size: " + file.length() + " bytes");
                return file.getAbsolutePath();
            }

            if (!isNetworkAvailable()) {
                Log.w(TAG, "No network available, cannot download video for media ID: " + mediaId + ", URL: " + url);
                return null;
            }

            Log.d(TAG, "Downloading video from: " + url + " for media ID: " + mediaId);
            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                Log.e(TAG, "Failed to download video, HTTP code: " + response.code() + ", Message: " + response.message() + ", URL: " + url);
                response.close();
                return null;
            }

            byte[] bytes = response.body().bytes();
            if (bytes.length < 1024) { // Minimum size threshold
                Log.e(TAG, "Downloaded video too small: " + bytes.length + " bytes for media ID: " + mediaId + ", URL: " + url);
                response.close();
                return null;
            }

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes);
            fos.close();
            response.close();
            Log.d(TAG, "Downloaded video to: " + file.getAbsolutePath() + ", Size: " + file.length() + " bytes");

            if (!file.canRead() || file.length() == 0) {
                Log.e(TAG, "Downloaded video is unreadable or empty: " + file.getAbsolutePath());
                return null;
            }

            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Error downloading video for media ID: " + mediaId + ", URL: " + url, e);
            return null;
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public void verifyCachedFiles() {
        executor.execute(() -> {
            List<MediaWithUrls> mediaWithUrls = mediaDao.getAllMedia();
            for (MediaWithUrls item : mediaWithUrls) {
                MediaEntity entity = item.media;
                if (entity.localFilePath != null && !new File(entity.localFilePath).exists()) {
                    Log.w(TAG, "Cached file missing for media ID: " + entity.id + ", Local file path: " + entity.localFilePath);
                    // Clear invalid localFilePath
                    entity.localFilePath = null;
                    mediaDao.insertAll(List.of(entity));
                    Log.d(TAG, "Cleared localFilePath for media ID: " + entity.id + ", Using remote URL: " + entity.url);
                }
            }
        });
    }
}