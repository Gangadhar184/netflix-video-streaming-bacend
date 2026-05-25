package com.example.encodingservice.exception;

/**
 * Thrown when FFmpeg exits with a non-zero exit code.
 */
public class FFmpegException extends RuntimeException {
    public FFmpegException(String message) {
        super(message);
    }
}