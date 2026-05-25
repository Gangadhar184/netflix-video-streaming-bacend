package com.example.videoservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidVideoException extends RuntimeException {
    public InvalidVideoException(String message) {
        super(message);
    }
}