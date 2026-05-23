package com.example.streamingservice.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * consumed from kakfa topic: video.encoded
 * published by encoding service after ffmpeg processing
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VideoEncodedEvent {
    private Long movieId;
    private String hlsUrl;
    private String masterPlaylistKey;
    private boolean success;
    private String errorMessage;
}
