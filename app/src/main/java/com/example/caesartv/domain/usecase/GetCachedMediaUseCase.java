package com.example.caesartv.domain.usecase;

import com.example.caesartv.domain.model.MediaItem;
import com.example.caesartv.domain.repository.MediaRepository;
import java.util.List;

public class GetCachedMediaUseCase {
    private final MediaRepository repository;

    public GetCachedMediaUseCase(MediaRepository repository) {
        this.repository = repository;
    }

    public List<MediaItem> execute() {
        return repository.getCachedMedia();
    }

    public MediaRepository getRepository() {
        return repository;
    }
}