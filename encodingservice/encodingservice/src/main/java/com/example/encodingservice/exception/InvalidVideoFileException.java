package com.example.encodingservice.exception;

/**
 * Thrown when the downloaded video file fails basic validation
 * (missing, empty, or unreadable).
 */
public class InvalidVideoFileException extends RuntimeException {
    public InvalidVideoFileException(String message) {
        super(message);
    }
}