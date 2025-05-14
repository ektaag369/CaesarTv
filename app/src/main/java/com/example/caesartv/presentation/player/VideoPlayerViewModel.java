package com.example.caesartv.presentation.player;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.caesartv.CustomLogger;
import com.example.caesartv.domain.model.MediaItem;
import com.example.caesartv.domain.usecase.GetCachedMediaUseCase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VideoPlayerViewModel extends ViewModel {

    private static final String TAG = "VideoPlayerViewModel";
    private final GetCachedMediaUseCase getCachedMediaUseCase;
    private final Context context;
    private final MutableLiveData<MediaItem> currentMedia = new MutableLiveData<>();
    private List<MediaItem> mediaList = new ArrayList<>();
    private int currentMediaIndex = 0;
    private long startTime;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public VideoPlayerViewModel(GetCachedMediaUseCase getCachedMediaUseCase, Context context) {
        this.getCachedMediaUseCase = getCachedMediaUseCase;
        this.context = context.getApplicationContext();
        loadCachedMedia();
    }

    public void loadCachedMedia() {
        executor.execute(() -> {
            try {
                mediaList = getCachedMediaUseCase.execute();
                CustomLogger.d(TAG, "Initialized with " + mediaList.size() + " cached media items");
                mainHandler.post(this::playNextVideo);
            } catch (Exception e) {
                CustomLogger.e(TAG, "Error loading cached media: " + e.getMessage(), e);
                mainHandler.post(() -> currentMedia.setValue(null)); // Close app immediately on error
            }
        });
    }

    public void playNextVideo() {
        if (mediaList.isEmpty()) {
            CustomLogger.w(TAG, "No media items to play, closing app immediately");
            currentMedia.setValue(null); // Close app immediately
            return;
        }
        boolean isOffline = !isNetworkAvailable();
        CustomLogger.d(TAG, "Playing video, isOffline: " + isOffline + ", currentMediaIndex: " + currentMediaIndex + ", mediaList size: " + mediaList.size());

        if (currentMediaIndex >= mediaList.size()) {
            CustomLogger.d(TAG, "All media items played, closing app immediately");
            currentMedia.setValue(null);
            return;
        }

        MediaItem media = mediaList.get(currentMediaIndex);
        CustomLogger.d(TAG, "Playing media: " + media.getTitle() + ", index: " + currentMediaIndex + ", localPath: " + (media.getLocalFilePath() != null ? media.getLocalFilePath() : media.getUrl()) + ", exists: " + (media.getLocalFilePath() != null && new File(media.getLocalFilePath()).exists()));
        currentMedia.setValue(media);
        currentMediaIndex++;
    }

    public Throwable handleVideoEnd() {
        if (mediaList.isEmpty()) {
            CustomLogger.w(TAG, "No media to handle, closing app immediately");
            mainHandler.post(() -> currentMedia.setValue(null)); // Close app immediately
            return null;
        }
        if (currentMediaIndex == 0) {
            CustomLogger.d(TAG, "No video played yet, skipping video end handling");
            playNextVideo();
            return null;
        }
        MediaItem media = mediaList.get(currentMediaIndex - 1);
        CustomLogger.d(TAG, "Video ended: " + media.getTitle() + ", index: " + (currentMediaIndex - 1));
        playNextVideo();
        return null;
    }

    public void retryCurrentMedia() {
        if (mediaList.isEmpty() || currentMediaIndex == 0) {
            CustomLogger.w(TAG, "No media to retry, closing app immediately");
            mainHandler.post(() -> currentMedia.setValue(null)); // Close app immediately
            return;
        }
        MediaItem media = mediaList.get(currentMediaIndex - 1);
        CustomLogger.d(TAG, "Retrying media: " + media.getTitle() + ", index: " + (currentMediaIndex - 1));
        mainHandler.post(() -> currentMedia.setValue(media));
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public LiveData<MediaItem> getCurrentMedia() {
        return currentMedia;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
        CustomLogger.d(TAG, "Set start time: " + startTime);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (!executor.isShutdown()) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    CustomLogger.w(TAG, "ExecutorService did not terminate");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        mainHandler.removeCallbacksAndMessages(null);
        CustomLogger.d(TAG, "ViewModel cleared, resources released");
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final GetCachedMediaUseCase getCachedMediaUseCase;
        private final Context context;

        public Factory(GetCachedMediaUseCase getCachedMediaUseCase, Context context) {
            this.getCachedMediaUseCase = getCachedMediaUseCase;
            this.context = context;
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            return (T) new VideoPlayerViewModel(getCachedMediaUseCase, context);
        }
    }
}