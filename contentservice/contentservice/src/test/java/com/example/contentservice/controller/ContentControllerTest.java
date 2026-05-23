package com.example.contentservice.controller;

import com.example.contentservice.dto.MovieResponse;
import com.example.contentservice.service.ContentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentControllerTest {

    @Mock
    private ContentService contentService;

    @InjectMocks
    private ContentController contentController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(contentController).build();
    }

    @Test
    void getMoviesByIdAcceptsLongPathVariable() throws Exception {
        MovieResponse response = MovieResponse.builder()
                .id(42L)
                .title("Inception")
                .build();

        when(contentService.getMoviesById(42L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/movies/{movieId}", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.title").value("Inception"));

        verify(contentService).getMoviesById(42L);
    }
}