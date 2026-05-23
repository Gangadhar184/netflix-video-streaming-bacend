package com.example.videoservice.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
public class VideoUploadEvent {
    private Long movieId;
    private String videoKey;
    private String bucketName;
    private String originalFileName;
    private long fileSizeInBytes;
}
