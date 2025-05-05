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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private MainViewmodel viewModel;
    private Handler mainHandler;
    private GetCachedMediaUseCase cachedMediaUseCase;
    private AppModule appModule;
    private boolean isSplashDisplayed = true;
    private VideoPlayerFragment videoPlayerFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());
        appModule = new AppModule();
        cachedMediaUseCase = appModule.provideGetCachedMediaUseCase(this);
        viewModel = appModule.provideMainViewModel(this);

        // Show splash screen
        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) ImageView splashLogo = findViewById(R.id.splash_logo);
        splashLogo.setVisibility(View.VISIBLE);
        Log.d(TAG, "Splash screen displayed");

        // Preload VideoPlayerFragment
        videoPlayerFragment = new VideoPlayerFragment();
        Log.d(TAG, "VideoPlayerFragment preloaded");

        // Set up observers and transition after 5 seconds
        mainHandler.postDelayed(() -> {
            isSplashDisplayed = false;
            splashLogo.setVisibility(View.GONE);
            Log.d(TAG, "Splash screen hidden");
            setContentView(R.layout.activity_main);
            startVideoPlayer();
            observeViewModel();
            // Initialize isDeviceBlocked to ensure observers are set up
            viewModel.getIsDeviceBlocked().postValue(false);
            // Check media items immediately
            if (viewModel.getMediaItems().getValue() != null && !viewModel.getMediaItems().getValue().isEmpty()) {
                Log.d(TAG, "Media items available, VideoPlayerFragment already started");
            } else {
                Log.d(TAG, "No media items yet, VideoPlayerFragment already started");
            }
        }, 5000);
    }

    private void observeViewModel() {
        viewModel.getMediaItems().observe(this, mediaItems -> {
            Log.d(TAG, "mediaItems received: " + (mediaItems != null ? mediaItems.size() : "null"));
            if (mediaItems != null && !mediaItems.isEmpty() && !isSplashDisplayed) {
                Log.d(TAG, "Media items received, VideoPlayerFragment already started");
            } else {
                Log.d(TAG, "No media items available or splash screen still displayed");
            }
        });

        viewModel.getIsDeviceBlocked().observe(this, isBlocked -> {
            Log.d(TAG, "isDeviceBlocked: " + isBlocked);
            if (isBlocked != null && isBlocked && !isSplashDisplayed) {
                setContentView(R.layout.activity_blocked);
                Log.w(TAG, "Device is blocked, showing blocked screen");
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
        }
    }
}