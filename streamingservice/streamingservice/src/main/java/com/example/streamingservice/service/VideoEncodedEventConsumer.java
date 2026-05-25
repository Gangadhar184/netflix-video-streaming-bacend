package com.example.streamingservice.service;

import com.example.streamingservice.event.VideoEncodedEvent;
import com.example.streamingservice.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoEncodedEventConsumer {

    private final RedisTemplate<String, String> redisTemplate;
    private final StreamingService streamingService;

    // 7 days — long enough for any VOD content; finite so stale keys don't live forever
    private static final long PLAYLIST_KEY_TTL_DAYS = 7;

    @KafkaListener(
            topics = "${kafka.topics.video-encoded}",
            groupId = "${spring.kafka.consumer.group-id}"  // FIX: was hardcoded "streaming-service-group"
    )
    public void consumeVideoEncodedEvent(VideoEncodedEvent event) {
        log.info("Consumed VideoEncodedEvent for movieId={}, success={}",
                event.getMovieId(), event.isSuccess());

        if (event.isSuccess()) {
            String cacheKey = RedisKeys.masterPlaylistKey(event.getMovieId());

            // FIX: was set with no TTL — key lived forever; stale after re-encoding
            redisTemplate.opsForValue().set(
                    cacheKey,
                    event.getHlsMasterPlaylistKey(),
                    PLAYLIST_KEY_TTL_DAYS,
                    TimeUnit.DAYS
            );

            // FIX: invalidate the pre-signed URL cache so the next request
            // generates a fresh URL pointing to the new playlist key
            streamingService.invalidateStreamingUrlCache(event.getMovieId());

            log.info("Master playlist key stored in Redis for movieId={}", event.getMovieId());

        } else {
            // FIX: was log.error(msg, movieId, errorMessage) — SLF4J treats the last
            // Object arg as a Throwable when there are exactly 2 args; errorMessage
            // (a String) was silently dropped or misformatted depending on SLF4J version.
            log.error("Encoding failed for movieId={}, error={}",
                    event.getMovieId(), event.getErrorMessage());
        }
    }
}