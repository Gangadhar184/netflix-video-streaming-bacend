package com.example.videoservice.service;

import com.example.videoservice.dto.VideoUploadResponse;
import com.example.videoservice.event.VideoUploadedEvent;
import com.example.videoservice.exception.InvalidVideoException;
import com.example.videoservice.exception.VideoUploadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoService {

    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoUploadedEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${kafka.topics.video-uploaded}")
    private String videoUploadedTopic;

    private static final long MAX_VIDEO_SIZE = 2L * 1024 * 1024 * 1024; // 2 GB

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "video/mp4",
            "video/x-matroska",
            "video/quicktime"
    );

    // Replaces any character that isn't alphanumeric, dot, hyphen, or underscore
    private static final Pattern SAFE_FILENAME = Pattern.compile("[^a-zA-Z0-9._-]");

    /**
     * Upload video to AWS S3 and publish VideoUploadedEvent to Kafka.
     *
     * Flow:
     *  1. Validate the multipart file (size, content type, filename)
     *  2. Generate a unique S3 key: raw/{movieId}/{uuid}_{sanitizedFilename}
     *  3. Upload to S3
     *  4. Publish VideoUploadedEvent to Kafka
     *  5. Encoding service picks it up and starts FFmpeg
     */
    public VideoUploadResponse uploadVideo(Long movieId, MultipartFile file) {
        long start = System.currentTimeMillis();

        // validateFile checks empty, size, content type, AND null/blank filename
        validateFile(file);

        String correlationId = UUID.randomUUID().toString();
        String sanitizedFilename = sanitizeFilename(file.getOriginalFilename());

        // Unique S3 key prevents collisions across re-uploads of the same movie
        String videoKey = "raw/" + movieId + "/" + UUID.randomUUID() + "_" + sanitizedFilename;

        log.info("Uploading video for movieId={}, key={}, correlationId={}",
                movieId, videoKey, correlationId);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("movieId", String.valueOf(movieId));
        metadata.put("correlationId", correlationId);
        metadata.put("uploadedAt", Instant.now().toString());

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(videoKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .metadata(metadata)
                .build();

        try {
            s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException e) {
            // Wrap and re-throw so the controller doesn't need to declare throws IOException,
            // and the caller gets a meaningful 500 rather than a raw stack trace
            throw new VideoUploadException(
                    "Failed to read uploaded file for movieId=" + movieId, e);
        }

        log.info("Video uploaded to S3 successfully, key={}", videoKey);

        Instant uploadedAt = Instant.now();

        VideoUploadedEvent event = VideoUploadedEvent.builder()
                .movieId(movieId)
                .videoKey(videoKey)
                .bucketName(bucketName)
                .originalFileName(sanitizedFilename)
                .fileSizeInBytes(file.getSize())
                .contentType(file.getContentType())
                .uploadedAt(uploadedAt)
                .correlationId(correlationId)
                .build();

        // Handle Kafka send result — a silent failure here means encoding never starts
        kafkaTemplate.send(videoUploadedTopic, String.valueOf(movieId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish VideoUploadedEvent for movieId={}, key={}",
                                movieId, videoKey, ex);
                    } else {
                        log.info("VideoUploadedEvent published for movieId={}, offset={}",
                                movieId, result.getRecordMetadata().offset());
                    }
                });

        long duration = System.currentTimeMillis() - start;
        log.info("Upload flow completed in {} ms for movieId={}", duration, movieId);

        return VideoUploadResponse.builder()
                .movieId(movieId)
                .videoKey(videoKey)
                .status("UPLOADED")
                .message("Video uploaded successfully")
                .build();
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidVideoException("Uploaded file is empty");
        }

        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new InvalidVideoException(
                    "File size " + file.getSize() + " bytes exceeds the 2 GB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidVideoException(
                    "Unsupported video format: " + contentType +
                            ". Allowed types: " + ALLOWED_CONTENT_TYPES);
        }

        // Validate filename here rather than hitting NPE later in uploadVideo
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new InvalidVideoException("File must have a valid filename");
        }
    }

    private String sanitizeFilename(String filename) {
        return SAFE_FILENAME.matcher(filename).replaceAll("_");
    }
}