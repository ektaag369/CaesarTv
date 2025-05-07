package com.example.caesartv.presentation.player;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.caesartv.R;
import com.example.caesartv.domain.model.MediaUrl;
import com.example.caesartv.presentation.main.MainActivity;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class VideoPlayerFragment extends Fragment {

    private static final String TAG = "VideoPlayerFragment";
    private VideoPlayerViewModel viewModel;
    private ExoPlayer playerFull;
    private ExoPlayer playerLeft;
    private ExoPlayer playerRight;
    private PlayerView playerViewFull;
    private PlayerView playerViewLeft;
    private PlayerView playerViewRight;
    private ImageView imageViewLeft;
    private ImageView imageViewRight;
    private LinearLayout splitScreenContainer;
    private ProgressBar loadingSpinner;
    private String currentMediaId;
    private boolean isRetryingMedia; // Track if retrying to allow same media ID
    private Consumer<Void> onVideoReadyCallback;
    private boolean isFirstPlayback = true;
    private Handler mainHandler;
    private int multipleMediaCompletionCount = 0;
    private boolean isHandlingMultipleMedia = false;

    public static VideoPlayerFragment newInstance(Consumer<Void> onVideoReadyCallback) {
        VideoPlayerFragment fragment = new VideoPlayerFragment();
        fragment.onVideoReadyCallback = onVideoReadyCallback;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_video_player, container, false);
        playerViewFull = view.findViewById(R.id.video_view_full);
        playerViewLeft = view.findViewById(R.id.video_view_left);
        playerViewRight = view.findViewById(R.id.video_view_right);
        imageViewLeft = view.findViewById(R.id.image_view_left);
        imageViewRight = view.findViewById(R.id.image_view_right);
        splitScreenContainer = view.findViewById(R.id.split_screen_container);
        loadingSpinner = view.findViewById(R.id.loading_spinner);
        loadingSpinner.setVisibility(View.GONE);
        Log.d(TAG, "View initialized");
        mainHandler = new Handler(Looper.getMainLooper());
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializePlayers();
        initializeViewModel();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initializePlayers() {
        playerFull = new ExoPlayer.Builder(requireContext()).build();
        playerViewFull.setPlayer(playerFull);
        playerViewFull.setControllerAutoShow(false);
        playerFull.addListener(createPlayerListener("Full"));

        playerLeft = new ExoPlayer.Builder(requireContext()).build();
        playerViewLeft.setPlayer(playerLeft);
        playerViewLeft.setControllerAutoShow(false);
        playerLeft.addListener(createPlayerListener("Left"));

        playerRight = new ExoPlayer.Builder(requireContext()).build();
        playerViewRight.setPlayer(playerRight);
        playerViewRight.setControllerAutoShow(false);
        playerRight.addListener(createPlayerListener("Right"));
    }

    private Player.Listener createPlayerListener(String playerName) {
        return new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case Player.STATE_BUFFERING:
                        Log.d(TAG, playerName + ": Buffering");
                        if (!isFirstPlayback) {
                            loadingSpinner.setVisibility(View.VISIBLE);
                        }
                        break;
                    case Player.STATE_READY:
                        Log.d(TAG, playerName + ": Ready to play");
                        loadingSpinner.setVisibility(View.GONE);
                        if (isFirstPlayback) {
                            isFirstPlayback = false;
                            if (onVideoReadyCallback != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    onVideoReadyCallback.accept(null);
                                }
                                onVideoReadyCallback = null;
                            }
                        }
                        break;
                    case Player.STATE_ENDED:
                        Log.d(TAG, playerName + ": Playback ended");
                        loadingSpinner.setVisibility(View.GONE);
                        if (isHandlingMultipleMedia) {
                            multipleMediaCompletionCount++;
                            Log.d(TAG, playerName + ": Completion count = " + multipleMediaCompletionCount);
                            if (multipleMediaCompletionCount >= 2 || !hasMultipleVideos()) {
                                isHandlingMultipleMedia = false;
                                viewModel.handleVideoEnd();
                            }
                        } else {
                            viewModel.handleVideoEnd();
                        }
                        break;
                }
            }

            @Override
            public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                Log.e(TAG, playerName + ": ExoPlayer error: " + error.getMessage() + ", Error code: " + error.errorCode, error);
                if (error.getCause() != null) {
                    Log.e(TAG, playerName + ": Error cause: " + error.getCause().getMessage());
                }
                loadingSpinner.setVisibility(View.GONE);
                if (error.errorCode == androidx.media3.exoplayer.ExoPlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                        error.errorCode == androidx.media3.exoplayer.ExoPlaybackException.ERROR_CODE_DECODING_FAILED) {
                    Log.w(TAG, playerName + ": Decoder error detected, attempting remote playback");
                    isRetryingMedia = true; // Allow retry with same media ID
                    viewModel.retryCurrentMedia();
                } else {
                    if (isHandlingMultipleMedia) {
                        multipleMediaCompletionCount++;
                        Log.d(TAG, playerName + ": Completion count = " + multipleMediaCompletionCount);
                        if (multipleMediaCompletionCount >= 2 || !hasMultipleVideos()) {
                            isHandlingMultipleMedia = false;
                            viewModel.handleVideoEnd();
                        }
                    } else {
                        viewModel.handleVideoEnd();
                    }
                }
            }
        };
    }

    private boolean hasMultipleVideos() {
        com.example.caesartv.domain.model.MediaItem media = viewModel.getCurrentMedia().getValue();
        if (media == null || !"MULTIPLE".equals(media.getMediaType())) {
            return false;
        }
        List<MediaUrl> multipleUrls = media.getMultipleUrl();
        return multipleUrls != null && multipleUrls.size() >= 2 &&
                "video".equals(multipleUrls.get(0).getUrlType()) &&
                "video".equals(multipleUrls.get(1).getUrlType());
    }

    private void initializeViewModel() {
        VideoPlayerViewModel.Factory factory = new VideoPlayerViewModel.Factory(
                ((MainActivity) requireActivity()).getCachedMediaUseCase(),
                requireContext()
        );
        viewModel = new ViewModelProvider(this, factory).get(VideoPlayerViewModel.class);

        viewModel.getCurrentMedia().observe(getViewLifecycleOwner(), media -> {
            if (media == null) {
                Log.w(TAG, "No media to play, closing app");
                // Ensure players are stopped and released before closing
                stopAllPlayers();
                releaseAllPlayers();
                // Close the app
                if (requireActivity() != null && !requireActivity().isFinishing()) {
                    requireActivity().finish();
                }
                return;
            }
            if (media.getId().equals(currentMediaId) && !isRetryingMedia) {
                Log.d(TAG, "Ignoring duplicate media: " + media.getTitle());
                return;
            }
            currentMediaId = media.getId();
            isRetryingMedia = false; // Reset retry flag after processing
            Log.d(TAG, "Processing media: " + media.getTitle() + ", Type: " + media.getMediaType());

            resetUI();

            if ("SINGLE".equals(media.getMediaType())) {
                handleSingleMedia(media);
            } else if ("MULTIPLE".equals(media.getMediaType())) {
                handleMultipleMedia(media);
            } else {
                Log.e(TAG, "Unknown media type: " + media.getMediaType());
                viewModel.handleVideoEnd();
            }
        });
    }

    private void resetUI() {
        playerViewFull.setVisibility(View.GONE);
        splitScreenContainer.setVisibility(View.GONE);
        playerViewLeft.setVisibility(View.GONE);
        playerViewRight.setVisibility(View.GONE);
        imageViewLeft.setVisibility(View.GONE);
        imageViewRight.setVisibility(View.GONE);
        loadingSpinner.setVisibility(View.GONE);
        stopAllPlayers();
        // Clear Glide images
        Glide.with(this).clear(imageViewLeft);
        Glide.with(this).clear(imageViewRight);
        // Reset completion tracking
        multipleMediaCompletionCount = 0;
        isHandlingMultipleMedia = false;
    }

    private void handleSingleMedia(com.example.caesartv.domain.model.MediaItem media) {
        playerViewFull.setVisibility(View.VISIBLE);
        String localFilePath = media.getLocalFilePath() != null ? media.getLocalFilePath() : media.getUrl();
        Log.d(TAG, "Playing SINGLE media: " + media.getTitle() + ", Path: " + localFilePath);

        File file = new File(localFilePath);
        Log.d(TAG, "File check: Path=" + localFilePath + ", Exists=" + file.exists() + ", CanRead=" + file.canRead() + ", Size=" + (file.exists() ? file.length() : 0));

        if (localFilePath != null && file.exists() && file.canRead()) {
            if (!supports4KDecoding() && isLikely4KVideo(localFilePath)) {
                Log.w(TAG, "Device does not support 4K for SINGLE media, attempting remote playback");
                attemptRemotePlayback(media, playerFull, playerViewFull, "Full");
            } else {
                try {
                    playVideoInView(playerFull, playerViewFull, localFilePath, "Full");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to play local SINGLE media: " + media.getTitle(), e);
                    attemptRemotePlayback(media, playerFull, playerViewFull, "Full");
                }
            }
        } else if (isNetworkAvailable() && media.getUrl() != null) {
            Log.d(TAG, "Playing remote SINGLE media: " + media.getUrl());
            attemptRemotePlayback(media, playerFull, playerViewFull, "Full");
        } else {
            Log.e(TAG, "Invalid or missing video file for SINGLE media: " + localFilePath);
            viewModel.handleVideoEnd();
        }
    }

    private void handleMultipleMedia(com.example.caesartv.domain.model.MediaItem media) {
        splitScreenContainer.setVisibility(View.VISIBLE);
        List<MediaUrl> multipleUrls = media.getMultipleUrl();
        if (multipleUrls == null || multipleUrls.size() < 2) {
            Log.e(TAG, "MULTIPLE media requires at least 2 URLs, found: " + (multipleUrls == null ? 0 : multipleUrls.size()));
            viewModel.handleVideoEnd();
            return;
        }

        // Reset completion tracking
        multipleMediaCompletionCount = 0;
        isHandlingMultipleMedia = true;

        boolean supports4K = supports4KDecoding();

        MediaUrl leftUrl = multipleUrls.get(0);
        String leftPath = leftUrl.getLocalFilePath() != null ? leftUrl.getLocalFilePath() : leftUrl.getUrl();
        if ("video".equals(leftUrl.getUrlType())) {
            if (leftPath != null) {
                File leftFile = new File(leftPath);
                Log.d(TAG, "Left video check: Path=" + leftPath + ", Exists=" + leftFile.exists() + ", CanRead=" + leftFile.canRead() + ", Size=" + (leftFile.exists() ? leftFile.length() : 0));
                if (leftFile.exists() && leftFile.canRead()) {
                    if (!supports4K && isLikely4KVideo(leftPath)) {
                        Log.w(TAG, "Device does not support 4K for Left video, attempting remote playback");
                        attemptRemotePlayback(leftUrl, playerLeft, playerViewLeft, "Left");
                    } else {
                        try {
                            playVideoInView(playerLeft, playerViewLeft, leftPath, "Left");
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to play local Left video: " + leftPath, e);
                            attemptRemotePlayback(leftUrl, playerLeft, playerViewLeft, "Left");
                        }
                    }
                } else if (isNetworkAvailable() && leftUrl.getUrl() != null) {
                    Log.d(TAG, "Playing remote Left video: " + leftUrl.getUrl());
                    attemptRemotePlayback(leftUrl, playerLeft, playerViewLeft, "Left");
                } else {
                    Log.e(TAG, "Invalid or missing video file for Left: " + leftPath);
                    mainHandler.post(() -> {
                        if (isHandlingMultipleMedia) {
                            multipleMediaCompletionCount++;
                            Log.d(TAG, "Left: Completion count = " + multipleMediaCompletionCount);
                            if (multipleMediaCompletionCount >= 2 || !hasMultipleVideos()) {
                                isHandlingMultipleMedia = false;
                                viewModel.handleVideoEnd();
                            }
                        }
                    });
                }
            } else {
                Log.w(TAG, "No valid video path for Left");
                mainHandler.post(() -> {
                    if (isHandlingMultipleMedia) {
                        multipleMediaCompletionCount++;
                        Log.d(TAG, "Left: Completion count = " + multipleMediaCompletionCount);
                        if (multipleMediaCompletionCount >= 2 || !hasMultipleVideos()) {
                            isHandlingMultipleMedia = false;
                            viewModel.handleVideoEnd();
                        }
                    }
                });
            }
        } else if ("image".equals(leftUrl.getUrlType())) {
            loadImageInView(imageViewLeft, leftPath, "Left");
        }

        MediaUrl rightUrl = multipleUrls.get(1);
        String rightPath = rightUrl.getLocalFilePath() != null ? rightUrl.getLocalFilePath() : rightUrl.getUrl();
        if ("video".equals(rightUrl.getUrlType())) {
            if (rightPath != null) {
                File rightFile = new File(rightPath);
                Log.d(TAG, "Right video check: Path=" + rightPath + ", Exists=" + rightFile.exists() + ", CanRead=" + rightFile.canRead() + ", Size=" + (rightFile.exists() ? rightFile.length() : 0));
                if (rightFile.exists() && rightFile.canRead()) {
                    if (!supports4K && isLikely4KVideo(rightPath)) {
                        Log.w(TAG, "Device does not support 4K for Right video, attempting remote playback");
                        attemptRemotePlayback(rightUrl, playerRight, playerViewRight, "Right");
                    } else {
                        try {
                            playVideoInView(playerRight, playerViewRight, rightPath, "Right");
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to play local Right video: " + rightPath, e);
                            attemptRemotePlayback(rightUrl, playerRight, playerViewRight, "Right");
                        }
                    }
                } else if (isNetworkAvailable() && rightUrl.getUrl() != null) {
                    Log.d(TAG, "Playing remote Right video: " + rightUrl.getUrl());
                    attemptRemotePlayback(rightUrl, playerRight, playerViewRight, "Right");
                } else {
                    Log.e(TAG, "Invalid or missing video file for Right: " + rightPath);
                    mainHandler.post(() -> {
                        if (isHandlingMultipleMedia) {
                            multipleMediaCompletionCount++;
                            Log.d(TAG, "Right: Completion count = " + multipleMediaCompletionCount);
                            if (multipleMediaCompletionCount >= 2 || !hasMultipleVideos()) {
                                isHandlingMultipleMedia = false;
                                viewModel.handleVideoEnd();
                            }
                        }
                    });
                }
            } else {
                Log.w(TAG, "No valid video path for Right");
                mainHandler.post(() -> {
                    if (isHandlingMultipleMedia) {
                        multipleMediaCompletionCount++;
                        Log.d(TAG, "Right: Completion count = " + multipleMediaCompletionCount);
                        if (multipleMediaCompletionCount >= 2 || !hasMultipleVideos()) {
                            isHandlingMultipleMedia = false;
                            viewModel.handleVideoEnd();
                        }
                    }
                });
            }
        } else if ("image".equals(rightUrl.getUrlType())) {
            loadImageInView(imageViewRight, rightPath, "Right");
        }

        viewModel.setStartTime(System.currentTimeMillis());

        // Add timeout to prevent indefinite stall
        mainHandler.postDelayed(() -> {
            if (isHandlingMultipleMedia && multipleMediaCompletionCount < 2) {
                Log.w(TAG, "Timeout reached for multiple media, forcing completion");
                isHandlingMultipleMedia = false;
                multipleMediaCompletionCount = 2;
                viewModel.handleVideoEnd();
            }
        }, 10000); // 10-second timeout
    }

    private void attemptRemotePlayback(com.example.caesartv.domain.model.MediaItem media, ExoPlayer player, PlayerView playerView, String viewName) {
        if (media.getUrl() == null) {
            Log.e(TAG, "No remote URL available for media: " + media.getTitle() + " in " + viewName);
            viewModel.handleVideoEnd();
            return;
        }
        Log.d(TAG, "Attempting remote playback in " + viewName + ": " + media.getUrl());
        try {
            playVideoInView(player, playerView, media.getUrl(), viewName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play remote media in " + viewName + ": " + media.getTitle(), e);
            viewModel.handleVideoEnd();
        }
    }

    private void attemptRemotePlayback(MediaUrl mediaUrl, ExoPlayer player, PlayerView playerView, String viewName) {
        if (mediaUrl.getUrl() == null) {
            Log.e(TAG, "No remote URL available for media URL in " + viewName);
            if (isHandlingMultipleMedia) {
                mainHandler.post(() -> {
                    multipleMediaCompletionCount++;
                    Log.d(TAG, viewName + ": Completion count = " + multipleMediaCompletionCount);
                    if (multipleMediaCompletionCount >= 2 || !hasMultipleVideos()) {
                        isHandlingMultipleMedia = false;
                        viewModel.handleVideoEnd();
                    }
                });
            } else {
                viewModel.handleVideoEnd();
            }
            return;
        }
        Log.d(TAG, "Attempting remote playback in " + viewName + ": " + mediaUrl.getUrl());
        try {
            playVideoInView(player, playerView, mediaUrl.getUrl(), viewName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play remote media URL in " + viewName + ": " + mediaUrl.getUrl(), e);
            if (isHandlingMultipleMedia) {
                mainHandler.post(() -> {
                    multipleMediaCompletionCount++;
                    Log.d(TAG, viewName + ": Completion count = " + multipleMediaCompletionCount);
                    if (multipleMediaCompletionCount >= 2 || !hasMultipleVideos()) {
                        isHandlingMultipleMedia = false;
                        viewModel.handleVideoEnd();
                    }
                });
            } else {
                viewModel.handleVideoEnd();
            }
        }
    }

    private void playVideoInView(ExoPlayer player, PlayerView playerView, String path, String viewName) {
        playerView.setVisibility(View.VISIBLE);
        Log.d(TAG, "Playing video in " + viewName + ": " + path);
        Uri uri = path.startsWith("/") ? Uri.fromFile(new File(path)) : Uri.parse(path);
        MediaItem mediaItem = MediaItem.fromUri(uri);
        player.stop();
        player.clearMediaItems();
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    private void loadImageInView(ImageView imageView, String url, String viewName) {
        imageView.setVisibility(View.VISIBLE);
        Log.d(TAG, "Loading image in " + viewName + ": " + url);
        try {
            Glide.with(this)
                    .load(url)
                    .error(R.drawable.ic_error)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Failed to load image in " + viewName + ": " + url, e);
                            mainHandler.post(() -> {
                                if (isHandlingMultipleMedia) {
                                    multipleMediaCompletionCount++;
                                    Log.d(TAG, viewName + ": Completion count = " + multipleMediaCompletionCount);
                                    if (multipleMediaCompletionCount >= 2 || !hasMultipleVideos()) {
                                        isHandlingMultipleMedia = false;
                                        viewModel.handleVideoEnd();
                                    }
                                } else {
                                    viewModel.handleVideoEnd();
                                }
                            });
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            Log.d(TAG, "Image loaded in " + viewName + ": " + url);
                            mainHandler.postDelayed(() -> {
                                Log.d(TAG, "Image display completed in " + viewName);
                                if (isHandlingMultipleMedia) {
                                    multipleMediaCompletionCount++;
                                    Log.d(TAG, viewName + ": Completion count = " + multipleMediaCompletionCount);
                                    if (multipleMediaCompletionCount >= 2 || !hasMultipleVideos()) {
                                        isHandlingMultipleMedia = false;
                                        viewModel.handleVideoEnd();
                                    }
                                } else {
                                    viewModel.handleVideoEnd();
                                }
                            }, 3000);
                            return false;
                        }
                    })
                    .into(imageView);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load image in " + viewName + ": " + url, e);
            mainHandler.post(() -> {
                if (isHandlingMultipleMedia) {
                    multipleMediaCompletionCount++;
                    Log.d(TAG, viewName + ": Completion count = " + multipleMediaCompletionCount);
                    if (multipleMediaCompletionCount >= 2 || !hasMultipleVideos()) {
                        isHandlingMultipleMedia = false;
                        viewModel.handleVideoEnd();
                    }
                } else {
                    viewModel.handleVideoEnd();
                }
            });
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private boolean supports4KDecoding() {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (codecInfo.isEncoder()) continue;
            for (String type : codecInfo.getSupportedTypes()) {
                if (type.equals("video/avc")) {
                    MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(type);
                    if (caps.getVideoCapabilities().areSizeAndRateSupported(3840, 2160, 30)) {
                        Log.d(TAG, "Device supports 4K H.264 decoding");
                        return true;
                    }
                }
            }
        }
        Log.d(TAG, "Device does not support 4K H.264 decoding");
        return false;
    }

    private boolean isLikely4KVideo(String path) {
        if (path == null) {
            Log.d(TAG, "Video path is null, assuming not 4K");
            return false;
        }
        if (path.startsWith("http")) {
            Log.d(TAG, "Remote URL detected, assuming 4K: " + path);
            return true; // Assume remote URLs are 4K to force remote playback
        }
        File file = new File(path);
        if (file.exists()) {
            long size = file.length();
            boolean isLarge = size > 50 * 1024 * 1024; // 50MB threshold
            Log.d(TAG, "Video size check: Path=" + path + ", Size=" + size + ", Likely 4K=" + isLarge);
            return isLarge;
        }
        Log.d(TAG, "Video file does not exist: " + path);
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        pauseAllPlayers();
        loadingSpinner.setVisibility(View.GONE);
        Log.d(TAG, "Fragment paused");
    }

    public void pauseVideo() {
        pauseAllPlayers();
    }

    private void pauseAllPlayers() {
        if (playerFull != null) {
            playerFull.pause();
            Log.d(TAG, "Full player paused");
        }
        if (playerLeft != null) {
            playerLeft.pause();
            Log.d(TAG, "Left player paused");
        }
        if (playerRight != null) {
            playerRight.pause();
            Log.d(TAG, "Right player paused");
        }
    }

    private void stopAllPlayers() {
        if (playerFull != null) {
            playerFull.stop();
            playerFull.clearMediaItems();
            Log.d(TAG, "Full player stopped");
        }
        if (playerLeft != null) {
            playerLeft.stop();
            playerLeft.clearMediaItems();
            Log.d(TAG, "Left player stopped");
        }
        if (playerRight != null) {
            playerRight.stop();
            playerRight.clearMediaItems();
            Log.d(TAG, "Right player stopped");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAllPlayers();
        releaseAllPlayers();
        loadingSpinner.setVisibility(View.GONE);
        mainHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "View destroyed");
    }

    private void releaseAllPlayers() {
        if (playerFull != null) {
            playerFull.release();
            playerFull = null;
            Log.d(TAG, "Full player released");
        }
        if (playerLeft != null) {
            playerLeft.release();
            playerLeft = null;
            Log.d(TAG, "Left player released");
        }
        if (playerRight != null) {
            playerRight.release();
            playerRight = null;
            Log.d(TAG, "Right player released");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAllPlayers();
        releaseAllPlayers();
        mainHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "Fragment destroyed");
    }
}