package com.example.streamingservice.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Consumed from Kafka topic: video.encoded
 * Published by encoding-service after FFmpeg processing.
 *
 * IMPORTANT: Fields must exactly match what encoding-service publishes.
 *
 * Previous version had two critical mismatches:
 *  - 'hlsUrl'            → removed (encoding-service no longer sends it)
 *  - 'masterPlaylistKey' → renamed to 'hlsMasterPlaylistKey' to match publisher
 *  - missing 'completedAt' → added
 *
 * With the old fields, Jackson would deserialize hlsMasterPlaylistKey as null,
 * so event.getMasterPlaylistKey() always returned null and no movie could stream.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VideoEncodedEvent {
    private Long movieId;
    private boolean success;
    private String hlsMasterPlaylistKey; // S3 key of master.m3u8
    private String errorMessage;
    private Instant completedAt;
}