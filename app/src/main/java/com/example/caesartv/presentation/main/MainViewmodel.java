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

import com.example.caesartv.CustomLogger;
import com.example.caesartv.domain.model.MediaItem;
import com.example.caesartv.domain.usecase.FetchMediaUseCase;
import com.example.caesartv.domain.usecase.GetCachedMediaUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class MainViewmodel extends ViewModel {

    private static final String TAG = "MainViewModel";
    private final FetchMediaUseCase fetchMediaUseCase;
    private final GetCachedMediaUseCase getCachedMediaUseCase;
    private final Context context;
    private final ExecutorService executorService;
    private final MutableLiveData<List<MediaItem>> mediaItems = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isDeviceBlocked = new MutableLiveData<>();
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean isWebSocketConnected = false;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    public MainViewmodel(FetchMediaUseCase fetchMediaUseCase, GetCachedMediaUseCase getCachedMediaUseCase, Context context, ExecutorService executorService) {
        this.fetchMediaUseCase = fetchMediaUseCase;
        this.getCachedMediaUseCase = getCachedMediaUseCase;
        this.context = context;
        this.executorService = executorService;
        setupNetworkCallback();
        checkCachedMedia();
        connectWebSocket(0);
    }

    public MutableLiveData<List<MediaItem>> getMediaItems() {
        return mediaItems;
    }

    public MutableLiveData<Boolean> getIsDeviceBlocked() {
        return isDeviceBlocked;
    }

    public void connectWebSocket() {
        connectWebSocket(0);
    }

    public void connectWebSocket(int retryCount) {
        if (isWebSocketConnected || retryCount > MAX_RETRIES) {
            if (!isWebSocketConnected) {
                CustomLogger.w(TAG, "Max retries reached, using cached media");
                isDeviceBlocked.postValue(false);
            }
            return;
        }
        if (!isNetworkAvailable()) {
            CustomLogger.w(TAG, "No network available, using cached media");
            isDeviceBlocked.postValue(false);
            return;
        }
        if (executorService.isShutdown()) {
            CustomLogger.w(TAG, "ExecutorService is shutdown, cannot initialize WebSocket");
            isDeviceBlocked.postValue(false);
            return;
        }
        isWebSocketConnected = true;
        executorService.execute(() -> {
            fetchMediaUseCase.execute(
                    mediaList -> {
                        if (!executorService.isShutdown()) {
                            executorService.execute(() -> {
                                mediaItems.postValue(mediaList);
                                isDeviceBlocked.postValue(false);
                                CustomLogger.d(TAG, "WebSocket fetched and updated DB with " + mediaList.size() + " media items");
                            });
                        }
                    },
                    () -> {
                        if (!executorService.isShutdown()) {
                            executorService.execute(() -> {
                                isDeviceBlocked.postValue(true);
                                CustomLogger.w(TAG, "Device blocked via WebSocket");
                                isWebSocketConnected = false;
                            });
                        }
                    },
                    () -> {
                        if (!executorService.isShutdown()) {
                            executorService.execute(() -> {
                                CustomLogger.w(TAG, "WebSocket error, retrying (" + (retryCount + 1) + "/" + MAX_RETRIES + ")");
                                isDeviceBlocked.postValue(false);
                                isWebSocketConnected = false;
                                try {
                                    CustomLogger.d(TAG, "Retrying in " + RETRY_DELAY_MS + "ms");
                                    Thread.sleep(RETRY_DELAY_MS);
                                } catch (InterruptedException e) {
                                    CustomLogger.e(TAG, "Retry interrupted: " + e.getMessage(), e);
                                    Thread.currentThread().interrupt();
                                }
                                connectWebSocket(retryCount + 1);
                            });
                        }
                    }
            );
        });
    }

    public void checkCachedMedia() {
        CustomLogger.d(TAG, "Checking cached media");
        if (executorService.isShutdown()) {
            CustomLogger.w(TAG, "ExecutorService is shutdown, cannot check cached media");
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
                            CustomLogger.d(TAG, "Found " + cachedMedia.size() + " cached media items");
                        } else {
                            CustomLogger.w(TAG, "No cached media found");
                            mediaItems.postValue(new ArrayList<>());
                        }
                    });
                }
            } catch (Exception e) {
                CustomLogger.e(TAG, "Error loading cached media: " + e.getMessage(), e);
                if (!executorService.isShutdown()) {
                    CustomLogger.d(TAG, "Clearing cached media");
                    executorService.execute(() -> mediaItems.postValue(new ArrayList<>()));
                }
            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CustomLogger.d(TAG, "Using new network check");
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
                CustomLogger.d(TAG, "Network available, initializing WebSocket");
                connectWebSocket(0);
            }

            @Override
            public void onLost(Network network) {
                CustomLogger.w(TAG, "Network lost, stopping WebSocket");
                disconnectWebSocket();
                checkCachedMedia();
            }
        };
        cm.registerNetworkCallback(request, networkCallback);
    }

    public void disconnectWebSocket() {
        if (!executorService.isShutdown() && isWebSocketConnected) {
            executorService.execute(() -> {
                fetchMediaUseCase.disconnect();
                isWebSocketConnected = false;
                CustomLogger.d(TAG, "WebSocket disconnected");
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
            CustomLogger.d(TAG, "Network callback unregistered");
            networkCallback = null;
        }
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    CustomLogger.w(TAG, "ExecutorService did not terminate");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        CustomLogger.d(TAG, "ViewModel cleared, resources released");
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