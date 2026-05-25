package com.example.contentservice.service;

import com.example.contentservice.event.VideoEncodedEvent;
import com.example.contentservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoEventConsumer {

    private final ContentService contentService;

    @KafkaListener(topics = "video.uploaded")
    public void consumeVideoUploadedEvent(
            VideoUploadedEvent event
    ) {

        log.info(
                "Received video uploaded event for movieId={}",
                event.movieId()
        );

        contentService.updateVideoKey(
                event.movieId(),
                event.videoKey()
        );
    }

    @KafkaListener(topics = "video.encoded")
    public void consumeVideoEncodedEvent(
            VideoEncodedEvent event
    ) {

        log.info(
                "Received video encoded event for movieId={}",
                event.movieId()
        );

        if (event.success()) {

            contentService.markEncodingCompleted(
                    event.movieId(),
                    event.hlsMasterPlaylistKey()
            );

        } else {

            contentService.markEncodingFailed(
                    event.movieId(),
                    event.errorMessage()
            );
        }
    }
}