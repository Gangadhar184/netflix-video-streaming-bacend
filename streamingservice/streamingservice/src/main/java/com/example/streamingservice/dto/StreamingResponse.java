package com.example.streamingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned to the client when they request a streaming URL.
 * The streamingUrl is a pre-signed S3 URL valid for expiresInMinutes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamingResponse {
    private Long movieId;
    /** Pre-signed HLS master playlist URL. Expires after expiresInMinutes. */
    private String streamingUrl;
    /** Human-readable list of available quality renditions. */
    private String availableQualities;
    /** How many minutes until the pre-signed URL expires. */
    private long expiresInMinutes;
}