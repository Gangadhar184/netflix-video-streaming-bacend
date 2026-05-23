package com.example.contentservice.controller;

import com.example.contentservice.dto.MovieRequest;
import com.example.contentservice.dto.MovieResponse;
import com.example.contentservice.model.Genre;
import com.example.contentservice.service.ContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/movies")
@Slf4j
@RequiredArgsConstructor
public class ContentController {
    private final ContentService contentService;

    //add new movie to catalog
    @PostMapping
    public ResponseEntity<MovieResponse> addMovie(
            @Valid @RequestBody MovieRequest movieRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contentService.addMovie(movieRequest));
    }

    //get all movies
    @GetMapping
    public ResponseEntity<Page<MovieResponse>> getAllMovies(
            @RequestParam(defaultValue = "0")
            int page,
            @RequestParam(defaultValue = "10")
            int size
    ) {
        return ResponseEntity.ok(
                contentService.getAllMovies(page, size)
        );
    }

    @GetMapping("/genre/{genre}")
    public ResponseEntity<Page<MovieResponse>> getMoviesByGenre(

            @PathVariable Genre genre,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size
    ) {

        return ResponseEntity.ok(
                contentService.getMoviesByGenre(
                        genre,
                        page,
                        size
                )
        );
    }

    @GetMapping("/id/{movieId}")
    public ResponseEntity<MovieResponse> getMovieById(
            @PathVariable Long movieId
    ) {

        return ResponseEntity.ok(
                contentService.getMovieById(movieId)
        );
    }

    @GetMapping("/search")
    public ResponseEntity<Page<MovieResponse>> searchMovies(

            @RequestParam String title,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size
    ) {

        return ResponseEntity.ok(
                contentService.searchMovies(
                        title,
                        page,
                        size
                )
        );
    }

    /**
     * Soft delete movie
     */
    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> deleteMovie(
            @PathVariable Long movieId
    ) {

        contentService.deleteMovie(movieId);

        return ResponseEntity.noContent().build();
    }

}
