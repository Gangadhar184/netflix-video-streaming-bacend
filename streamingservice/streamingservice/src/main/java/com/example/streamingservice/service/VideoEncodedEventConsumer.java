package com.example.streamingservice.service;

import com.example.streamingservice.event.VideoEncodedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoEncodedEventConsumer {
    private final RedisTemplate<String, String> redisTemplate;
    private static final String MASTER_PLAYLIST_KEY_PREFIX = "streaming:playlist:";

    /**
     * Listens to video.encoded kafka topic
     * stores master playlist key in redis when encoding is complete
     * this allows streaming service to quicly find the playlist key by movieid
     */
    @KafkaListener(
            topics = "video.encoded",
            groupId = "streaming-service-group"
    )
    public void consumeVidoeEncodedEvent(VideoEncodedEvent event) {
        log.info("Consumed videoencodedevent for movie: {} success:{}", event.getMovieId(), event.isSuccess());
        if (event.isSuccess()) {
            // store master playlist key in redis
            String cacheKey = MASTER_PLAYLIST_KEY_PREFIX + event.getMovieId();
            redisTemplate.opsForValue().set(cacheKey, event.getMasterPlaylistKey());
            log.info("Master playlist key stored in Redis for movie:{}", event.getMovieId());
        }else{
            log.error("Encoding failed for movie: {}", event.getMovieId(), event.getErrorMessage());
        }

    }














}
