//package com.example.caesartv;
//
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.util.Log;
//import android.widget.Toast;
//import android.os.Handler;
//
//import com.example.caesartv.presentation.main.MainActivity;
//
//public class BootReceiver extends BroadcastReceiver {
//    private static final String TAG = "BootReceiver";
//    @Override
//    public void onReceive(Context context, Intent intent) {
//        CustomLogger.d(TAG, "Received intent: " + intent.getAction());
//        Context appContext = context.getApplicationContext();
//        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
//            CustomLogger.d(TAG, intent.getAction() + " received, launching MainActivity");
//            Toast.makeText(appContext, "Launching Main activity", Toast.LENGTH_LONG).show();
//            Intent launchIntent = appContext.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
//            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ;
//            try {
//                new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
//                    appContext.startActivity(launchIntent);
//                    Toast.makeText(appContext, "Launching Main activity in Try", Toast.LENGTH_LONG).show();
//                    CustomLogger.d(TAG, "Started MainActivity");
//                }, 10000);
////                appContext.startActivity(launchIntent);
////                Toast.makeText(appContext, "Launching Main activity in Try", Toast.LENGTH_LONG).show();
////                CustomLogger.d(TAG, "Started MainActivity");
//            } catch (Exception e) {
//                Toast.makeText(appContext, "Launching Main activity in catch", Toast.LENGTH_LONG).show();
//                CustomLogger.e(TAG, "Failed to start MainActivity: " + e.getMessage(), e);
//            }
//        } else {
//            Toast.makeText(appContext, "Failed to get the intent.", Toast.LENGTH_LONG).show();
//            CustomLogger.w(TAG, "Ignored action: " + intent.getAction());
//        }
//    }
//}


package com.example.caesartv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.view.Display;
import android.widget.Toast;

import androidx.work.Constraints;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.caesartv.presentation.main.MainActivity;

import java.util.concurrent.TimeUnit;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        CustomLogger.d(TAG, "Received intent: " + intent.getAction());
        Context appContext = context.getApplicationContext();

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {

            CustomLogger.d(TAG, intent.getAction() + " received, waiting to launch MainActivity");

            Toast.makeText(appContext, "Waiting 10 seconds to launch Main activity", Toast.LENGTH_LONG).show();

            Constraints constraints = new Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build();

            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(LaunchActivityWorker.class)
                    .setInitialDelay(1, TimeUnit.SECONDS)
                    .setConstraints(constraints)
                    .build();

            WorkManager.getInstance(context).enqueue(workRequest);



//            new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
//                // Check for available displays
//                DisplayManager displayManager = (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
//                Display[] displays = displayManager.getDisplays();
//
//                if (displays != null && displays.length > 0) {
//                    Toast.makeText(appContext, "Display is available, launching MainActivity", Toast.LENGTH_LONG).show();
//                    CustomLogger.d(TAG, "UI display is available, launching MainActivity");
//                    Intent launchIntent = appContext.getPackageManager().getLaunchIntentForPackage(appContext.getPackageName());
//                    if (launchIntent != null) {
//                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                        try {
//                            appContext.startActivity(launchIntent);
//                            Toast.makeText(appContext, "Launching Main activity", Toast.LENGTH_LONG).show();
//                        } catch (Exception e) {
//                            Toast.makeText(appContext, "Error launching Main activity as Display is not set", Toast.LENGTH_LONG).show();
//                            CustomLogger.e(TAG, "Failed to start MainActivity as Display is not set: " + e.getMessage(), e);
//                        }
//                    } else {
//                        CustomLogger.d(TAG, "Launch intent is null");
//                    }
//                } else {
//                    Toast.makeText(appContext, "No display available, cannot launch MainActivity", Toast.LENGTH_LONG).show();
//                    CustomLogger.w(TAG, "No display available, skipping launch");
//                }
//
//            }, 10000); // 10 seconds delay

        } else {
            Toast.makeText(appContext, "Invalid boot intent received", Toast.LENGTH_LONG).show();
            CustomLogger.w(TAG, "Ignored action: " + intent.getAction());
        }
    }
}
