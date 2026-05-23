package com.example.videoservice.controller;

import com.example.videoservice.dto.VideoUploadResponse;
import com.example.videoservice.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
@Slf4j
public class VideoController {
    private final VideoService videoService;

    /**
     * upload video file for a movie
     * accepts multipart file upload
     * post /api/v1/vidoes/upload/{movieId}
     */

    @PostMapping("/upload/{movieId}")
    public ResponseEntity<VideoUploadResponse> uploadVideo(
            @PathVariable Long movieId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ResponseEntity.ok(
                videoService.uploadVideo(
                        movieId,
                        file
                )
        );
    }
}
