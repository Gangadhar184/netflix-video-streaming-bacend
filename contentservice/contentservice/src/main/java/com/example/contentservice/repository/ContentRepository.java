package com.example.contentservice.repository;

import com.example.contentservice.model.Genre;
import com.example.contentservice.model.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContentRepository extends JpaRepository<Movie, Long> {

    Optional<Movie> findByIdAndDeletedFalse(Long id);

    Page<Movie> findByDeletedFalse(Pageable pageable);

    Page<Movie> findByGenreAndDeletedFalse(
            Genre genre,
            Pageable pageable
    );

    Page<Movie> findByTitleContainingIgnoreCaseAndDeletedFalse(
            String title,
            Pageable pageable
    );
}