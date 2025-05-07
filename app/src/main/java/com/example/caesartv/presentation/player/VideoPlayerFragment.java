package com.example.caesartv.presentation.player;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
    private Consumer<Void> onVideoReadyCallback;
    private boolean isFirstPlayback = true;

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
                        viewModel.handleVideoEnd();
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
                    viewModel.retryCurrentMedia();
                } else {
                    viewModel.handleVideoEnd();
                }
            }
        };
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
                if (requireActivity() != null) {
                    requireActivity().finish();
                }
                return;
            }
            if (media.getId().equals(currentMediaId)) {
                Log.d(TAG, "Ignoring duplicate media: " + media.getTitle());
                return;
            }
            currentMediaId = media.getId();
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
        if (playerFull != null) playerFull.stop();
        if (playerLeft != null) playerLeft.stop();
        if (playerRight != null) playerRight.stop();
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
                    attemptRemotePlayback(leftUrl, playerLeft, playerViewLeft, "Left");
                } else {
                    Log.e(TAG, "Invalid or missing video file for Left: " + leftPath);
                    viewModel.handleVideoEnd();
                }
            } else {
                Log.w(TAG, "No valid video path for Left");
                viewModel.handleVideoEnd();
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
                    attemptRemotePlayback(rightUrl, playerRight, playerViewRight, "Right");
                } else {
                    Log.e(TAG, "Invalid or missing video file for Right: " + rightPath);
                    viewModel.handleVideoEnd();
                }
            } else {
                Log.w(TAG, "No valid video path for Right");
                viewModel.handleVideoEnd();
            }
        } else if ("image".equals(rightUrl.getUrlType())) {
            loadImageInView(imageViewRight, rightPath, "Right");
        }

        viewModel.setStartTime(System.currentTimeMillis());
    }

    private void attemptRemotePlayback(com.example.caesartv.domain.model.MediaItem media, ExoPlayer player, PlayerView playerView, String viewName) {
        if (media.getUrl() == null) {
            Log.e(TAG, "No remote URL available for media: " + media.getTitle() + " in " + viewName);
            viewModel.handleVideoEnd();
            return;
        }
        Log.d(TAG, "Falling back to remote URL in " + viewName + ": " + media.getUrl());
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
            viewModel.handleVideoEnd();
            return;
        }
        Log.d(TAG, "Falling back to remote URL in " + viewName + ": " + mediaUrl.getUrl());
        try {
            playVideoInView(player, playerView, mediaUrl.getUrl(), viewName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play remote media URL in " + viewName + ": " + mediaUrl.getUrl(), e);
            viewModel.handleVideoEnd();
        }
    }

    private void playVideoInView(ExoPlayer player, PlayerView playerView, String path, String viewName) {
        playerView.setVisibility(View.VISIBLE);
        Log.d(TAG, "Playing video in " + viewName + ": " + path);
        Uri uri = path.startsWith("/") ? Uri.fromFile(new File(path)) : Uri.parse(path);
        MediaItem mediaItem = MediaItem.fromUri(uri);
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
                    .into(imageView);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load image in " + viewName + ": " + url, e);
            viewModel.handleVideoEnd();
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
        if (path.startsWith("http")) {
            return true;
        }
        File file = new File(path);
        if (file.exists()) {
            long size = file.length();
            boolean isLarge = size > 50 * 1024 * 1024;
            Log.d(TAG, "Video size check: Path=" + path + ", Size=" + size + ", Likely 4K=" + isLarge);
            return isLarge;
        }
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        releaseAllPlayers();
        loadingSpinner.setVisibility(View.GONE);
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
        releaseAllPlayers(); // Ensure players are released even if view is not destroyed
        Log.d(TAG, "Fragment destroyed");
    }
}