package com.example.contentservice.dto;

import com.example.contentservice.model.Genre;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255)
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Genre is required")
    private Genre genre;

    private String director;

    private String castMembers;

    @Min(value = 1888, message = "Invalid release year")
    @Max(value = 2100, message = "Invalid release year")
    private int releaseYear;

    @DecimalMin(value = "0.0", message = "Rating cannot be below 0")
    @DecimalMax(value = "10.0", message = "Rating cannot exceed 10")
    private BigDecimal rating;

    @NotBlank(message = "Thumbnail URL is required")
    private String thumbnailUrl;

    @Positive(message = "Duration must be positive")
    private int durationMinutes;
}