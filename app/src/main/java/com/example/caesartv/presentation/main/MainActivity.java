package com.example.caesartv.presentation.main;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.caesartv.R;
import com.example.caesartv.di.AppModule;
import com.example.caesartv.domain.usecase.GetCachedMediaUseCase;
import com.example.caesartv.presentation.player.VideoPlayerFragment;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private MainViewmodel viewModel;
    private Handler mainHandler;
    private GetCachedMediaUseCase cachedMediaUseCase;
    private AppModule appModule;
    private boolean isSplashDisplayed = true;
    private VideoPlayerFragment videoPlayerFragment;
    private ExecutorService executorService;
    private static final long MEDIA_CHECK_TIMEOUT_MS = 10000; // 10s timeout

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());
        appModule = new AppModule();
        cachedMediaUseCase = appModule.provideGetCachedMediaUseCase(this);
        viewModel = appModule.provideMainViewModel(this);
        executorService = Executors.newSingleThreadExecutor();

        // Show splash screen
        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) ImageView splashLogo = findViewById(R.id.splash_logo);
        splashLogo.setVisibility(View.VISIBLE);
        Log.d(TAG, "Splash screen displayed");

        // Initialize in background
        executorService.execute(() -> {
            // Preload VideoPlayerFragment
            videoPlayerFragment = new VideoPlayerFragment();
            Log.d(TAG, "VideoPlayerFragment preloaded");

            // Start WebSocket and check media
            viewModel.connectWebSocket();
            viewModel.checkCachedMedia();
        });

        // Transition after 5 seconds
        mainHandler.postDelayed(() -> {
            isSplashDisplayed = false;
            splashLogo.setVisibility(View.GONE);
            Log.d(TAG, "Splash screen hidden");
            setContentView(R.layout.activity_main);
            // Check block status
            Boolean isBlocked = viewModel.getIsDeviceBlocked().getValue();
            if (isBlocked != null && isBlocked) {
                setContentView(R.layout.activity_blocked);
                Log.w(TAG, "Device is blocked, showing blocked screen");
                mainHandler.postDelayed(this::finish, 5000);
            } else {
                waitForMediaAndStartVideoPlayer();
                observeViewModel();
            }
        }, 5000);
    }

    private void waitForMediaAndStartVideoPlayer() {
        Log.d(TAG, "Waiting for media items");
        long startTime = System.currentTimeMillis();
        Runnable checkMedia = new Runnable() {
            @Override
            public void run() {
                if (viewModel.getMediaItems().getValue() != null && !viewModel.getMediaItems().getValue().isEmpty()) {
                    Log.d(TAG, "Media items available: " + viewModel.getMediaItems().getValue().size());
                    startVideoPlayer();
                } else if (System.currentTimeMillis() - startTime < MEDIA_CHECK_TIMEOUT_MS) {
                    Log.d(TAG, "No media yet, retrying...");
                    mainHandler.postDelayed(this, 500);
                } else {
                    Log.w(TAG, "Media fetch timeout, starting VideoPlayerFragment with no media");
                    startVideoPlayer();
                }
            }
        };
        mainHandler.post(checkMedia);
    }

    private void observeViewModel() {
        viewModel.getMediaItems().observe(this, mediaItems -> {
            Log.d(TAG, "mediaItems received: " + (mediaItems != null ? mediaItems.size() : "null"));
            if (mediaItems != null && !mediaItems.isEmpty() && !isSplashDisplayed) {
                Log.d(TAG, "Media items received, VideoPlayerFragment already started or will start");
            } else {
                Log.d(TAG, "No media items available or splash screen still displayed");
            }
        });

        viewModel.getIsDeviceBlocked().observe(this, isBlocked -> {
            Log.d(TAG, "isDeviceBlocked: " + isBlocked);
            if (isBlocked != null && isBlocked && !isSplashDisplayed) {
                setContentView(R.layout.activity_blocked);
                Log.w(TAG, "Device is blocked, showing blocked screen");
                mainHandler.postDelayed(this::finish, 5000);
            }
        });
    }

    private void startVideoPlayer() {
        Log.d(TAG, "Starting video player");
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_browse_fragment, videoPlayerFragment)
                .commit();
        Log.d(TAG, "Fragment transaction committed");
    }

    public GetCachedMediaUseCase getCachedMediaUseCase() {
        return cachedMediaUseCase;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewModel.disconnectWebSocket();
        mainHandler.removeCallbacksAndMessages(null);
        if (isFinishing()) {
            appModule.shutdownExecutorService();
            executorService.shutdown();
        }
    }
}