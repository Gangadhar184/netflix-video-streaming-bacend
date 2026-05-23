package com.example.contentservice.service;

import com.example.contentservice.dto.MovieRequest;
import com.example.contentservice.dto.MovieResponse;
import com.example.contentservice.exception.MovieNotFoundException;
import com.example.contentservice.model.Genre;
import com.example.contentservice.model.Movie;
import com.example.contentservice.model.VideoStatus;
import com.example.contentservice.repository.ContentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;

    //add movie metadata
    @Transactional
    public MovieResponse addMovie(MovieRequest request) {

        log.info("Adding movie: {}", request.getTitle());

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

        return mapToResponse(savedMovie);
    }

    //paginated the movie fetch
    public Page<MovieResponse> getAllMovies(
            int page,
            int size
    ) {

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by("createdAt").descending()
                );

        return contentRepository.findByDeletedFalse(pageable)
                .map(this::mapToResponse);
    }

    //get movie by id
    public MovieResponse getMovieById(Long id) {

        Movie movie = contentRepository.findById(id)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new MovieNotFoundException(id));

        return mapToResponse(movie);
    }

    //filter by genre
    public Page<MovieResponse> getMoviesByGenre(
            Genre genre,
            int page,
            int size
    ) {

        Pageable pageable = PageRequest.of(page, size);

        return contentRepository
                .findByGenreAndDeletedFalse(
                        genre,
                        pageable
                )
                .map(this::mapToResponse);
    }

    //search by title
    public Page<MovieResponse> searchMovies(
            String title,
            int page,
            int size
    ) {

        Pageable pageable = PageRequest.of(page, size);

        return contentRepository
                .findByTitleContainingIgnoreCaseAndDeletedFalse(
                        title,
                        pageable
                )
                .map(this::mapToResponse);
    }

    //vidoe uploaded
    @Transactional
    public void updateVideoKey(
            Long movieId,
            String videoKey
    ) {

        Movie movie = contentRepository.findById(movieId)
                .orElseThrow(() ->
                        new MovieNotFoundException(movieId));

        movie.setVideoKey(videoKey);

        movie.setVideoStatus(VideoStatus.UPLOADED);

        contentRepository.save(movie);

        log.info(
                "Video uploaded for movie: {}",
                movieId
        );
    }

    //encoding
    @Transactional
    public void markEncodingStarted(Long movieId) {

        Movie movie = contentRepository.findById(movieId)
                .orElseThrow(() ->
                        new MovieNotFoundException(movieId));

        movie.setVideoStatus(VideoStatus.ENCODING);

        movie.setEncodingStartedAt(LocalDateTime.now());

        contentRepository.save(movie);

        log.info(
                "Encoding started for movie: {}",
                movieId
        );
    }

    //encoding completed
    @Transactional
    public void markEncodingCompleted(
            Long movieId,
            String hlsUrl
    ) {

        Movie movie = contentRepository.findById(movieId)
                .orElseThrow(() ->
                        new MovieNotFoundException(movieId));

        movie.setHlsUrl(hlsUrl);

        movie.setVideoStatus(VideoStatus.READY);

        movie.setEncodingCompletedAt(LocalDateTime.now());

        movie.setLastEncodingError(null);

        contentRepository.save(movie);

        log.info(
                "Movie ready for streaming: {}",
                movieId
        );
    }

    //encoding failed
    @Transactional
    public void markEncodingFailed(
            Long movieId,
            String error
    ) {

        Movie movie = contentRepository.findById(movieId)
                .orElseThrow(() ->
                        new MovieNotFoundException(movieId));

        movie.setVideoStatus(VideoStatus.FAILED);

        movie.setLastEncodingError(error);

        contentRepository.save(movie);

        log.error(
                "Encoding failed for movie: {}",
                movieId
        );
    }

    //soft delete movie
    @Transactional
    public void deleteMovie(Long movieId) {

        Movie movie = contentRepository.findById(movieId)
                .orElseThrow(() ->
                        new MovieNotFoundException(movieId));

        movie.setDeleted(true);

        contentRepository.save(movie);

        log.info(
                "Movie soft deleted: {}",
                movieId
        );
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
                .videoKey(movie.getVideoKey())
                .hlsUrl(movie.getHlsUrl())
                .videoStatus(movie.getVideoStatus())
                .lastEncodingError(
                        movie.getLastEncodingError()
                )
                .encodingStartedAt(
                        movie.getEncodingStartedAt()
                )
                .encodingCompletedAt(
                        movie.getEncodingCompletedAt()
                )
                .createdAt(movie.getCreatedAt())
                .updatedAt(movie.getUpdatedAt())
                .build();
    }
}