package com.example.contentservice.model;

/**
 * Tracks the video processing lifecycle.
 *
 * Flow:
 *   PENDING → UPLOADED → ENCODING → READY
 *                                 ↘ FAILED
 */
public enum VideoStatus {
    PENDING,   // Movie metadata added, video not yet uploaded
    UPLOADED,  // Raw video uploaded to S3
    ENCODING,  // FFmpeg is encoding the video
    READY,     // HLS playlist ready — can be streamed
    FAILED     // Encoding failed
}