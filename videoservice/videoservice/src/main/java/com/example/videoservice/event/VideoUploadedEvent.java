package com.example.videoservice.event;

import lombok.*;

import java.time.LocalDateTime;

/**
 * EVENT publish to kafks when a video is uploaded to s3
 * encoding service consume this to starte ffmpeg processing
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
    private LocalDateTime uploadedAt;
    //distributed tracing
    private String correlationId;
}
