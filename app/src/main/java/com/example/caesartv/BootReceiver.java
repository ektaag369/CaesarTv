package com.example.caesartv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.example.caesartv.presentation.main.MainActivity;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        CustomLogger.d(TAG, "Received intent: " + intent.getAction());
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            CustomLogger.d(TAG, intent.getAction() + " received, launching MainActivity");
            Toast.makeText(context, "Launching Main activity", Toast.LENGTH_SHORT).show();
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ;
            try {
                context.startActivity(launchIntent);
                Toast.makeText(context, "Launching Main activity in Try", Toast.LENGTH_SHORT).show();
                CustomLogger.d(TAG, "Started MainActivity");
            } catch (Exception e) {
                Toast.makeText(context, "Launching Main activity in catch", Toast.LENGTH_SHORT).show();
                CustomLogger.e(TAG, "Failed to start MainActivity: " + e.getMessage(), e);
            }
        } else {
            Toast.makeText(context, "Failed to get the intent.", Toast.LENGTH_SHORT).show();
            CustomLogger.w(TAG, "Ignored action: " + intent.getAction());
        }
    }
}