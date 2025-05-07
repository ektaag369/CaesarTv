package com.example.caesartv.presentation.main;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
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
    private MainViewmodel viewModel; // Corrected typo from MainViewmodel to MainViewModel
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
        Log.d(TAG, "Splash screen displayed");

        // Initialize in background
        executorService.execute(() -> {
            // Preload VideoPlayerFragment with callback
            videoPlayerFragment = VideoPlayerFragment.newInstance((Void unused) -> {
                mainHandler.post(() -> {
                    isSplashDisplayed = false;
                    splashLogo.setVisibility(View.GONE);
                    Log.d(TAG, "Splash screen hidden after video ready");
                });
            });
            Log.d(TAG, "VideoPlayerFragment preloaded");

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
            Log.w(TAG, "Device is blocked, showing blocked screen");
            // Remove VideoPlayerFragment if it exists
            if (videoPlayerFragment != null && videoPlayerFragment.isAdded()) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .remove(videoPlayerFragment)
                        .commit();
                Log.d(TAG, "VideoPlayerFragment removed due to device block");
            }
            // Close app after 3 seconds
            mainHandler.postDelayed(this::finish, BLOCKED_CLOSE_DELAY_MS);
            Log.d(TAG, "Scheduled app closure in " + BLOCKED_CLOSE_DELAY_MS + "ms");
        } else {
            setContentView(R.layout.activity_main);
            // Reinitialize splash logo since layout is reset
            splashLogo = findViewById(R.id.splash_logo);
            splashLogo.setVisibility(View.VISIBLE);
            checkCachedMediaAndStartVideoPlayer();
            observeViewModel();
        }
    }

    private void checkCachedMediaAndStartVideoPlayer() {
        Log.d(TAG, "Checking cached media");
        executorService.execute(() -> {
            List<MediaItem> cachedMedia = cachedMediaUseCase.execute();
            mainHandler.post(() -> {
                if (!cachedMedia.isEmpty()) {
                    Log.d(TAG, "Cached media available: " + cachedMedia.size() + " items");
                    viewModel.getMediaItems().postValue(cachedMedia);
                    startVideoPlayer();
                } else {
                    Log.d(TAG, "No cached media, waiting for API media");
                    waitForMediaAndStartVideoPlayer();
                }
            });
        });
    }

    private void waitForMediaAndStartVideoPlayer() {
        Log.d(TAG, "Waiting for media items");
        long startTime = System.currentTimeMillis();
        Runnable checkMedia = new Runnable() {
            @Override
            public void run() {
                if (isDeviceBlocked) {
                    Log.d(TAG, "Device is blocked, skipping media check");
                    return;
                }
                if (viewModel.getMediaItems().getValue() != null && !viewModel.getMediaItems().getValue().isEmpty()) {
                    Log.d(TAG, "Media items available: " + viewModel.getMediaItems().getValue().size());
                    startVideoPlayer();
                } else if (System.currentTimeMillis() - startTime < MEDIA_CHECK_TIMEOUT_MS) {
                    Log.d(TAG, "No media yet, retrying...");
                    mainHandler.postDelayed(this, 500);
                } else {
                    Log.w(TAG, "Media fetch timeout, checking cached media");
                    checkCachedMediaAndStartVideoPlayer();
                }
            }
        };
        mainHandler.post(checkMedia);
    }

    private void observeViewModel() {
        viewModel.getMediaItems().observe(this, mediaItems -> {
            Log.d(TAG, "mediaItems received: " + (mediaItems != null ? mediaItems.size() : "null"));
            if (mediaItems != null && !mediaItems.isEmpty() && !isSplashDisplayed && !isDeviceBlocked) {
                Log.d(TAG, "Media items received, starting VideoPlayerFragment");
                startVideoPlayer();
            } else {
                Log.d(TAG, "No media items, splash screen displayed, or device blocked");
            }
        });

        viewModel.getIsDeviceBlocked().observe(this, isBlocked -> {
            Log.d(TAG, "isDeviceBlocked: " + isBlocked);
            boolean newBlockStatus = isBlocked != null && isBlocked;
            if (newBlockStatus != isDeviceBlocked) {
                isDeviceBlocked = newBlockStatus;
                if (isDeviceBlocked) {
                    setContentView(R.layout.activity_blocked);
                    Log.w(TAG, "Device is blocked, showing blocked screen");
                    // Stop and remove VideoPlayerFragment
                    if (videoPlayerFragment != null && videoPlayerFragment.isAdded()) {
                        videoPlayerFragment.pauseVideo();
                        getSupportFragmentManager()
                                .beginTransaction()
                                .remove(videoPlayerFragment)
                                .commit();
                        Log.d(TAG, "VideoPlayerFragment paused and removed due to device block");
                    }
                    // Close app after 3 seconds
                    mainHandler.postDelayed(this::finish, BLOCKED_CLOSE_DELAY_MS);
                    Log.d(TAG, "Scheduled app closure in " + BLOCKED_CLOSE_DELAY_MS + "ms");
                } else {
                    setContentView(R.layout.activity_main);
                    // Reinitialize splash logo
                    splashLogo = findViewById(R.id.splash_logo);
                    splashLogo.setVisibility(View.VISIBLE);
                    Log.d(TAG, "Device unblocked, resuming normal flow");
                    // Reinitialize VideoPlayerFragment and force media check
                    videoPlayerFragment = VideoPlayerFragment.newInstance((Void unused) -> {
                        mainHandler.post(() -> {
                            isSplashDisplayed = false;
                            splashLogo.setVisibility(View.GONE);
                            Log.d(TAG, "Splash screen hidden after video ready");
                        });
                    });
                    checkCachedMediaAndStartVideoPlayer();
                }
            }
        });
    }

    private void startVideoPlayer() {
        if (isDeviceBlocked) {
            Log.d(TAG, "Device is blocked, not starting video player");
            return;
        }
        Log.d(TAG, "Starting video player");
        if (videoPlayerFragment == null || videoPlayerFragment.isDetached()) {
            videoPlayerFragment = VideoPlayerFragment.newInstance((Void unused) -> {
                mainHandler.post(() -> {
                    isSplashDisplayed = false;
                    splashLogo.setVisibility(View.GONE);
                    Log.d(TAG, "Splash screen hidden after video ready");
                });
            });
            Log.d(TAG, "Created new VideoPlayerFragment instance");
        }
        if (!videoPlayerFragment.isAdded()) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_browse_fragment, videoPlayerFragment)
                    .commitAllowingStateLoss();
            Log.d(TAG, "Fragment transaction committed");
        } else {
            Log.d(TAG, "VideoPlayerFragment already added");
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
            mainHandler.removeCallbacksAndMessages(null);
        }

        // Shutdown ExecutorService
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    Log.w(TAG, "ExecutorService did not terminate");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Shutdown AppModule's executor
        if (isFinishing()) {
            appModule.shutdownExecutorService();
        }

        // Clear fragment reference
        videoPlayerFragment = null;

        Log.d(TAG, "Activity destroyed, resources released");
    }
}