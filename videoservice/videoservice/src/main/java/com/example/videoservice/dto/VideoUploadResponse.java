package com.example.videoservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VideoUploadResponse {

    private Long movieId;

    private String videoKey;

    private String status;

    private String message;
}