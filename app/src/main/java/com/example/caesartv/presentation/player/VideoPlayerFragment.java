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
import com.example.caesartv.R;
import com.example.caesartv.presentation.main.MainActivity;
import java.io.File;

public class VideoPlayerFragment extends Fragment {

    private static final String TAG = "VideoPlayerFragment";
    private VideoPlayerViewModel viewModel;
    private ExoPlayer player;
    private PlayerView playerView;
    private View loadingSpinner;
    private String currentMediaId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_video_player, container, false);
        playerView = view.findViewById(R.id.video_view);
        loadingSpinner = view.findViewById(R.id.loading_spinner);
        Log.d(TAG, "PlayerView initialized");
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializePlayer();
        initializeViewModel();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initializePlayer() {
        player = new ExoPlayer.Builder(requireContext()).build();
        playerView.setPlayer(player);
        playerView.setControllerAutoShow(false); // Hide media controls
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case Player.STATE_BUFFERING:
                        Log.d(TAG, "Buffering video");
                        loadingSpinner.setVisibility(View.VISIBLE);
                        break;
                    case Player.STATE_READY:
                        Log.d(TAG, "Video ready to play");
                        loadingSpinner.setVisibility(View.GONE);
                        break;
                    case Player.STATE_ENDED:
                        Log.d(TAG, "Playback ended");
                        loadingSpinner.setVisibility(View.GONE);
                        viewModel.handleVideoEnd();
                        break;
                }
            }

            @Override
            public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                Log.e(TAG, "ExoPlayer error: " + error.getMessage() + ", Cause: " + (error.getCause() != null ? error.getCause().getMessage() : "null"), error);
                loadingSpinner.setVisibility(View.GONE);
                viewModel.handleVideoEnd();
            }
        });
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
            String localFilePath = media.getLocalFilePath() != null ? media.getLocalFilePath() : media.getUrl();
            Log.d(TAG, "Attempting to play video: " + media.getTitle() + ", LocalPath: " + localFilePath + ", Exists: " + new File(localFilePath).exists());
            if (localFilePath != null && new File(localFilePath).exists()) {
                try {
                    Uri uri = Uri.fromFile(new File(localFilePath));
                    Log.d(TAG, "Playing URI: " + uri);
                    MediaItem mediaItem = MediaItem.fromUri(uri);
                    player.setMediaItem(mediaItem);
                    player.prepare();
                    player.play();
                    viewModel.setStartTime(System.currentTimeMillis());
                    loadingSpinner.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to play video: " + media.getTitle() + ", Path: " + localFilePath, e);
                    loadingSpinner.setVisibility(View.GONE);
                    viewModel.handleVideoEnd();
                }
            } else {
                Log.e(TAG, "Invalid or missing video file: " + localFilePath);
                loadingSpinner.setVisibility(View.GONE);
                viewModel.handleVideoEnd();
            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
            loadingSpinner.setVisibility(View.GONE);
            Log.d(TAG, "Player paused");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (player != null) {
            player.release();
            player = null;
            Log.d(TAG, "Player released");
        }
        loadingSpinner.setVisibility(View.GONE);
    }
}