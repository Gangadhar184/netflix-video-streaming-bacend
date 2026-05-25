package com.example.videoservice.controller;

import com.example.videoservice.dto.VideoUploadResponse;
import com.example.videoservice.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
@Slf4j
public class VideoController {

    private final VideoService videoService;

    /**
     * Upload a video file for a movie.
     * POST /api/v1/videos/upload/{movieId}
     *
     * IOException is caught inside VideoService and wrapped as VideoUploadException (500),
     * so this endpoint no longer needs to declare or handle it.
     */
    @PostMapping("/upload/{movieId}")
    public ResponseEntity<VideoUploadResponse> uploadVideo(
            @PathVariable Long movieId,
            @RequestParam("file") MultipartFile file
    ) {
        // 201 CREATED — a new video resource has been created in S3
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(videoService.uploadVideo(movieId, file));
    }
}