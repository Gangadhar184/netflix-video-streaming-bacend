package com.example.contentservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "movies" ,
        indexes = {
        @Index(
                name = "idx_movie_title",
                columnList = "title"
        ),
                @Index(
                        name = "idx_movie_genre",
                        columnList = "genre"
                ),
                @Index(name = "idx_movie_video_status", columnList = "videoStatus")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Genre genre;

    private String director;

    @Column(columnDefinition = "TEXT")
    private String castMembers;

    private int releaseYear;

    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    private String thumbnailUrl;
    private int durationMinutes;

    //s3 key for video file

    private String videoKey;

    //HLS master playlist URL for streaming
    private String hlsUrl;

    //status of video processsing
    @Enumerated(EnumType.STRING)
    private VideoStatus videoStatus;

    //encoding meta-data
    private LocalDateTime encodingStartedAt;

    private LocalDateTime encodingCompletedAt;

    @Column(columnDefinition = "TEXT")
    private String lastEncodingError;

    //soft delete support
    @Builder.Default
    private boolean deleted = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
