package com.example.encodingservice.event;

import lombok.*;

import java.time.Instant;

/**
 * Published to Kafka topic: video.encoded
 *
 * IMPORTANT: Fields must exactly match what content-service's VideoEncodedEvent
 * record expects:
 *   movieId, success, hlsMasterPlaylistKey, errorMessage, completedAt
 *
 * Previous version had field name mismatches:
 *   - 'hlsUrl'           → removed (streaming service generates pre-signed URLs at request time)
 *   - 'masterPlaylistKey'→ renamed to 'hlsMasterPlaylistKey' to match content-service
 *   - missing completedAt → added
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoEncodedEvent {
    private Long movieId;
    private boolean success;
    private String hlsMasterPlaylistKey; // S3 key of master.m3u8
    private String errorMessage;         // null on success
    private Instant completedAt;
}