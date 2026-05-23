package com.example.encodingservice.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VideoEncodedEvent {
    private Long movieId;
    private String hlsUrl; //master playlist url for streaming
    private String masterPlaylistKey; //s3 key of master.m3u8
    private boolean success;
    private String errorMessage; //if encoding failed
}
