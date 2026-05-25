package com.example.encodingservice.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Consumed from Kafka topic: video.uploaded
 *
 * IMPORTANT: Fields must exactly match what video-service publishes,
 * otherwise Jackson deserialization will silently drop missing fields
 * or fail on unknown ones depending on your DeserializationFeature config.
 *
 * Matches video-service's VideoUploadedEvent fields:
 *   movieId, videoKey, bucketName, originalFileName,
 *   fileSizeInBytes, contentType, uploadedAt, correlationId
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VideoUploadedEvent {
    private Long movieId;
    private String videoKey;
    private String bucketName;
    private String originalFileName;
    private long fileSizeInBytes;
    private String contentType;
    private Instant uploadedAt;
    private String correlationId;   // for distributed tracing
}