package com.example.streamingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamingResponse {
    private Long movieId;
    //presigned hls masterplaylist url
    private String streamingUrl;
    private String quality; //avaialble qualites
    private long expiresInMinutes; //url expiry time

}
