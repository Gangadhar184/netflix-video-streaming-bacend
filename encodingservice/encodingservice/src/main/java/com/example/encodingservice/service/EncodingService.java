package com.example.encodingservice.service;

import com.example.encodingservice.event.VideoEncodedEvent;
import com.example.encodingservice.event.VideoUploadedEvent;
import com.example.encodingservice.exception.FFmpegException;
import com.example.encodingservice.exception.InvalidVideoFileException;
import com.example.encodingservice.model.VideoQuality;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EncodingService {

    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoEncodedEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Value("${encoding.base-path}")
    private String basePath;

    @Value("${kafka.topics.video-encoded}")
    private String videoEncodedTopic;

    /**
     * Adaptive bitrate ladder.
     *
     * Using VideoQuality records instead of int[] to eliminate
     * index-based access (quality[0], quality[1], quality[2]) which
     * is fragile and requires remembering the order.
     */
    private static final List<VideoQuality> VIDEO_QUALITIES = List.of(
            new VideoQuality(1920, 1080, 5000),
            new VideoQuality(1280, 720,  2800),
            new VideoQuality(854,  480,  1200),
            new VideoQuality(640,  360,  800)
    );

    /**
     * Main encoding pipeline.
     *
     * Steps:
     *  1. Create isolated job directory (prevents concurrent job collisions)
     *  2. Download raw video from S3
     *  3. Validate the downloaded file
     *  4. Encode into multiple HLS renditions
     *  5. Generate master playlist
     *  6. Upload encoded assets to S3
     *  7. Publish success/failure Kafka event
     *  8. Always clean up temp files
     */
    public void encodeVideo(VideoUploadedEvent event) {
        log.info("Starting encoding pipeline for movieId={}, correlationId={}",
                event.getMovieId(), event.getCorrelationId());

        String jobId   = UUID.randomUUID().toString();
        String jobPath = Paths.get(basePath, jobId).toString();

        try {
            // --- 1. Create temp directories ---
            Files.createDirectories(Paths.get(jobPath, "encoded"));

            // --- 2. Download raw video from S3 ---
            String localVideoPath = Paths.get(jobPath, "raw_video.mp4").toString();
            downloadFromS3(event.getBucketName(), event.getVideoKey(), localVideoPath);
            log.info("Raw video downloaded to: {}", localVideoPath);

            // --- 3. Validate ---
            validateVideoFile(localVideoPath);

            // --- 4. Encode each rendition ---
            for (VideoQuality quality : VIDEO_QUALITIES) {
                String qualityDir = Paths.get(jobPath, "encoded", quality.height() + "p").toString();
                Files.createDirectories(Paths.get(qualityDir));
                encodeToHLS(localVideoPath, qualityDir, quality);
                log.info("Encoded {}p successfully", quality.height());
            }

            // --- 5. Generate master playlist ---
            String masterPlaylistPath = Paths.get(jobPath, "encoded", "master.m3u8").toString();
            generateMasterPlaylist(masterPlaylistPath);
            log.info("Master playlist generated");

            // --- 6. Upload encoded assets to S3 ---
            String encodedPrefix = "encoded/" + event.getMovieId() + "/";
            // FIX: was called with 2 args but defined with 3 (bucketName, localDir, s3Prefix)
            uploadEncodedFilesToS3(bucketName, Paths.get(jobPath, "encoded").toString(), encodedPrefix);
            log.info("Encoded assets uploaded to S3 under prefix: {}", encodedPrefix);

            // --- 7. Publish success event ---
            // FIX: do NOT construct a public S3 URL here.
            // content-service stores the S3 key; the streaming service generates
            // pre-signed URLs at request time. Public URLs break on private buckets.
            String masterPlaylistKey = encodedPrefix + "master.m3u8";

            VideoEncodedEvent successEvent = VideoEncodedEvent.builder()
                    .movieId(event.getMovieId())
                    .success(true)
                    .hlsMasterPlaylistKey(masterPlaylistKey)
                    .errorMessage(null)
                    .completedAt(Instant.now())
                    .build();

            publishEvent(successEvent, event.getMovieId());
            log.info("VideoEncodedEvent (success) published for movieId={}", event.getMovieId());

        } catch (Exception e) {
            log.error("Encoding failed for movieId={}", event.getMovieId(), e);

            VideoEncodedEvent failureEvent = VideoEncodedEvent.builder()
                    .movieId(event.getMovieId())
                    .success(false)
                    .hlsMasterPlaylistKey(null)
                    .errorMessage(e.getMessage())
                    .completedAt(Instant.now())
                    .build();

            publishEvent(failureEvent, event.getMovieId());

        } finally {
            // Always clean up regardless of success or failure
            cleanupTempFiles(jobPath);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void downloadFromS3(String bucket, String s3Key, String localPath) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();
        s3Client.getObject(request, Paths.get(localPath));
    }

    /**
     * Basic sanity check on the downloaded file.
     * In production, extend with ffprobe: codec validation, duration check, corruption scan.
     */
    private void validateVideoFile(String videoPath) throws IOException {
        Path path = Paths.get(videoPath);

        if (!Files.exists(path)) {
            // FIX: was bare RuntimeException
            throw new InvalidVideoFileException("Video file does not exist at: " + videoPath);
        }

        if (Files.size(path) == 0) {
            throw new InvalidVideoFileException("Downloaded video file is empty: " + videoPath);
        }
    }

    /**
     * Encode one rendition into HLS segments.
     *
     * Outputs:
     *  - playlist.m3u8
     *  - segment_000.ts, segment_001.ts, ...
     */
    private void encodeToHLS(
            String inputPath,
            String outputDir,
            VideoQuality quality   // FIX: was (int width, int height, int bitrate) with int[]
    ) throws IOException, InterruptedException {

        String playlistPath    = Paths.get(outputDir, "playlist.m3u8").toString();
        String segmentPattern  = Paths.get(outputDir, "segment_%03d.ts").toString();

        List<String> command = Arrays.asList(
                ffmpegPath,
                "-i", inputPath,
                "-vf", "scale=" + quality.width() + ":" + quality.height(),
                "-c:v", "libx264",
                "-b:v", quality.bitrateKbps() + "k",
                "-c:a", "aac",
                "-b:a", "128k",
                // GOP settings — critical for ABR switching
                "-g", "48",
                "-keyint_min", "48",
                "-sc_threshold", "0",
                // HLS settings
                "-hls_time", "10",
                "-hls_list_size", "0",
                "-hls_playlist_type", "vod",
                "-hls_segment_type", "mpegts",
                "-hls_segment_filename", segmentPattern,
                "-threads", "2",
                "-f", "hls",
                playlistPath
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true); // merges stderr into stdout

        Process process = processBuilder.start();

        // IMPORTANT: must drain stdout before waitFor() to prevent process deadlock
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[FFmpeg] {}", line); // debug to avoid flooding info logs
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            // FIX: was bare RuntimeException
            throw new FFmpegException(
                    "FFmpeg failed with exit code " + exitCode +
                            " for rendition " + quality.height() + "p");
        }
    }

    /**
     * Generate HLS master playlist referencing all quality renditions.
     * This is the first file a video player fetches.
     */
    private void generateMasterPlaylist(String masterPlaylistPath) throws IOException {
        StringBuilder master = new StringBuilder();
        master.append("#EXTM3U\n");
        master.append("#EXT-X-VERSION:3\n\n");

        for (VideoQuality q : VIDEO_QUALITIES) {
            int totalBandwidth = (q.bitrateKbps() + 128) * 1000; // video + audio

            master.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                    .append(totalBandwidth)
                    .append(",RESOLUTION=")
                    .append(q.width()).append("x").append(q.height())
                    .append(",CODECS=\"avc1.42E01E,mp4a.40.2\"\n");

            master.append(q.height()).append("p/playlist.m3u8\n\n");
        }

        Files.writeString(Paths.get(masterPlaylistPath), master.toString());
    }

    /**
     * Upload all encoded HLS files recursively to S3.
     *
     * FIX: original method was called with 2 args (localDir, s3Prefix)
     * but defined with 3 args (bucketName, localDir, s3Prefix) — compile error.
     * Signature is now consistent: (bucketName, localDir, s3Prefix).
     */
    private void uploadEncodedFilesToS3(
            String bucketName,
            String localDir,
            String s3Prefix
    ) throws IOException {
        uploadDirectoryToS3(bucketName, new File(localDir), localDir, s3Prefix);
    }

    private void uploadDirectoryToS3(
            String bucketName,
            File dir,
            String baseDir,
            String s3Prefix
    ) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return; // null-safe: listFiles() returns null for non-directories

        for (File file : files) {
            if (file.isDirectory()) {
                uploadDirectoryToS3(bucketName, file, baseDir, s3Prefix);
            } else {
                String relativePath = file.getAbsolutePath()
                        .substring(baseDir.length() + 1)
                        .replace("\\", "/"); // Windows path safety

                String s3Key = s3Prefix + relativePath;

                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType(determineContentType(file))
                        .build();

                s3Client.putObject(request, RequestBody.fromFile(file));
                log.info("Uploaded: {}", s3Key);
            }
        }
    }

    private String determineContentType(File file) {
        String name = file.getName();
        if (name.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (name.endsWith(".ts"))   return "video/mp2t";
        if (name.endsWith(".mp4")) return "video/mp4";
        return "application/octet-stream";
    }

    /**
     * Publish a VideoEncodedEvent with a completion callback.
     * FIX: was fire-and-forget; silent Kafka failures left content-service
     * waiting forever for a status update.
     */
    private void publishEvent(VideoEncodedEvent event, Long movieId) {
        kafkaTemplate.send(videoEncodedTopic, String.valueOf(movieId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish VideoEncodedEvent for movieId={}", movieId, ex);
                    } else {
                        log.info("VideoEncodedEvent published for movieId={}, offset={}",
                                movieId, result.getRecordMetadata().offset());
                    }
                });
    }

    private void cleanupTempFiles(String jobPath) {
        try {
            Path dirPath = Paths.get(jobPath);
            if (Files.exists(dirPath)) {
                Files.walk(dirPath)
                        .sorted(Comparator.reverseOrder()) // delete children before parents
                        .map(Path::toFile)
                        .forEach(File::delete);
                log.info("Temp files cleaned for job: {}", jobPath);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temp files at {}: {}", jobPath, e.getMessage());
        }
    }
}