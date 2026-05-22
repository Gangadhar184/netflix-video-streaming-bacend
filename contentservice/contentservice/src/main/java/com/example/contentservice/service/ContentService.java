package com.example.contentservice.service;

import com.example.contentservice.dto.MovieRequest;
import com.example.contentservice.dto.MovieResponse;
import com.example.contentservice.model.Genre;
import com.example.contentservice.model.Movie;
import com.example.contentservice.model.VideoStatus;
import com.example.contentservice.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;

    /**
     * Add a new movie to the catalog
     * Video is not uploaded yet at this stage
     */
    public MovieResponse addMovie(MovieRequest request) {

        log.info("Adding new Movie: {}", request.getTitle());

        Movie movie = Movie.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .genre(request.getGenre())
                .director(request.getDirector())
                .castMembers(request.getCastMembers())
                .releaseYear(request.getReleaseYear())
                .rating(request.getRating())
                .thumbnailUrl(request.getThumbnailUrl())
                .durationMinutes(request.getDurationMinutes())
                .videoStatus(VideoStatus.PENDING)
                .build();

        Movie savedMovie = contentRepository.save(movie);

        log.info("Movie added with ID: {}", savedMovie.getId());

        return mapToResponse(savedMovie);
    }

    /**
     * Get all movies in catalog
     */
    public List<MovieResponse> getAllMovies() {

        return contentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Get movie by ID
     */
    public MovieResponse getMoviesById(Long id) {

        Movie movie = contentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Movie not found with ID: " + id));

        return mapToResponse(movie);
    }

    /**
     * Get movies by genre
     */
    public List<MovieResponse> getMoviesByGenre(Genre genre) {

        return contentRepository.findByGenre(genre)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Search movies by title
     */
    public List<MovieResponse> searchMovies(String title) {

        return contentRepository.findByTitleContainingIgnoreCase(title)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    //s3 bucket
    public void updateVideoKey(Long movieId, String videoKey) {
        log.info("Updating videoKey for movie: {}" , movieId);
        Movie movie = contentRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie not found: " + movieId));
        movie.setVideoKey(videoKey);
        //contentservice job is to add the movie and vidoeservice job is upload that movie to S3->will get s3 key and call
        // this method from here
        movie.setVideoStatus(VideoStatus.UPLOADED);
        contentRepository.save(movie);
    }

    public void updateHslUrl(Long movieId, String hslUrl) {
        log.info("Updating hslUrl for movie: {}", movieId);
        Movie movie = contentRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie not found: " + movieId));
        movie.setHlsUrl(hslUrl);
        //at encoding service as soon as its encoded it will call this method from encoding service
        movie.setVideoStatus(VideoStatus.READY);
        contentRepository.save(movie);
        log.info("Movie {} is now ready for streaming:", movieId);

    }


    private MovieResponse mapToResponse(Movie movie) {
        return MovieResponse.builder()
                .id(movie.getId())
                .title(movie.getTitle())
                .description(movie.getDescription())
                .genre(movie.getGenre())
                .director(movie.getDirector())
                .castMembers(movie.getCastMembers())
                .releaseYear(movie.getReleaseYear())
                .rating(movie.getRating())
                .thumbnailUrl(movie.getThumbnailUrl())
                .durationMinutes(movie.getDurationMinutes())
                .videoStatus(movie.getVideoStatus())
                .build();
    }
}