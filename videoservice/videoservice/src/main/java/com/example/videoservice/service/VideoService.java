package com.example.videoservice.service;

import com.example.videoservice.dto.VideoUploadResponse;
import com.example.videoservice.event.VideoUploadedEvent;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoService {

    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoUploadedEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName; //which we created in aws

    @Value("${kafka.topics.video-uploaded}")
    private String videoUploadedTopic;

    private static final long MAX_VIDEO_SIZE = 2L * 1024*1024*1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(
                    "video/mp4",
                    "video/x-matroska",
                    "video/quicktime"
            );

    //safe file name regex
    private static final Pattern SAFE_FILENAME = Pattern.compile("[^a-zA-Z0-9._-]");

    /**
     * upload video to aws s3 and publish videoUploadedEvent to Kafka
     *
     * flow :
     * 1. Receive multipart video file
     * 2. Generate unique s3 key
     * 3. Upload to s3
     * 4. Publish VideoUploadedEvent to kafka
     * 5. Encoding service picks up and start ffmpeg
     */

    public VideoUploadResponse uploadVideo(Long movieId, MultipartFile file) throws IOException {

        long start = System.currentTimeMillis();
        validateFile(file);

        String correlationId = UUID.randomUUID().toString();

        String sanitizedFilename = sanitizeFilename(
                Objects.requireNonNull(file.getOriginalFilename())
        );

        //generate unique s3Key for raw video
        //format: raw/movieId/uuid_fileName
        String videoKey = "raw/" + movieId + "/" +
                UUID.randomUUID() + "_" + sanitizedFilename;

        log.info(
                "Uploading video for movie: {} key: {}",
                movieId,
                videoKey
        );

        Map<String, String> metadata = new HashMap<>();
        metadata.put("movieId", String.valueOf(movieId));
        metadata.put("uploadedAt", LocalDateTime.now().toString());
        metadata.put("correlationId", correlationId);

        //now we need to build the request, request to send the video file to s3

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(videoKey)
                .contentType(file.getContentType())
                .metadata(metadata)
                .contentLength(file.getSize() )
                .build();

        s3Client.putObject(putObjectRequest,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
                );
        log.info("Video is uploaded to s3 successfully, Key:{} ", videoKey);

        //publish video to kafka
        //encoding service will consume, this and start ffmpeg processing

        VideoUploadedEvent event = VideoUploadedEvent.builder()
                        .movieId(movieId)
                                .videoKey(videoKey).bucketName(bucketName).originalFileName(sanitizedFilename)
                        .fileSizeInBytes(file.getSize()).contentType(file.getContentType()).uploadedAt(LocalDateTime.now())
                        .correlationId(correlationId)
                                .build();
        kafkaTemplate.send(
                videoUploadedTopic,
                String.valueOf(movieId),
                event
        );
        log.info("VideoUploadedEvent published for movie:{} ", movieId);

        long duration =  System.currentTimeMillis() - start;
        log.info(
                "Upload completed in {} ms",
                duration
        );
        return VideoUploadResponse.builder()
                .movieId(movieId)
                .videoKey(videoKey)
                .status("UPLOADED")
                .message(
                        "Video uploaded successfully"
                )
                .build();
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException(
                    "Uploaded file is empty"
            );
        }

        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new RuntimeException(
                    "File exceeds maximum allowed size"
            );
        }

        String contentType =
                file.getContentType();

        if (contentType == null ||
                        !ALLOWED_CONTENT_TYPES.contains(contentType)
        ) {
            throw new RuntimeException(
                    "Unsupported video format"
            );
        }
    }

    private String sanitizeFilename(String filename) {
        return SAFE_FILENAME
                .matcher(filename)
                .replaceAll("_");
    }
}
