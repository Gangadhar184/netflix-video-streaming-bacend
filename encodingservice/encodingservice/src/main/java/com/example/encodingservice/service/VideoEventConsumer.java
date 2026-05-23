package com.example.encodingservice.service;

import com.example.encodingservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * Kafka Consumer
 *
 * Responsibilities:
 * 1. Listen for uploaded video events
 * 2. Trigger encoding pipeline
 * 3. Handle consumer-level failures
 * 4. Log metadata for observability
 *
 * FLOW:
 *
 * Video Service
 *      ↓
 * video.uploaded topic
 *      ↓
 * EncodingService
 *      ↓
 * video.encoded topic
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VideoEventConsumer {

    /**
     * Main encoding service
     */
    private final EncodingService encodingService;

    /**
     * Kafka consumer for uploaded videos
     *
     * Topic:
     * video.uploaded
     *
     * Consumer Group:
     * encoding-service-group
     *
     * NOTE:
     * Group ID should ideally come from application.yml
     */
    @KafkaListener(
            topics = "${kafka.topics.video-uploaded}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeVideoUploadedEvent(



            VideoUploadedEvent event,

            /**
             * Kafka metadata headers
             */
            @Header(KafkaHeaders.RECEIVED_KEY)
            String key,

            @Header(KafkaHeaders.RECEIVED_TOPIC)
            String topic,

            @Header(KafkaHeaders.RECEIVED_PARTITION)
            int partition,

            @Header(KafkaHeaders.OFFSET)
            long offset
    ) {


        log.info(
                """
                        
                ================================
                VIDEO UPLOADED EVENT RECEIVED
                ================================
                Topic      : {}
                Partition  : {}
                Offset     : {}
                Key        : {}
                Movie ID   : {}
                Video Key  : {}
                ================================
                """,
                topic,
                partition,
                offset,
                key,
                event.getMovieId(),
                event.getVideoKey()
        );

        try {

            /**
             * Trigger encoding pipeline
             */
            encodingService.encodeVideo(event);

            log.info(
                    "Encoding pipeline completed for movie: {}",
                    event.getMovieId()
            );

        } catch (Exception e) {

            /**
             * IMPORTANT:
             *
             * Never silently swallow exceptions.
             *
             * Depending on your Kafka retry strategy,
             * throwing exception here may:
             *
             * - retry message
             * - send to DLQ
             * - stop consumer
             *
             * Current behavior:
             * log + rethrow
             */
            log.error(
                    "Failed to process uploaded video event for movie: {}",
                    event.getMovieId(),
                    e
            );

            throw e;
        }
    }
}