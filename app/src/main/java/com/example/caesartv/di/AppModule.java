package com.example.caesartv.di;

import android.content.Context;

import com.example.caesartv.CustomLogger;
import com.example.caesartv.data.local.AppDatabase;
import com.example.caesartv.data.local.MediaDao;
import com.example.caesartv.data.remote.WebSocketDataSource;
import com.example.caesartv.data.repository.MediaRepositoryImpl;
import com.example.caesartv.domain.repository.MediaRepository;
import com.example.caesartv.domain.usecase.FetchMediaUseCase;
import com.example.caesartv.domain.usecase.GetCachedMediaUseCase;
import com.example.caesartv.presentation.main.MainViewmodel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppModule {
    private static final String TAG = "AppModule";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    public FetchMediaUseCase provideFetchMediaUseCase(Context context) {
        return new FetchMediaUseCase(provideMediaRepository(context));
    }
    public GetCachedMediaUseCase provideGetCachedMediaUseCase(Context context) {
        return new GetCachedMediaUseCase(provideMediaRepository(context));
    }
    public MainViewmodel provideMainViewModel(Context context) {
        return new MainViewmodel(
                provideFetchMediaUseCase(context),
                provideGetCachedMediaUseCase(context),
                context.getApplicationContext(),
                executorService
        );
    }
    public MediaRepository provideMediaRepository(Context context) {
        return new MediaRepositoryImpl(
                provideWebSocketDataSource(context),
                provideMediaDao(context),
                context.getApplicationContext(),
                executorService
        );
    }
    private WebSocketDataSource provideWebSocketDataSource(Context context) {
        return new WebSocketDataSource(context.getApplicationContext());
    }
    private MediaDao provideMediaDao(Context context) {
        return AppDatabase.getDatabase(context.getApplicationContext()).mediaDao();
    }
    public void shutdownExecutorService() {
        if (!executorService.isShutdown()) {
            CustomLogger.d(TAG, "Shutting down executor service in IF block");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    CustomLogger.d(TAG, "Executor service did not terminate in 5 seconds in TRY IF block");
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
                CustomLogger.d(TAG, "Executor service did not terminate in 5 seconds in TRY CATCH block");
            }
        }
    }
}