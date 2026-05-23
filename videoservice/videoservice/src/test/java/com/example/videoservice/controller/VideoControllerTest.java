package com.example.videoservice.controller;

import com.example.videoservice.service.VideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VideoControllerTest {

    @Mock
    private VideoService videoService;

    @InjectMocks
    private VideoController videoController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(videoController).build();
    }

    @Test
    void uploadVideoAcceptsLongMovieId() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "movie.mp4",
                "video/mp4",
                "video-data".getBytes()
        );

        when(videoService.uploadVideo(eq(42L), any())).thenReturn("raw/42/movie.mp4");

        mockMvc.perform(multipart("/api/v1/videos/upload/{movieId}", 42L).file(file))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("raw/42/movie.mp4")));

        verify(videoService).uploadVideo(eq(42L), any());
    }

    @Test
    void uploadVideoRejectsEmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.mp4",
                "video/mp4",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/videos/upload/{movieId}", 42L).file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("File is empty"));

        verify(videoService, never()).uploadVideo(eq(42L), any());
    }
}