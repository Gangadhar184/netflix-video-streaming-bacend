package com.example.encodingservice.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * consumed from kafka topic: video.uploaded
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
}