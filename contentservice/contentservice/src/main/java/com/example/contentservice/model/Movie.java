package com.example.contentservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    private Genre genre;

    private String director;

    @Column(name = "cast_members")
    private String castMembers;

    private int releaseYear;

    private double rating;

    private String thumbnailUrl;
    private int durationMinutes;

    //s3 key for video file

    private String videoKey;

    //HLS master playlist URL for streaming
    private String hlsUrl;

    //status of video processsing
    @Enumerated(EnumType.STRING)
    private VideoStatus videoStatus;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
