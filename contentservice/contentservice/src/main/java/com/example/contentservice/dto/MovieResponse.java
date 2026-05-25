package com.example.contentservice.dto;

import com.example.contentservice.model.Genre;
import com.example.contentservice.model.VideoStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieResponse {

    private Long id;

    private String title;

    private String description;

    private Genre genre;

    private String director;

    private String castMembers;

    private int releaseYear;

    private BigDecimal rating;

    private String thumbnailUrl;

    private int durationMinutes;

    private String videoKey;

//    private String hlsUrl;
    private String hlsMasterPlaylistKey;

    private VideoStatus videoStatus;

    private String lastEncodingError;

    private LocalDateTime encodingStartedAt;

    private LocalDateTime encodingCompletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}