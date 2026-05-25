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
                @Index(name = "idx_movie_title", columnList = "title"),
                @Index(name = "idx_movie_genre", columnList = "genre"),
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

    @Version
    private Long version;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Genre genre;

    private String director;

    @Column(columnDefinition = "TEXT")
    private String castMembers;

    private int releaseYear;

    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(nullable = false)
    private String thumbnailUrl;

    @Column(nullable = false)
    private int durationMinutes;

    //s3 key for video file
    @Column(unique = true)
    private String videoKey;

    //HLS master playlist URL for streaming
//    private String hlsUrl;
    private String hlsMasterPlaylistKey;

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
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

}
