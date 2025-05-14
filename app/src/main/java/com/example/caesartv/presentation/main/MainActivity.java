package com.example.caesartv.presentation.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.caesartv.CustomLogger;
import com.example.caesartv.R;
import com.example.caesartv.data.repository.MediaRepositoryImpl;
import com.example.caesartv.di.AppModule;
import com.example.caesartv.domain.model.MediaItem;
import com.example.caesartv.domain.usecase.GetCachedMediaUseCase;
import com.example.caesartv.presentation.player.VideoPlayerFragment;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    private static final long BLOCKED_CLOSE_DELAY_MS = 3000; // 3s delay before closing
    private boolean isDeviceBlocked = false;
    private ImageView splashLogo;

    // Double back press logic
    private static final long DOUBLE_BACK_PRESS_INTERVAL = 2000; // 2s interval
    private long lastBackPressTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());
        appModule = new AppModule();
        cachedMediaUseCase = appModule.provideGetCachedMediaUseCase(this);
        viewModel = appModule.provideMainViewModel(this);
        executorService = Executors.newSingleThreadExecutor();

        // Verify cached files
        ((MediaRepositoryImpl) appModule.provideMediaRepository(this)).verifyCachedFiles();

        // Show splash screen
        splashLogo = findViewById(R.id.splash_logo);
        splashLogo.setVisibility(View.VISIBLE);
        CustomLogger.d(TAG,"Splash screen displayed");

        // Initialize in background
        executorService.execute(() -> {
            // Preload VideoPlayerFragment with callback
            videoPlayerFragment = VideoPlayerFragment.newInstance((Void unused) -> {
                mainHandler.post(() -> {
                    isSplashDisplayed = false;
                    splashLogo.setVisibility(View.GONE);
                    CustomLogger.d(TAG,"Splash screen hidden after video ready");
                });
            });
            CustomLogger.d(TAG, "VideoPlayerFragment preloaded");

            // Start WebSocket and check media
            viewModel.connectWebSocket();
            viewModel.checkCachedMedia();
        });

        // Trigger device block status check
        handleDeviceBlockStatus();
    }

    private void handleDeviceBlockStatus() {
        Boolean isBlocked = viewModel.getIsDeviceBlocked().getValue();
        isDeviceBlocked = isBlocked != null && isBlocked;
        if (isDeviceBlocked) {
            setContentView(R.layout.activity_blocked);
            splashLogo.setVisibility(View.GONE);
            CustomLogger.w(TAG, "Device is blocked, showing blocked screen");
            // Remove VideoPlayerFragment if it exists
            if (videoPlayerFragment != null && videoPlayerFragment.isAdded()) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .remove(videoPlayerFragment)
                        .commit();
                CustomLogger.d(TAG, "VideoPlayerFragment removed due to device block");
            }
            // Close app after 3 seconds
            mainHandler.postDelayed(this::finish, BLOCKED_CLOSE_DELAY_MS);
            CustomLogger.d(TAG, "Scheduled app closure in " + BLOCKED_CLOSE_DELAY_MS + "ms");
        } else {
            setContentView(R.layout.activity_main);
            // Reinitialize splash logo since layout is reset
            splashLogo = findViewById(R.id.splash_logo);
            splashLogo.setVisibility(View.VISIBLE);
            checkCachedMediaAndStartVideoPlayer();
            observeViewModel();
            CustomLogger.d(TAG, "Device unblocked, resuming normal flow");
        }
    }

    private void checkCachedMediaAndStartVideoPlayer() {
        CustomLogger.d(TAG, "Checking cached media");
        executorService.execute(() -> {
            List<MediaItem> cachedMedia = cachedMediaUseCase.execute();
            mainHandler.post(() -> {
                if (!cachedMedia.isEmpty()) {
                    CustomLogger.d(TAG, "Cached media available: " + cachedMedia.size() + " items");
                    viewModel.getMediaItems().postValue(cachedMedia);
                    startVideoPlayer();
                } else {
                    CustomLogger.d(TAG, "No cached media, waiting for API media");
                    waitForMediaAndStartVideoPlayer();
                }
            });
        });
    }

    private void waitForMediaAndStartVideoPlayer() {
        CustomLogger.d(TAG, "Waiting for media items");
        long startTime = System.currentTimeMillis();
        Runnable checkMedia = new Runnable() {
            @Override
            public void run() {
                if (isDeviceBlocked) {
                    CustomLogger.d(TAG, "Device is blocked, skipping media check");
                    return;
                }
                if (viewModel.getMediaItems().getValue() != null && !viewModel.getMediaItems().getValue().isEmpty()) {
                    CustomLogger.d(TAG, "Media items available: " + viewModel.getMediaItems().getValue().size());
                    startVideoPlayer();
                } else if (System.currentTimeMillis() - startTime < MEDIA_CHECK_TIMEOUT_MS) {
                    CustomLogger.d(TAG, "No media yet, retrying...");
                    mainHandler.postDelayed(this, 500);
                } else {
                    CustomLogger.w(TAG, "Media fetch timeout, checking cached media");
                    checkCachedMediaAndStartVideoPlayer();
                }
            }
        };
        mainHandler.post(checkMedia);
    }

    private void observeViewModel() {
        viewModel.getMediaItems().observe(this, mediaItems -> {
            CustomLogger.d(TAG, "mediaItems received: " + (mediaItems != null ? mediaItems.size() : "null"));
            if (mediaItems != null && !mediaItems.isEmpty() && !isSplashDisplayed && !isDeviceBlocked) {
                CustomLogger.d(TAG, "Media items received, starting VideoPlayerFragment");
                startVideoPlayer();
            } else {
                CustomLogger.d(TAG, "No media items, splash screen displayed, or device blocked");
            }
        });

        viewModel.getIsDeviceBlocked().observe(this, isBlocked -> {
            CustomLogger.d(TAG, "isDeviceBlocked: " + isBlocked);
            boolean newBlockStatus = isBlocked != null && isBlocked;
            if (newBlockStatus != isDeviceBlocked) {
                isDeviceBlocked = newBlockStatus;
                if (isDeviceBlocked) {
                    setContentView(R.layout.activity_blocked);
                    CustomLogger.w(TAG, "Device is blocked, showing blocked screen");
                    // Stop and remove VideoPlayerFragment
                    if (videoPlayerFragment != null && videoPlayerFragment.isAdded()) {
                        videoPlayerFragment.pauseVideo();
                        getSupportFragmentManager()
                                .beginTransaction()
                                .remove(videoPlayerFragment)
                                .commit();
                        CustomLogger.d(TAG, "VideoPlayerFragment paused and removed due to device block");
                    }
                    // Close app after 3 seconds
                    mainHandler.postDelayed(this::finish, BLOCKED_CLOSE_DELAY_MS);
                    CustomLogger.d(TAG, "Scheduled app closure in " + BLOCKED_CLOSE_DELAY_MS + "ms");
                } else {
                    setContentView(R.layout.activity_main);
                    // Reinitialize splash logo
                    splashLogo = findViewById(R.id.splash_logo);
                    splashLogo.setVisibility(View.VISIBLE);
                    CustomLogger.d(TAG, "Device unblocked, resuming normal flow");
                    // Reinitialize VideoPlayerFragment and force media check
                    videoPlayerFragment = VideoPlayerFragment.newInstance((Void unused) -> {
                        mainHandler.post(() -> {
                            isSplashDisplayed = false;
                            splashLogo.setVisibility(View.GONE);
                            CustomLogger.d(TAG, "Splash screen hidden after video ready");
                        });
                    });
                    checkCachedMediaAndStartVideoPlayer();
                }
            }
        });
    }

    private void startVideoPlayer() {
        if (isDeviceBlocked) {
            CustomLogger.d(TAG, "Device is blocked, not starting video player");
            return;
        }
        CustomLogger.d(TAG, "Starting video player");
        if (videoPlayerFragment == null || videoPlayerFragment.isDetached()) {
            videoPlayerFragment = VideoPlayerFragment.newInstance((Void unused) -> {
                mainHandler.post(() -> {
                    isSplashDisplayed = false;
                    splashLogo.setVisibility(View.GONE);
                    CustomLogger.d(TAG, "Splash screen hidden after video ready");
                });
            });
            CustomLogger.d(TAG, "Created new VideoPlayerFragment instance");
        }
        if (!videoPlayerFragment.isAdded()) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_browse_fragment, videoPlayerFragment)
                    .commitAllowingStateLoss();
            CustomLogger.d(TAG, "Fragment transaction committed");
        } else {
            CustomLogger.d(TAG, "VideoPlayerFragment already added");
        }
    }

    public GetCachedMediaUseCase getCachedMediaUseCase() {
        return cachedMediaUseCase;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Disconnect WebSocket
        viewModel.disconnectWebSocket();

        // Clear Handler callbacks
        if (mainHandler != null) {
            CustomLogger.d(TAG, "Clearing Handler callbacks");
            mainHandler.removeCallbacksAndMessages(null);
        }

        // Shutdown ExecutorService
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    CustomLogger.w(TAG, "ExecutorService did not terminate");
                }
            } catch (InterruptedException e) {
                CustomLogger.d(TAG, "ExecutorService termination interrupted");
                Thread.currentThread().interrupt();
            }
        }

        // Shutdown AppModule's executor
        if (isFinishing()) {
            CustomLogger.d(TAG, "Activity is finishing, shutting down AppModule's executor");
            appModule.shutdownExecutorService();
        }

        // Clear fragment reference
        videoPlayerFragment = null;

        CustomLogger.d(TAG, "Activity destroyed, resources released");
    }

    @Override
    public void onBackPressed() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBackPressTime < DOUBLE_BACK_PRESS_INTERVAL) {
            // Second back press within 2 seconds, launch default launcher
            CustomLogger.d(TAG, "Double back press detected, launching default launcher");
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                startActivity(homeIntent);
                CustomLogger.d(TAG, "Default launcher launched");
                // Finish MainActivity to exit app
                finish();
            } catch (Exception e) {
                CustomLogger.e(TAG, "Failed to launch default launcher: " + e.getMessage(), e);
                super.onBackPressed(); // Fallback to default behavior
            }
        } else {
            // First back press, update timestamp
            lastBackPressTime = currentTime;
            CustomLogger.d(TAG, "First back press, waiting for second press");
            // Optionally notify user (e.g., Toast, but TVs may not show it)
        }
    }
}