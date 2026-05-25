package com.example.streamingservice.service;

import com.example.streamingservice.dto.StreamingResponse;
import com.example.streamingservice.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    /** Configurable URL expiry in minutes (e.g. 60). */
    @Value("${aws.s3.presigned-url-expiry}")
    private long presignedUrlExpiry;

    /** Cache signed playlists for this many minutes — short enough to stay fresh for VOD. */
    private static final long SIGNED_PLAYLIST_CACHE_MINUTES = 5;

    /**
     * Returns a pre-signed HLS master playlist URL for the given movie.
     *
     * Flow:
     *  1. Check Redis cache for an existing pre-signed URL
     *  2. If cached, return immediately
     *  3. Otherwise generate a new pre-signed URL from S3
     *  4. Cache it for (presignedUrlExpiry - 5) minutes to avoid returning an
     *     expired URL due to clock skew or slow clients
     *  5. Return the response
     *
     * Why pre-signed URLs?
     *  S3 bucket is private. Pre-signed URLs grant temporary, scoped access
     *  without exposing credentials or making the bucket public.
     */
    public StreamingResponse getStreamingUrl(Long movieId, String playlistKey) {
        log.info("Getting streaming URL for movieId={}", movieId);

        String cacheKey   = RedisKeys.streamingUrlKey(movieId);
        String cachedUrl  = redisTemplate.opsForValue().get(cacheKey);

        if (cachedUrl != null) {
            log.info("Returning cached streaming URL for movieId={}", movieId);
            return buildResponse(movieId, cachedUrl);
        }

        log.info("Generating new pre-signed URL for movieId={}", movieId);
        String presignedUrl = generatePresignedUrl(playlistKey);

        // FIX: was hardcoded 55 — if presignedUrlExpiry changes in config, cache TTL
        // was silently wrong. Now always derived as (expiry - 5), floor at 1 minute.
        long cacheTtlMinutes = Math.max(1, presignedUrlExpiry - 5);
        redisTemplate.opsForValue().set(cacheKey, presignedUrl, cacheTtlMinutes, TimeUnit.MINUTES);

        log.info("Streaming URL generated and cached for movieId={} (TTL={}min)",
                movieId, cacheTtlMinutes);
        return buildResponse(movieId, presignedUrl);
    }

    /**
     * Reads an M3U8 playlist from S3 and rewrites every segment/playlist reference
     * as a pre-signed URL, so the HLS player can fetch segments directly from S3
     * without going through this service on every tick.
     *
     * Results are cached briefly (SIGNED_PLAYLIST_CACHE_MINUTES) because:
     *  - VOD segment lists are immutable
     *  - Players may request the same playlist repeatedly
     *
     * Security: the path parameter is validated to belong to the expected
     * encoded/{movieId}/ prefix before it is used as an S3 key.
     */
    public String getSignedPlaylist(Long movieId, String playlistPath) {
        // FIX: validate path belongs to this movie — prevents path traversal attacks
        // where a caller passes "../other-movie/..." to access arbitrary S3 keys
        validatePlaylistPath(movieId, playlistPath);

        String cacheKey    = RedisKeys.signedPlaylistKey(playlistPath);
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null) {
            log.debug("Returning cached signed playlist for path={}", playlistPath);
            return cachedValue;
        }

        String basePath    = playlistPath.substring(0, playlistPath.lastIndexOf('/') + 1);
        String m3u8Content = readFromS3(playlistPath);
        String signed      = rewriteM3u8WithSignedUrls(m3u8Content, basePath);

        // FIX: cache so repeated player ticks don't hit S3 and re-sign on every call
        redisTemplate.opsForValue().set(
                cacheKey, signed, SIGNED_PLAYLIST_CACHE_MINUTES, TimeUnit.MINUTES);

        return signed;
    }

    /**
     * Invalidates the pre-signed URL cache for a movie.
     * Called by VideoEncodedEventConsumer when a movie is re-encoded so the next
     * request generates a fresh URL pointing at the new playlist key.
     */
    public void invalidateStreamingUrlCache(Long movieId) {
        redisTemplate.delete(RedisKeys.streamingUrlKey(movieId));
        // Also invalidate any cached signed playlists — their S3 paths may have changed
        redisTemplate.delete(RedisKeys.signedPlaylistKey(
                "encoded/" + movieId + "/master.m3u8"));
        log.info("Streaming URL cache invalidated for movieId={}", movieId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private StreamingResponse buildResponse(Long movieId, String url) {
        return new StreamingResponse(movieId, url, "1080p, 720p, 480p, 360p", presignedUrlExpiry);
    }

    /**
     * Validates that the requested playlist path belongs to the expected
     * S3 prefix for this movie. Rejects path traversal attempts.
     */
    private void validatePlaylistPath(Long movieId, String path) {
        String expectedPrefix = "encoded/" + movieId + "/";
        if (path == null || !path.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException(
                    "Invalid playlist path for movieId=" + movieId + ": " + path);
        }
    }

    private String rewriteM3u8WithSignedUrls(String m3u8Content, String basePath) {
        StringBuilder rewritten = new StringBuilder();
        for (String line : m3u8Content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                // Directives and blank lines pass through unchanged
                rewritten.append(line).append("\n");
            } else {
                // Segment or sub-playlist reference — build full S3 key and pre-sign it
                String fullKey   = basePath + trimmed;
                String signedUrl = generatePresignedUrl(fullKey);
                rewritten.append(signedUrl).append("\n");
            }
        }
        return rewritten.toString();
    }

    /**
     * Reads text content from an S3 object.
     *
     * FIX: original code never closed the ResponseInputStream, which wraps an
     * HTTP connection. Each call leaked a connection from the S3 client pool.
     * try-with-resources ensures the stream is always closed.
     */
    private String readFromS3(String s3Key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
             BufferedReader reader = new BufferedReader(new InputStreamReader(response))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read S3 object: " + s3Key, e);
        }
    }

    private String generatePresignedUrl(String key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiry))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build())
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}