package com.example.caesartv.data.remote;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.example.caesartv.CustomLogger;
import com.example.caesartv.domain.model.MediaItem;
import com.example.caesartv.domain.model.MediaUrl;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WebSocketDataSource {

    private static final String TAG = "WebSocketDataSource";
    private static final String WEBSOCKET_URL = "wss://tvapi.afikgroup.com/";
    private static final String API_BASE_URL = "https://tvapi.afikgroup.com/media/getMedia/";
    private static final int MAX_RETRIES = 3;
    private static final long MEDIA_TIMEOUT_MS = 10000; // 10s timeout for media fetch
    private static final long API_RETRY_DELAY_MS = 2000; // 2s delay for API retries
    private Socket socket;
    private int retryCount = 0;
    private final Context context;
    private boolean socketHasReceivedMedia = false;
    private final OkHttpClient client;

    public WebSocketDataSource(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    public void connect(OnMediaFetchedListener listener, Runnable onBlocked, Runnable onError) {
        if (!isNetworkAvailable()) {
            CustomLogger.w(TAG, "No network available, skipping WebSocket connection");
            onError.run();
            return;
        }

        try {
            IO.Options options = new IO.Options();
            options.transports = new String[]{"websocket"};
            socket = IO.socket(WEBSOCKET_URL, options);

            socket.on(Socket.EVENT_CONNECT, args -> {
                CustomLogger.d(TAG, "WebSocket connected");
                retryCount = 0;
                socketHasReceivedMedia = false;
                JSONObject deviceInfo = new JSONObject();
                try {
                    deviceInfo.put("deviceId", getDeviceId());
                    deviceInfo.put("deviceName", getDeviceName());
                    socket.emit("register_tv", deviceInfo);
                    CustomLogger.d(TAG, "Emitted register_tv with deviceInfo: " + deviceInfo);
                    // Schedule timeout for media fetch
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        if (socket != null && socket.connected() && !socketHasReceivedMedia) {
                            CustomLogger.w(TAG, "Timeout waiting for media");
                            onError.run();
                        }
                    }, MEDIA_TIMEOUT_MS);
                } catch (Exception e) {
                    CustomLogger.e(TAG, "Error emitting register_tv", e);
                    onError.run();
                }
            });

            socket.on("registered_success", args -> {
                CustomLogger.d(TAG, "Device registered successfully, raw response: " + args[0]);
                try {
                    JSONObject data = (JSONObject) args[0];
                    String deviceId = data.optString("deviceId", getDeviceId());
                    CustomLogger.d(TAG, "Fetching media from API for deviceId: " + deviceId);
                    fetchMediaFromApiWithRetry(deviceId, listener, 0);
                } catch (Exception e) {
                    CustomLogger.e(TAG, "Error processing registered_success", e);
                    onError.run();
                }
            });

            socket.on("registered_failed", args -> {
                CustomLogger.w(TAG, "Device registration failed: " + args[0]);
                onError.run();
            });

            socket.on("latest_all_media", args -> {
                CustomLogger.d(TAG, "Device latest_media successfully, raw response: " + args[0]);
                try {
                    JSONObject data = (JSONObject) args[0];
                    String deviceId = data.optString("deviceId", getDeviceId());
                    CustomLogger.d(TAG, "Fetching media from API for deviceId: " + deviceId);
                    fetchMediaFromApiWithRetry(deviceId, listener, 0);
                } catch (Exception e) {
                    CustomLogger.e(TAG, "Error processing registered_success", e);
                    onError.run();
                }
            });

            socket.on("blocked_device", args -> {
                CustomLogger.w(TAG, "Device blocked: " + args[0]);
                onBlocked.run();
            });

            socket.on("unblocked_device", args -> {
                CustomLogger.d(TAG, "Device unblocked, fetching media");
                // Notify MainViewmodel to update isDeviceBlocked
                onError.run(); // Temporarily trigger onError to reset blocked state
                // Fetch media from API with retry
                fetchMediaFromApiWithRetry(getDeviceId(), listener, 0);
            });

            socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                CustomLogger.d(TAG, "WebSocket connection error: " + args[0]);
                retryConnection(listener, onBlocked, onError);
            });

            socket.on(Socket.EVENT_DISCONNECT, args -> {
                CustomLogger.w(TAG, "WebSocket disconnected: " + args[0]);
                retryConnection(listener, onBlocked, onError);
            });

            CustomLogger.d(TAG, "Connecting to WebSocket");
            socket.connect();
        } catch (URISyntaxException e) {
            CustomLogger.e(TAG, "WebSocket URI error", e);
            onError.run();
        }
    }

    private void fetchMediaFromApiWithRetry(String deviceId, OnMediaFetchedListener listener, int attempt) {
        if (attempt >= MAX_RETRIES) {
            CustomLogger.d(TAG, "Max retries reached for API fetch, deviceId: " + deviceId);
            return;
        }
        if (!isNetworkAvailable()) {
            CustomLogger.w(TAG, "No network available for API fetch, deviceId: " + deviceId);
            retryApiFetch(deviceId, listener, attempt + 1);
            return;
        }
        try {
            String apiUrl = API_BASE_URL + deviceId + "?page=1&limit=10";
            CustomLogger.d(TAG, "Fetching media from: " + apiUrl + ", attempt: " + (attempt + 1));
            Request request = new Request.Builder().url(apiUrl).build();
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                CustomLogger.d(TAG, "Failed to fetch media from API, HTTP code: " + response.code() + ", attempt: " + (attempt + 1));
                response.close();
                retryApiFetch(deviceId, listener, attempt + 1);
                return;
            }
            String json = response.body().string();
            response.close();
            JSONObject jsonObject = new JSONObject(json);
            List<MediaItem> mediaList = parseApiResponse(jsonObject);
            if (!mediaList.isEmpty()) {
                socketHasReceivedMedia = true;
                listener.onMediaFetched(mediaList);
                CustomLogger.d(TAG, "Successfully fetched " + mediaList.size() + " media items from API: " + getMediaIds(mediaList));
            } else {
                CustomLogger.w(TAG, "No active media from API, attempt: " + (attempt + 1));
                retryApiFetch(deviceId, listener, attempt + 1);
            }
        } catch (Exception e) {
            CustomLogger.e(TAG, "Error fetching media from API, attempt: " + (attempt + 1), e);
            retryApiFetch(deviceId, listener, attempt + 1);
        }
    }

    private void retryApiFetch(String deviceId, OnMediaFetchedListener listener, int attempt) {
        if (attempt >= MAX_RETRIES) {
            CustomLogger.d(TAG, "Max API fetch retries reached for deviceId: " + deviceId);
            return;
        }
        long delay = API_RETRY_DELAY_MS * (1 << (attempt - 1)); // Exponential backoff: 2s, 4s, 8s
        CustomLogger.d(TAG, "Retrying API fetch, attempt " + attempt + "/" + MAX_RETRIES + ", delay: " + delay + "ms");
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            fetchMediaFromApiWithRetry(deviceId, listener, attempt);
        }, delay);
    }

    private List<MediaItem> parseApiResponse(JSONObject data) {
        List<MediaItem> mediaList = new ArrayList<>();
        try {
            String status = data.optString("status", "");
            if (!"success".equals(status)) {
                CustomLogger.w(TAG, "API response status is not success: " + status);
                return mediaList;
            }
            JSONObject outerData = data.optJSONObject("data");
            if (outerData == null) {
                CustomLogger.w(TAG, "No 'data' field in API response");
                return mediaList;
            }
            JSONArray mediaAllData = outerData.optJSONArray("mediaAllData");
            if (mediaAllData == null) {
                CustomLogger.w(TAG, "No 'data.mediaAllData' field in API response");
                return mediaList;
            }
            for (int i = 0; i < mediaAllData.length(); i++) {
                JSONObject item = mediaAllData.getJSONObject(i);
                JSONArray multipleUrlArray = item.optJSONArray("multipleUrl");
                List<MediaUrl> multipleUrl = new ArrayList<>();
                if (multipleUrlArray != null) {
                    for (int j = 0; j < multipleUrlArray.length(); j++) {
                        JSONObject urlItem = multipleUrlArray.getJSONObject(j);
                        CustomLogger.d(TAG, "Parsed URL item: " + urlItem.toString());
                        multipleUrl.add(new MediaUrl(
                                urlItem.optString("urlType", ""),
                                urlItem.optString("url", ""),
                                urlItem.optString("_id", "")
                        ));
                    }
                }
                MediaItem media = new MediaItem(
                        item.optString("_id", ""),
                        item.optString("title", ""),
                        item.optString("description", ""),
                        item.optString("mediaType", ""),
                        item.optString("url", null),
                        multipleUrl,
                        item.optString("thumbnailUrl", null),
                        item.optInt("duration", 0),
                        item.optInt("displayOrder", 0),
                        item.optBoolean("isActive", false),
                        item.optString("createdAt", ""),
                        item.optString("updatedAt", "")
                );
                if (media.isActive()) {
                    mediaList.add(media);
                    CustomLogger.d(TAG, "Added active media from API: " + media.getTitle() + ", URL: " + media.getUrl() + ", Duration: " + media.getDuration());
                }
            }
        } catch (Exception e) {
            CustomLogger.e(TAG, "Error parsing API response", e);
        }
        return mediaList;
    }

    private List<MediaItem> parseMediaData(JSONObject data) {
        List<MediaItem> mediaList = new ArrayList<>();
        try {
            JSONObject outerData = data.optJSONObject("data");
            if (outerData == null) {
                CustomLogger.w(TAG, "No 'data' field in latest_all_media response");
                return mediaList;
            }
            JSONObject innerData = outerData.optJSONObject("data");
            if (innerData == null) {
                CustomLogger.w(TAG, "No 'data.data' field in latest_all_media response");
                return mediaList;
            }
            JSONArray mediaAllData = innerData.optJSONArray("mediaAllData");
            if (mediaAllData == null) {
                CustomLogger.w(TAG, "No 'data.data.mediaAllData' field in latest_all_media response");
                return mediaList;
            }
            for (int i = 0; i < mediaAllData.length(); i++) {
                JSONObject item = mediaAllData.getJSONObject(i);
                JSONArray multipleUrlArray = item.optJSONArray("multipleUrl");
                List<MediaUrl> multipleUrl = new ArrayList<>();
                if (multipleUrlArray != null) {
                    for (int j = 0; j < multipleUrlArray.length(); j++) {
                        JSONObject urlItem = multipleUrlArray.getJSONObject(j);
                        multipleUrl.add(new MediaUrl(
                                urlItem.optString("urlType", ""),
                                urlItem.optString("url", ""),
                                urlItem.optString("_id", "")
                        ));
                    }
                }
                MediaItem media = new MediaItem(
                        item.optString("_id", ""),
                        item.optString("title", ""),
                        item.optString("description", ""),
                        item.optString("mediaType", ""),
                        item.optString("url", null),
                        multipleUrl,
                        item.optString("thumbnailUrl", null),
                        item.optInt("duration", 0),
                        item.optInt("displayOrder", 0),
                        item.optBoolean("isActive", false),
                        item.optString("createdAt", ""),
                        item.optString("updatedAt", "")
                );
                if (media.isActive()) {
                    mediaList.add(media);
                    CustomLogger.d(TAG, "Added active media from latest_all_media: " + media.getTitle() + ", URL: " + media.getUrl() + ", Duration: " + media.getDuration());
                }
            }
        } catch (Exception e) {
            CustomLogger.e(TAG, "Error parsing latest_all_media data", e);
        }
        return mediaList;
    }

    private void retryConnection(OnMediaFetchedListener listener, Runnable onBlocked, Runnable onError) {
        if (retryCount < MAX_RETRIES && isNetworkAvailable()) {
            retryCount++;
            long delay = 2000 * (1 << (retryCount - 1)); // Exponential backoff: 2s, 4s, 8s
            CustomLogger.d(TAG, "Retrying WebSocket connection, attempt " + retryCount + "/" + MAX_RETRIES + ", delay: " + delay + "ms");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                connect(listener, onBlocked, onError);
            }, delay);
        } else {
            CustomLogger.w(TAG, "Max WebSocket retries reached or no network, triggering onError");
            onError.run();
        }
    }

    private boolean isNetworkAvailable() {
        CustomLogger.d(TAG, "Checking network availability");
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket.close();
            CustomLogger.d(TAG, "WebSocket disconnected and closed");
        }
    }

    private String getDeviceId() {
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        CustomLogger.d(TAG, "Device ID: " + deviceId);
        return deviceId != null ? deviceId : "unknown_device";
    }

    private String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        String deviceName = manufacturer + " " + model;
        CustomLogger.d(TAG, "Device Name: " + deviceName);
        return deviceName != null && !deviceName.trim().isEmpty() ? deviceName : "Unknown Device";
    }

    private List<String> getMediaIds(List<MediaItem> mediaItems) {
        List<String> ids = new ArrayList<>();
        for (MediaItem item : mediaItems) {
            ids.add(item.getId());
        }
        CustomLogger.d(TAG, "Fetched media IDs: " + ids);
        return ids;
    }

    public interface OnMediaFetchedListener {
        void onMediaFetched(List<MediaItem> mediaItems);
    }
}