package com.example.streamingservice.service;

import com.example.streamingservice.dto.StreamingResponse;
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

    @Value("${aws.s3.presigned-url-expiry}")
    private long presignedUrlExpiry; //60mins

    //redis key for caching url
    private final static String STREAMING_URL_CACHE_PREFIX = "streaming:url:";

    /**
     * get streaming url for a movie
     * flow:
     * 1.check redis cache for existing presigned url
     * 2. if cached then return immediately
     * 3. if not cached = generate new presigned url from s3
     * 4. cache the url in redis
     * 5. return streaming url
     *
     * why prsigned url?
     * - s3 buced is private locker room  - videso are not publicly accessible
     * - presigned url gives temporary access(x-minutes_
     * - prevent unauthorizedd video downloads
     */

    public StreamingResponse getStreamingUrl(Long movieId, String playlistKey) {
        log.info("Getting streaming url for movieId :  {}", movieId);
        String cacheKey = STREAMING_URL_CACHE_PREFIX + movieId;
        // check redis cache first
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        if (cachedUrl != null) {
            log.info("Returning cached streaming url for movie: {}", movieId);
            return new StreamingResponse(
                    movieId,
                    cachedUrl,
                    "1080p, 720p, 480p, 360p",
                    presignedUrlExpiry
            );
        }
        //generate presigned url from s3
        log.info("Generating new presigned url for movie: {}", movieId);
        String presignedUrl = generatePresignedUrl(playlistKey);
        //cache in redis for 55 minutes -> 5mins less than actual expiry to avoid edgecases

        redisTemplate.opsForValue().set(
                cacheKey,
                presignedUrl,
                55,
                TimeUnit.MINUTES
        );
        log.info("Streaming URL generated and cached for movie {}", movieId);
        return new StreamingResponse(
                movieId,
                presignedUrl,
                "1080p, 720p, 480p, 360p",
                presignedUrlExpiry
        );
    }

    /**
     * this is the KEY METHOD that makes everything secure
     * @param movieId
     * @param playlistPath
     * @return
     */

    public String getSignedPlaylist(Long movieId, String playlistPath) {
        //get basepath for this playlist
        String basePath = playlistPath.substring(0,playlistPath.lastIndexOf('/') + 1);
        //read m3u8 from s3
        String m3u8Content = readFromS3(playlistPath);
        //rewrite each line that is a segment or playlist reference
        String signedContent = rewriteM3u8SignedUrls(m3u8Content, basePath);
        return signedContent;
    }

    private String rewriteM3u8SignedUrls(String m3u8Content, String basePath) {
        StringBuilder rewritten = new StringBuilder();
        for (String line : m3u8Content.split("\n")) {
            String trimmed = line.trim();
            //skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                rewritten.append(line).append("\n");
                continue;
            }
            //this is a segment or playlist refernce.bulid fill s3key and sign it
            String fullKey = basePath + trimmed;
            String signedUrl = generatePresignedUrl(fullKey);
            rewritten.append(signedUrl).append("\n");

        }
        return rewritten.toString();
    }

    /**
     * read file content from s3

     */
    private String readFromS3(String s3Key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();
        ResponseInputStream<GetObjectResponse> response =
                s3Client.getObject(request);
        return new BufferedReader(new InputStreamReader(response))
                .lines()
                .collect(Collectors.joining("\n"));
    }


    //generate presigned url for s3 object
    // url expired after configured time
    private String generatePresignedUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiry))
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest)
                .url().toString();
    }
    //invalidate cache streaming url
    //called when video is reencoded or updated
    public void invalidateCache(Long movieId) {
        String cacheKey = STREAMING_URL_CACHE_PREFIX + movieId;
        redisTemplate.delete(cacheKey);
        log.info("Streaming URL cache invalidated for movie: {}" , movieId);

    }

}
