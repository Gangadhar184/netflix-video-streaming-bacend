package com.example.videoservice.service;

import com.example.videoservice.event.VideoUploadEvent;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;


import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoService {

    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoUploadEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName; //which we created in aws

    private static final String VIDEO_UPLOADED_TOPIC = "video.uploaded";

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

    public String uploadVideo(Long movieId, MultipartFile file) throws IOException {
        log.info("Starting video upload for movie: {} file: {}" , movieId, file.getOriginalFilename());

        //generate unique s3Key for raw video
        //format: raw/movieId/uuid_fileName
        String videoKey = "raw/" + movieId + "/" +
                UUID.randomUUID() + "_" + file.getOriginalFilename();


        //now we need to build the request, request to send the video file to s3

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(videoKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize() )
                .build();

        s3Client.putObject(putObjectRequest,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
                );
        log.info("Video is uploaded to s3 successfully, Key:{} ", videoKey);

        //publish video to kafka
        //encoding service will consume, this and start ffmpeg processing

        VideoUploadEvent event = new VideoUploadEvent(
                movieId,
                videoKey,
                bucketName,
                file.getOriginalFilename(),
                file.getSize()
        );
        kafkaTemplate.send(VIDEO_UPLOADED_TOPIC, movieId.toString(), event);
        log.info("VideoUploadedEvent published for movie:{} ", movieId);

        return videoKey;
    }

}
