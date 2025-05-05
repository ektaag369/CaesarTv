package com.example.caesartv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.example.caesartv.presentation.main.MainActivity;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received intent: " + intent.getAction());
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, intent.getAction() + " received, launching MainActivity");
            Intent launchIntent = new Intent(context, MainActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(launchIntent);
                Log.d(TAG, "Started MainActivity");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start MainActivity: " + e.getMessage(), e);
            }
        } else {
            Log.w(TAG, "Ignored action: " + intent.getAction());
        }
    }
}