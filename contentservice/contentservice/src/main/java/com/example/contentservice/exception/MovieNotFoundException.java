package com.example.contentservice.exception;

public class MovieNotFoundException extends RuntimeException {

    public MovieNotFoundException(Long movieId) {
        super("Movie not found with ID: " + movieId);
    }
}
