package com.example.caesartv;

import android.util.Log;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CustomLogger {
    private static final String API_URL = "https://tvapi.afikgroup.com/media/log-text";
    private static final String TAG = "CustomLogger";
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    public static void d(String tag, String message) {
        Log.d(tag, message);
        sendLogToApi(tag, message);
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);
        sendLogToApi(tag, message);
    }

    public static void e(String tag, String message, Throwable t) {
        Log.e(tag, message, t);
        sendLogToApi(tag, message + " Exception: " + t.getMessage());
    }

    private static void sendLogToApi(String tag, String message) {
        String date = dateFormat.format(new Date());
        String time = timeFormat.format(new Date());
        String logMessage = String.format("[%s %s] %s: %s", date, time, tag, message);
        String jsonBody = String.format("{\"text\": \"%s\"}", logMessage.replace("\"", "\\\""));

        executorService.execute(() -> {
            RequestBody body = RequestBody.create(jsonBody, JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, "Failed to send log to API: " + response.code());
                } else {
                    Log.w(TAG, "Successfully sent log to API");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error sending log to API: " + e.getMessage(), e);
            }
        });
    }

    public static void shutdown() {
        if (!executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    Log.w(TAG, "ExecutorService did not terminate");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}