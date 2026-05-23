package com.example.contentservice.repository;

import com.example.contentservice.model.Genre;
import com.example.contentservice.model.Movie;
import com.example.contentservice.model.VideoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentRepository extends JpaRepository<Movie, Long> {

    Page<Movie> findByTitleContainingIgnoreCaseAndDeletedFalse(
            String title,
            Pageable pageable
    );


    Page<Movie> findByGenreAndDeletedFalse(
            Genre genre,
            Pageable pageable
    );

    Page<Movie> findByVideoStatusAndDeletedFalse(
            VideoStatus status,
            Pageable pageable
    );

    Page<Movie> findByDeletedFalse(Pageable pageable);
}
