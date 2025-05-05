package com.example.caesartv.presentation.main;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.caesartv.domain.model.MediaItem;
import com.example.caesartv.domain.usecase.FetchMediaUseCase;
import com.example.caesartv.domain.usecase.GetCachedMediaUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class MainViewmodel extends ViewModel {

    private static final String TAG = "MainViewModel";
    private final FetchMediaUseCase fetchMediaUseCase;
    private final GetCachedMediaUseCase getCachedMediaUseCase;
    private final Context context;
    private final ExecutorService executorService;
    private final MutableLiveData<List<MediaItem>> mediaItems = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isDeviceBlocked = new MutableLiveData<>();
    private ConnectivityManager.NetworkCallback networkCallback;

    public MainViewmodel(FetchMediaUseCase fetchMediaUseCase, GetCachedMediaUseCase getCachedMediaUseCase, Context context, ExecutorService executorService) {
        this.fetchMediaUseCase = fetchMediaUseCase;
        this.getCachedMediaUseCase = getCachedMediaUseCase;
        this.context = context;
        this.executorService = executorService;
        setupNetworkCallback();
        checkCachedMedia();
    }

    public LiveData<List<MediaItem>> getMediaItems() {
        return mediaItems;
    }

    public MutableLiveData<Boolean> getIsDeviceBlocked() {
        return isDeviceBlocked;
    }

    public void initializeWebSocket() {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "No network available, skipping WebSocket initialization");
            return;
        }
        if (executorService.isShutdown()) {
            Log.w(TAG, "ExecutorService is shutdown, cannot initialize WebSocket");
            return;
        }
        executorService.execute(() -> {
            fetchMediaUseCase.execute(
                    mediaList -> {
                        if (!executorService.isShutdown()) {
                            executorService.execute(() -> {
                                mediaItems.postValue(mediaList);
                                Log.d(TAG, "WebSocket fetched " + mediaList.size() + " media items");
                            });
                        }
                    },
                    () -> {
                        if (!executorService.isShutdown()) {
                            executorService.execute(() -> {
                                isDeviceBlocked.postValue(true);
                                Log.w(TAG, "Device blocked via WebSocket");
                            });
                        }
                    },
                    () -> {
                        if (!executorService.isShutdown()) {
                            executorService.execute(() -> {
                                Log.w(TAG, "WebSocket error, checking cached media");
                                checkCachedMedia();
                            });
                        }
                    }
            );
        });
    }

    private void checkCachedMedia() {
        Log.d(TAG, "Checking cached media");
        if (executorService.isShutdown()) {
            Log.w(TAG, "ExecutorService is shutdown, cannot check cached media");
            mediaItems.postValue(new ArrayList<>());
            return;
        }
        executorService.execute(() -> {
            try {
                List<MediaItem> cachedMedia = getCachedMediaUseCase.execute();
                if (!executorService.isShutdown()) {
                    executorService.execute(() -> {
                        if (cachedMedia != null && !cachedMedia.isEmpty()) {
                            mediaItems.postValue(cachedMedia);
                            Log.d(TAG, "Found " + cachedMedia.size() + " cached media items");
                        } else {
                            Log.w(TAG, "No cached media found");
                            mediaItems.postValue(new ArrayList<>());
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading cached media: " + e.getMessage(), e);
                if (!executorService.isShutdown()) {
                    executorService.execute(() -> mediaItems.postValue(new ArrayList<>()));
                }
            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            network = cm.getActiveNetwork();
        }
        if (network == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }

    private void setupNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.d(TAG, "Network available, initializing WebSocket");
                initializeWebSocket();
            }

            @Override
            public void onLost(Network network) {
                Log.w(TAG, "Network lost, stopping WebSocket");
                disconnectWebSocket();
            }
        };
        cm.registerNetworkCallback(request, networkCallback);
    }

    public void disconnectWebSocket() {
        if (!executorService.isShutdown()) {
            executorService.execute(() -> {
                fetchMediaUseCase.disconnect();
                Log.d(TAG, "WebSocket disconnected");
            });
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disconnectWebSocket();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
        }
    }

    public static class Factory extends ViewModelProvider.NewInstanceFactory {
        private final FetchMediaUseCase fetchMediaUseCase;
        private final GetCachedMediaUseCase getCachedMediaUseCase;
        private final Context context;
        private final ExecutorService executorService;

        public Factory(FetchMediaUseCase fetchMediaUseCase, GetCachedMediaUseCase getCachedMediaUseCase, Context context, ExecutorService executorService) {
            this.fetchMediaUseCase = fetchMediaUseCase;
            this.getCachedMediaUseCase = getCachedMediaUseCase;
            this.context = context;
            this.executorService = executorService;
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            return (T) new MainViewmodel(fetchMediaUseCase, getCachedMediaUseCase, context, executorService);
        }
    }
}