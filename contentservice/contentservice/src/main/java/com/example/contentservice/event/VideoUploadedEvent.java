package com.example.contentservice.event;

import java.time.Instant;

public record VideoUploadedEvent(
        Long movieId,
        String videoKey,
        Instant uploadedAt
) {
}