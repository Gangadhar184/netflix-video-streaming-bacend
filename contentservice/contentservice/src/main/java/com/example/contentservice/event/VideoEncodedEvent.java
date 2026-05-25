package com.example.contentservice.event;

import java.time.Instant;

public record VideoEncodedEvent(
        Long movieId,
        boolean success,
        String hlsMasterPlaylistKey,
        String errorMessage,
        Instant completedAt
) {
}