package com.example.encodingservice.service;

import com.example.encodingservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for uploaded video events.
 *
 * Responsibilities:
 *  1. Listen for video.uploaded events
 *  2. Trigger the encoding pipeline
 *  3. Re-throw exceptions so Kafka retry / DLQ config takes effect
 *
 * Flow:
 *   VideoService → video.uploaded topic → VideoEventConsumer → EncodingService → video.encoded topic
 *
 * On exception:
 *   Re-throwing lets your Kafka retry/DLQ configuration (spring.kafka.listener.*)
 *   decide whether to retry, send to DLQ, or stop the consumer.
 *   Never silently swallow exceptions here — content-service would never receive
 *   the encoded event and the movie would stay stuck in ENCODING status.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VideoEventConsumer {

    private final EncodingService encodingService;

    @KafkaListener(
            topics = "${kafka.topics.video-uploaded}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeVideoUploadedEvent(
            VideoUploadedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY)       String key,
            @Header(KafkaHeaders.RECEIVED_TOPIC)     String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET)             long offset
    ) {
        log.info("""
                
                ================================
                VIDEO UPLOADED EVENT RECEIVED
                ================================
                Topic         : {}
                Partition     : {}
                Offset        : {}
                Key           : {}
                Movie ID      : {}
                Video Key     : {}
                Correlation ID: {}
                ================================
                """,
                topic, partition, offset, key,
                event.getMovieId(), event.getVideoKey(), event.getCorrelationId());

        try {
            encodingService.encodeVideo(event);
            log.info("Encoding pipeline completed for movieId={}", event.getMovieId());
        } catch (Exception e) {
            log.error("Failed to process video uploaded event for movieId={}",
                    event.getMovieId(), e);
            // Re-throw so Kafka retry / DLQ configuration handles it
            throw e;
        }
    }
}