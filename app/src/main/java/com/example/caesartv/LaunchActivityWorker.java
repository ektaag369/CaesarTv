package com.example.caesartv;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.caesartv.presentation.main.MainActivity;

public class LaunchActivityWorker extends Worker {

    public LaunchActivityWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        DisplayManager displayManager = (DisplayManager) getApplicationContext().getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays();

        if (displays != null && displays.length > 0) {
            CustomLogger.d("LaunchActivityWorker", "Launched MainActivity before intent");
//            Toast.makeText(getApplicationContext(), "Launched MainActivity from Worker", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getApplicationContext().startActivity(intent);
                CustomLogger.d("LaunchActivityWorker", "Launched MainActivity after intent");
//            Toast.makeText(getApplicationContext(), "Launched MainActivity from Worker", Toast.LENGTH_LONG).show();
//            return Result.retry();
            return Result.success();
        } else {
            // Retry after 10 seconds if no display found
            CustomLogger.d("LaunchActivityWorker", "No display found, retrying in 10 seconds");
            Toast.makeText(getApplicationContext(), "No display found, retrying in 10 seconds from worker", Toast.LENGTH_LONG).show();
            return Result.retry();
        }
    }
}

