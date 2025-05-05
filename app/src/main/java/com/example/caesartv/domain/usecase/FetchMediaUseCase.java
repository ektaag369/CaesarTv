package com.example.caesartv.domain.usecase;

import com.example.caesartv.domain.repository.MediaRepository;

public class FetchMediaUseCase {
    private final MediaRepository repository;

    public FetchMediaUseCase(MediaRepository repository) {
        this.repository = repository;
    }

    public void execute(MediaRepository.OnMediaFetchedListener listener, Runnable onBlocked, Runnable onError) {
        repository.fetchMedia(listener, onBlocked, onError);
    }

    public void disconnect() {
        repository.disconnectWebSocket();
    }
}