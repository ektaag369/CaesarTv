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
import com.example.caesartv.domain.model.MediaItem;
import com.example.caesartv.domain.usecase.GetCachedMediaUseCase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private void loadCachedMedia() {
        executor.execute(() -> {
            try {
                mediaList = getCachedMediaUseCase.execute();
                Log.d(TAG, "Initialized with " + mediaList.size() + " cached media items");
                mainHandler.post(this::playNextVideo);
            } catch (Exception e) {
                Log.e(TAG, "Error loading cached media: " + e.getMessage(), e);
                mainHandler.post(() -> currentMedia.setValue(null));
            }
        });
    }

    public void playNextVideo() {
        if (mediaList.isEmpty()) {
            Log.w(TAG, "No media items to play");
            currentMedia.setValue(null);
            return;
        }
        boolean isOffline = !isNetworkAvailable();
        Log.d(TAG, "Playing video, isOffline: " + isOffline + ", currentMediaIndex: " + currentMediaIndex + ", mediaList size: " + mediaList.size());
        if (currentMediaIndex >= mediaList.size()) {
            Log.d(TAG, "All videos played, closing app");
            currentMedia.setValue(null);
            return;
        }
        MediaItem media = mediaList.get(currentMediaIndex);
        Log.d(TAG, "Playing media: " + media.getTitle() + ", index: " + currentMediaIndex + ", localPath: " + (media.getLocalFilePath() != null ? media.getLocalFilePath() : media.getUrl()) + ", exists: " + (media.getLocalFilePath() != null && new File(media.getLocalFilePath()).exists()));
        currentMedia.setValue(media);
        currentMediaIndex++;
    }

    public void handleVideoEnd() {
        if (mediaList.isEmpty()) {
            Log.w(TAG, "No media to handle");
            currentMedia.setValue(null);
            return;
        }
        if (currentMediaIndex == 0) {
            Log.d(TAG, "No video played yet, skipping video end handling");
            playNextVideo();
            return;
        }
        MediaItem media = mediaList.get(currentMediaIndex - 1);
        Log.d(TAG, "Video ended: " + media.getTitle() + ", index: " + (currentMediaIndex - 1));
        playNextVideo();
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
        Log.d(TAG, "Set start time: " + startTime);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
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