package com.example.videoservice.event;

import lombok.*;

import java.time.Instant;

/**
 * Event published to Kafka when a video is uploaded to S3.
 * Encoding service consumes this to start FFmpeg processing.
 *
 * TOPIC: video.uploaded
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoUploadedEvent {
    private Long movieId;
    private String videoKey;
    private String bucketName;
    private String originalFileName;
    private long fileSizeInBytes;
    private String contentType;
    // Instant is timezone-safe and consistent with VideoEncodedEvent in content-service
    private Instant uploadedAt;
    // For distributed tracing
    private String correlationId;
}