package com.example.caesartv.data.remote;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
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
            Log.w(TAG, "No network available, skipping WebSocket connection");
            onError.run();
            return;
        }

        try {
            IO.Options options = new IO.Options();
            options.transports = new String[]{"websocket"};
            socket = IO.socket(WEBSOCKET_URL, options);

            socket.on(Socket.EVENT_CONNECT, args -> {
                Log.d(TAG, "WebSocket connected");
                retryCount = 0;
                socketHasReceivedMedia = false;
                JSONObject deviceInfo = new JSONObject();
                try {
                    deviceInfo.put("deviceId", getDeviceId());
                    deviceInfo.put("deviceName", getDeviceName());
                    socket.emit("register_tv", deviceInfo);
                    Log.d(TAG, "Emitted register_tv with deviceInfo: " + deviceInfo);
                    // Schedule timeout for media fetch
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        if (socket != null && socket.connected() && !socketHasReceivedMedia) {
                            Log.w(TAG, "Timeout waiting for media");
                            onError.run();
                        }
                    }, MEDIA_TIMEOUT_MS);
                } catch (Exception e) {
                    Log.e(TAG, "Error emitting register_tv", e);
                    onError.run();
                }
            });

            socket.on("registered_success", args -> {
                Log.d(TAG, "Device registered successfully, raw response: " + args[0]);
                try {
                    JSONObject data = (JSONObject) args[0];
                    String deviceId = data.optString("deviceId", getDeviceId());
                    Log.d(TAG, "Fetching media from API for deviceId: " + deviceId);
                    fetchMediaFromApi(deviceId, listener);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing registered_success", e);
                    onError.run();
                }
            });

            socket.on("registered_failed", args -> {
                Log.w(TAG, "Device registration failed: " + args[0]);
                onError.run();
            });

            socket.on("latest_all_media", args -> {
                socketHasReceivedMedia = true;
                Log.d(TAG, "Received latest_all_media, raw response: " + args[0]);
                try {
                    JSONObject data = (JSONObject) args[0];
                    List<MediaItem> mediaList = parseMediaData(data);
                    if (!mediaList.isEmpty()) {
                        listener.onMediaFetched(mediaList);
                        Log.d(TAG, "Fetched " + mediaList.size() + " media items from latest_all_media: " +
                                getMediaIds(mediaList));
                    } else {
                        Log.w(TAG, "No active media in latest_all_media");
                        onError.run();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing latest_all_media", e);
                    onError.run();
                }
            });

            socket.on("blocked_device", args -> {
                Log.w(TAG, "Device blocked: " + args[0]);
                onBlocked.run();
            });

            socket.on("unblocked_device", args -> {
                Log.d(TAG, "Device unblocked, fetching media");
                fetchMediaFromApi(getDeviceId(), listener);
            });

            socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                Log.e(TAG, "WebSocket connection error: " + args[0]);
                retryConnection(listener, onBlocked, onError);
            });

            socket.on(Socket.EVENT_DISCONNECT, args -> {
                Log.w(TAG, "WebSocket disconnected: " + args[0]);
                retryConnection(listener, onBlocked, onError);
            });

            Log.d(TAG, "Connecting to WebSocket");
            socket.connect();
        } catch (URISyntaxException e) {
            Log.e(TAG, "WebSocket URI error", e);
            onError.run();
        }
    }

    private void fetchMediaFromApi(String deviceId, OnMediaFetchedListener listener) {
        try {
            String apiUrl = API_BASE_URL + deviceId + "?page=1&limit=10";
            Log.d(TAG, "Fetching media from: " + apiUrl);
            Request request = new Request.Builder().url(apiUrl).build();
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                Log.e(TAG, "Failed to fetch media from API, HTTP code: " + response.code());
                response.close();
                return;
            }
            String json = response.body().string();
            response.close();
            JSONObject jsonObject = new JSONObject(json);
            List<MediaItem> mediaList = parseApiResponse(jsonObject);
            if (!mediaList.isEmpty()) {
                socketHasReceivedMedia = true;
                listener.onMediaFetched(mediaList);
                Log.d(TAG, "Fetched " + mediaList.size() + " media items from API: " + getMediaIds(mediaList));
            } else {
                Log.w(TAG, "No active media from API");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching media from API", e);
        }
    }

    private List<MediaItem> parseApiResponse(JSONObject data) {
        List<MediaItem> mediaList = new ArrayList<>();
        try {
            String status = data.optString("status", "");
            if (!"success".equals(status)) {
                Log.w(TAG, "API response status is not success: " + status);
                return mediaList;
            }
            JSONObject outerData = data.optJSONObject("data");
            if (outerData == null) {
                Log.w(TAG, "No 'data' field in API response");
                return mediaList;
            }
            JSONArray mediaAllData = outerData.optJSONArray("mediaAllData");
            if (mediaAllData == null) {
                Log.w(TAG, "No 'data.mediaAllData' field in API response");
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
                    Log.d(TAG, "Added active media from API: " + media.getTitle() + ", URL: " + media.getUrl() + ", Duration: " + media.getDuration());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing API response", e);
        }
        return mediaList;
    }

    private List<MediaItem> parseMediaData(JSONObject data) {
        List<MediaItem> mediaList = new ArrayList<>();
        try {
            JSONObject outerData = data.optJSONObject("data");
            if (outerData == null) {
                Log.w(TAG, "No 'data' field in latest_all_media response");
                return mediaList;
            }
            JSONObject innerData = outerData.optJSONObject("data");
            if (innerData == null) {
                Log.w(TAG, "No 'data.data' field in latest_all_media response");
                return mediaList;
            }
            JSONArray mediaAllData = innerData.optJSONArray("mediaAllData");
            if (mediaAllData == null) {
                Log.w(TAG, "No 'data.data.mediaAllData' field in latest_all_media response");
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
                    Log.d(TAG, "Added active media from latest_all_media: " + media.getTitle() + ", URL: " + media.getUrl() + ", Duration: " + media.getDuration());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing latest_all_media data", e);
        }
        return mediaList;
    }

    private void retryConnection(OnMediaFetchedListener listener, Runnable onBlocked, Runnable onError) {
        if (retryCount < MAX_RETRIES && isNetworkAvailable()) {
            retryCount++;
            long delay = 2000 * (1 << (retryCount - 1)); // Exponential backoff: 2s, 4s, 8s
            Log.d(TAG, "Retrying WebSocket connection, attempt " + retryCount + "/" + MAX_RETRIES + ", delay: " + delay + "ms");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                connect(listener, onBlocked, onError);
            }, delay);
        } else {
            Log.w(TAG, "Max WebSocket retries reached or no network, triggering onError");
            onError.run();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket.close();
            Log.d(TAG, "WebSocket disconnected and closed");
        }
    }

    private String getDeviceId() {
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        Log.d(TAG, "Device ID: " + deviceId);
        return deviceId != null ? deviceId : "unknown_device";
    }

    private String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        String deviceName = manufacturer + " " + model;
        Log.d(TAG, "Device Name: " + deviceName);
        return deviceName != null && !deviceName.trim().isEmpty() ? deviceName : "Unknown Device";
    }

    private List<String> getMediaIds(List<MediaItem> mediaItems) {
        List<String> ids = new ArrayList<>();
        for (MediaItem item : mediaItems) {
            ids.add(item.getId());
        }
        return ids;
    }

    public interface OnMediaFetchedListener {
        void onMediaFetched(List<MediaItem> mediaItems);
    }
}