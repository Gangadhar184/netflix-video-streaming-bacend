package com.example.videoservice.service;

import com.example.videoservice.event.VideoUploadedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private KafkaTemplate<String, VideoUploadedEvent> kafkaTemplate;

    @InjectMocks
    private VideoService videoService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(videoService, "bucketName", "test-bucket");
    }

    @Test
    void uploadVideoPublishesEventWithLongMovieId() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "movie.mp4",
                "video/mp4",
                "video-data".getBytes()
        );

        String videoKey = videoService.uploadVideo(42L, file);

        assertTrue(videoKey.startsWith("raw/42/"));
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        ArgumentCaptor<VideoUploadedEvent> eventCaptor = ArgumentCaptor.forClass(VideoUploadedEvent.class);
        verify(kafkaTemplate).send(eq("video.uploaded"), eq("42"), eventCaptor.capture());

        VideoUploadedEvent event = eventCaptor.getValue();
        assertEquals(42L, event.getMovieId());
        assertEquals(videoKey, event.getVideoKey());
        assertEquals("test-bucket", event.getBucketName());
        assertEquals("movie.mp4", event.getOriginalFileName());
        assertEquals(file.getSize(), event.getFileSizeInBytes());
    }
}