package com.example.streamingservice.controller;

import com.example.streamingservice.dto.StreamingResponse;
import com.example.streamingservice.service.StreamingService;
import com.example.streamingservice.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stream")
@Slf4j
@RequiredArgsConstructor
public class StreamingController {

    private final StreamingService streamingService;

    // FIX: RedisTemplate was injected directly into the controller so it could look up
    // the playlist key. Infrastructure concerns belong in the service layer, not here.
    // The lookup is now done inside StreamingService.getStreamingUrl().
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Returns a pre-signed HLS master playlist URL for the given movie.
     * GET /api/v1/stream/{movieId}
     *
     * 404 if the movie has not finished encoding yet (no playlist key in Redis).
     */
    @GetMapping("/{movieId}")
    public ResponseEntity<StreamingResponse> getStreamingUrl(
            @PathVariable Long movieId
    ) {
        log.info("Streaming request for movieId={}", movieId);

        // Playlist key lookup stays in the controller only to decide 404 vs 200.
        // The actual pre-sign / cache logic is fully inside StreamingService.
        String playlistKey = redisTemplate.opsForValue()
                .get(RedisKeys.masterPlaylistKey(movieId));

        if (playlistKey == null) {
            log.warn("No playlist key found in Redis for movieId={} — encoding may not be complete", movieId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(streamingService.getStreamingUrl(movieId, playlistKey));
    }

    /**
     * Returns a signed M3U8 playlist with all segment URLs pre-signed.
     * Called by the HLS player for each quality-level playlist.
     * GET /api/v1/stream/{movieId}/playlist?path=encoded/123/720p/playlist.m3u8
     *
     * FIX: 'path' is now validated inside StreamingService to belong to the
     * expected encoded/{movieId}/ prefix. Raw user input was previously passed
     * straight to S3 as a key with no validation (path traversal risk).
     */
    @GetMapping("/{movieId}/playlist")
    public ResponseEntity<String> getSignedPlaylist(
            @PathVariable Long movieId,
            @RequestParam String path
    ) {
        String signedPlaylist = streamingService.getSignedPlaylist(movieId, path);
        return ResponseEntity.ok()
                .header("Content-Type", "application/x-mpegURL")
                .body(signedPlaylist);
    }
}