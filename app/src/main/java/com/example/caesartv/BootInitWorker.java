package com.example.caesartv;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class BootInitWorker extends Worker {

    public BootInitWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        CustomLogger.d("BootInitWorker", "Boot init task running (one-time or periodic)");

        // Your periodic or boot-time task logic here
        return Result.success();
    }

    public static void schedulePeriodicWork(Context context) {
        PeriodicWorkRequest periodicWork =
                new PeriodicWorkRequest.Builder(BootInitWorker.class, 1, TimeUnit.MINUTES)
                        .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "boot_check_periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
        );
    }
}
