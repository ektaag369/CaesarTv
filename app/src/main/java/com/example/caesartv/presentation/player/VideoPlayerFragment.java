package com.example.caesartv.presentation.player;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
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
        // Full-screen player for SINGLE media
        playerFull = new ExoPlayer.Builder(requireContext()).build();
        playerViewFull.setPlayer(playerFull);
        playerViewFull.setControllerAutoShow(false);
        playerFull.addListener(createPlayerListener("Full"));

        // Left player for MULTIPLE media
        playerLeft = new ExoPlayer.Builder(requireContext()).build();
        playerViewLeft.setPlayer(playerLeft);
        playerViewLeft.setControllerAutoShow(false);
        playerLeft.addListener(createPlayerListener("Left"));

        // Right player for MULTIPLE media
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
                        loadingSpinner.setVisibility(View.VISIBLE);
                        break;
                    case Player.STATE_READY:
                        Log.d(TAG, playerName + ": Ready to play");
                        loadingSpinner.setVisibility(View.GONE);
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
                Log.e(TAG, playerName + ": ExoPlayer error: " + error.getMessage(), error);
                loadingSpinner.setVisibility(View.GONE);
                viewModel.handleVideoEnd();
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
                requireActivity().finish();
                return;
            }
            if (media.getId().equals(currentMediaId)) {
                Log.d(TAG, "Ignoring duplicate media: " + media.getTitle());
                return;
            }
            currentMediaId = media.getId();
            Log.d(TAG, "Processing media: " + media.getTitle() + ", Type: " + media.getMediaType());

            // Reset UI
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
        loadingSpinner.setVisibility(View.VISIBLE);
        if (playerFull != null) playerFull.stop();
        if (playerLeft != null) playerLeft.stop();
        if (playerRight != null) playerRight.stop();
    }

    private void handleSingleMedia(com.example.caesartv.domain.model.MediaItem media) {
        playerViewFull.setVisibility(View.VISIBLE);
        String localFilePath = media.getLocalFilePath() != null ? media.getLocalFilePath() : media.getUrl();
        Log.d(TAG, "Playing SINGLE media: " + media.getTitle() + ", Path: " + localFilePath);
        if (localFilePath != null && new File(localFilePath).exists()) {
            try {
                Uri uri = Uri.fromFile(new File(localFilePath));
                MediaItem mediaItem = MediaItem.fromUri(uri);
                playerFull.setMediaItem(mediaItem);
                playerFull.prepare();
                playerFull.play();
                viewModel.setStartTime(System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Failed to play SINGLE media: " + media.getTitle(), e);
                viewModel.handleVideoEnd();
            }
        } else if (isNetworkAvailable() && media.getUrl() != null) {
            try {
                Uri uri = Uri.parse(media.getUrl());
                MediaItem mediaItem = MediaItem.fromUri(uri);
                playerFull.setMediaItem(mediaItem);
                playerFull.prepare();
                playerFull.play();
                viewModel.setStartTime(System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Failed to play SINGLE media remotely: " + media.getTitle(), e);
                viewModel.handleVideoEnd();
            }
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

        // Handle left half (index 0)
        MediaUrl leftUrl = multipleUrls.get(0);
        String leftPath = leftUrl.getLocalFilePath() != null && new File(leftUrl.getLocalFilePath()).exists() ? leftUrl.getLocalFilePath() : leftUrl.getUrl();
        if ("video".equals(leftUrl.getUrlType())) {
            if (leftPath != null && (new File(leftPath).exists() || (isNetworkAvailable() && !leftPath.startsWith("/")))) {
                playVideoInView(playerLeft, playerViewLeft, leftPath, "Left");
            } else {
                Log.w(TAG, "No valid video path for Left: " + leftPath);
                viewModel.handleVideoEnd();
            }
        } else if ("image".equals(leftUrl.getUrlType())) {
            loadImageInView(imageViewLeft, leftPath, "Left");
        }

        // Handle right half (index 1)
        MediaUrl rightUrl = multipleUrls.get(1);
        String rightPath = rightUrl.getLocalFilePath() != null && new File(rightUrl.getLocalFilePath()).exists() ? rightUrl.getLocalFilePath() : rightUrl.getUrl();
        if ("video".equals(rightUrl.getUrlType())) {
            if (rightPath != null && (new File(rightPath).exists() || (isNetworkAvailable() && !rightPath.startsWith("/")))) {
                playVideoInView(playerRight, playerViewRight, rightPath, "Right");
            } else {
                Log.w(TAG, "No valid video path for Right: " + rightPath);
                viewModel.handleVideoEnd();
            }
        } else if ("image".equals(rightUrl.getUrlType())) {
            loadImageInView(imageViewRight, rightPath, "Right");
        }

        viewModel.setStartTime(System.currentTimeMillis());
    }

    private void playVideoInView(ExoPlayer player, PlayerView playerView, String path, String viewName) {
        playerView.setVisibility(View.VISIBLE);
        Log.d(TAG, "Playing video in " + viewName + ": " + path);
        try {
            Uri uri = path.startsWith("/") ? Uri.fromFile(new File(path)) : Uri.parse(path);
            MediaItem mediaItem = MediaItem.fromUri(uri);
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
        } catch (Exception e) {
            Log.e(TAG, "Failed to play video in " + viewName + ": " + path, e);
            viewModel.handleVideoEnd();
        }
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
}