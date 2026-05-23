package com.example.encodingservice.service;

import com.example.encodingservice.event.VideoEncodedEvent;
import com.example.encodingservice.event.VideoUploadedEvent;
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

    /**
     * Kafka topic name from config
     */
    @Value("${kafka.topics.video-encoded}")
    private String videoEncodedTopic;

    /**
     * Adaptive bitrate ladder
     *
     * FORMAT:
     * width, bitrate(kbps), height
     */
    private static final List<int[]> VIDEO_QUALITIES = Arrays.asList(
            new int[]{1920, 5000, 1080},
            new int[]{1280, 2800, 720},
            new int[]{854, 1200, 480},
            new int[]{640, 800, 360}
    );

    /**
     * Main Encoding Pipeline
     *
     * STEPS:
     * 1. Create isolated job directory
     * 2. Download raw video from S3
     * 3. Validate input video
     * 4. Encode into multiple HLS renditions
     * 5. Generate master playlist
     * 6. Upload encoded assets to S3
     * 7. Publish success/failure Kafka event
     * 8. Cleanup temp files
     */
    public void encodeVideo(VideoUploadedEvent event) {

        log.info("Starting encoding pipeline for movie: {}", event.getMovieId());

        /**
         * Unique job directory
         *
         * Prevents collisions when:
         * - same movie encoded simultaneously
         * - retries happen
         * - multiple workers exist
         */
        String jobId = UUID.randomUUID().toString();

        String jobPath = Paths.get(basePath, jobId).toString();

        try {

            /**
             * Create temp directories
             */
            Files.createDirectories(Paths.get(jobPath));
            Files.createDirectories(Paths.get(jobPath, "encoded"));

            /**
             * Step 1:
             * Download raw video from S3
             */
            String localVideoPath =
                    Paths.get(jobPath, "raw_video.mp4").toString();

            downloadFromS3(event.getBucketName(),event.getVideoKey(), localVideoPath);

            log.info("Raw video downloaded: {}", localVideoPath);

            /**
             * Step 2:
             * Validate video file
             */
            validateVideoFile(localVideoPath);

            /**
             * Step 3:
             * Encode multiple renditions
             */
            for (int[] quality : VIDEO_QUALITIES) {

                int width = quality[0];
                int bitrate = quality[1];
                int height = quality[2];

                /**
                 * Correct directory structure
                 */
                String qualityDir =
                        Paths.get(jobPath, "encoded", height + "p")
                                .toString();

                Files.createDirectories(Paths.get(qualityDir));

                encodeToHLS(
                        localVideoPath,
                        qualityDir,
                        width,
                        height,
                        bitrate
                );

                log.info("Encoded {}p successfully", height);
            }

            /**
             * Step 4:
             * Generate master playlist
             */
            String masterPlaylistPath =
                    Paths.get(jobPath, "encoded", "master.m3u8")
                            .toString();

            generateMasterPlaylist(masterPlaylistPath);

            log.info("Master playlist generated");

            /**
             * Step 5:
             * Upload encoded assets to S3
             */
            String encodedPrefix =
                    "encoded/" + event.getMovieId() + "/";

            uploadEncodedFilesToS3(
                    Paths.get(jobPath, "encoded").toString(),
                    encodedPrefix
            );

            log.info("Encoded assets uploaded to S3");

            /**
             * Step 6:
             * Generate region-safe S3 URL
             */
            String masterPlaylistKey =
                    encodedPrefix + "master.m3u8";

            String hlsUrl = s3Client.utilities()
                    .getUrl(builder -> builder
                            .bucket(bucketName)
                            .key(masterPlaylistKey))
                    .toExternalForm();

            /**
             * Publish success event
             */
            VideoEncodedEvent encodedEvent =
                    new VideoEncodedEvent(
                            event.getMovieId(),
                            hlsUrl,
                            masterPlaylistKey,
                            true,
                            null
                    );

            kafkaTemplate.send(
                    videoEncodedTopic,
                    String.valueOf(event.getMovieId()),
                    encodedEvent
            );

            log.info(
                    "VideoEncodedEvent published for movie: {}",
                    event.getMovieId()
            );

        } catch (Exception e) {

            /**
             * FIXED:
             * Proper stacktrace logging
             */
            log.error(
                    "Encoding failed for movie: {}",
                    event.getMovieId(),
                    e
            );

            /**
             * Publish failure event
             */
            VideoEncodedEvent failureEvent =
                    new VideoEncodedEvent(
                            event.getMovieId(),
                            null,
                            null,
                            false,
                            e.getMessage()
                    );

            kafkaTemplate.send(
                    videoEncodedTopic,
                    String.valueOf(event.getMovieId()),
                    failureEvent
            );

        } finally {

            /**
             * Always cleanup temp files
             */
            cleanupTempFiles(jobPath);
        }
    }

    /**
     * Download raw file from S3
     */
    private void downloadFromS3(
            String bucket,
            String s3Key,
            String localPath
    ) {

        GetObjectRequest request =
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .build();

        s3Client.getObject(
                request,
                Paths.get(localPath)
        );
    }

    /**
     * Basic validation
     *
     * In production:
     * - ffprobe validation
     * - codec validation
     * - duration validation
     * - corruption checks
     */
    private void validateVideoFile(String videoPath)
            throws IOException {

        Path path = Paths.get(videoPath);

        if (!Files.exists(path)) {
            throw new RuntimeException("Video file does not exist");
        }

        if (Files.size(path) == 0) {
            throw new RuntimeException("Video file is empty");
        }
    }

    /**
     * Encode one rendition into HLS
     *
     * Generates:
     * - playlist.m3u8
     * - segment_001.ts
     * - segment_002.ts
     * etc.
     */
    private void encodeToHLS(
            String inputPath,
            String outputDir,
            int width,
            int height,
            int bitrate
    ) throws IOException, InterruptedException {

        String playlistPath =
                Paths.get(outputDir, "playlist.m3u8")
                        .toString();

        String segmentPattern =
                Paths.get(outputDir, "segment_%03d.ts")
                        .toString();

        /**
         * Production-grade FFmpeg command
         */
        List<String> command = Arrays.asList(

                ffmpegPath,

                /**
                 * Input file
                 */
                "-i", inputPath,

                /**
                 * Video scaling
                 */
                "-vf", "scale=" + width + ":" + height,

                /**
                 * Video codec
                 */
                "-c:v", "libx264",

                /**
                 * Video bitrate
                 */
                "-b:v", bitrate + "k",

                /**
                 * Audio codec
                 */
                "-c:a", "aac",

                /**
                 * Audio bitrate
                 */
                "-b:a", "128k",

                /**
                 * GOP settings
                 *
                 * Critical for ABR switching
                 */
                "-g", "48",
                "-keyint_min", "48",
                "-sc_threshold", "0",

                /**
                 * HLS settings
                 */
                "-hls_time", "10",

                /**
                 * FIXED:
                 * Added missing '-'
                 */
                "-hls_list_size", "0",

                /**
                 * VOD playlist
                 */
                "-hls_playlist_type", "vod",

                /**
                 * MPEGTS segment type
                 */
                "-hls_segment_type", "mpegts",

                /**
                 * Segment naming
                 */
                "-hls_segment_filename",
                segmentPattern,

                /**
                 * Thread control
                 */
                "-threads", "2",

                /**
                 * Output format
                 */
                "-f", "hls",

                /**
                 * Output playlist
                 */
                playlistPath
        );

        ProcessBuilder processBuilder =
                new ProcessBuilder(command);

        /**
         * Merge stdout + stderr
         */
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        /**
         *
         * Prevents process deadlock
         */
        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream()
                                )
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {
                log.info("[FFMPEG] {}", line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException(
                    "FFmpeg failed with exit code: " + exitCode
            );
        }
    }

    /**
     * Generate master playlist
     *
     * This is the first file
     * downloaded by video players
     */
    private void generateMasterPlaylist(
            String masterPlaylistPath
    ) throws IOException {

        StringBuilder master = new StringBuilder();

        master.append("#EXTM3U\n");

        /**
         * FIXED:
         * Added missing #
         */
        master.append("#EXT-X-VERSION:3\n\n");

        for (int[] q : VIDEO_QUALITIES) {

            int width = q[0];
            int bitrate = q[1];
            int height = q[2];

            /**
             * Include audio bitrate
             */
            int totalBandwidth =
                    (bitrate + 128) * 1000;

            master.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                    .append(totalBandwidth)
                    .append(",RESOLUTION=")
                    .append(width)
                    .append("x")
                    .append(height)
                    .append(",CODECS=\"avc1.42E01E,mp4a.40.2\"\n");

            master.append(height)
                    .append("p/playlist.m3u8\n\n");
        }

        Files.writeString(
                Paths.get(masterPlaylistPath),
                master.toString()
        );
    }

    /**
     * Upload all encoded assets recursively
     */
    private void uploadEncodedFilesToS3(
            String localDir,
            String s3Prefix
    ) throws IOException {

        File directory = new File(localDir);

        uploadDirectoryToS3(
                directory,
                localDir,
                s3Prefix
        );
    }

    /**
     * Recursive directory upload
     */
    private void uploadDirectoryToS3(
            File dir,
            String baseDir,
            String s3Prefix
    ) throws IOException {

        /**
         * Null safety
         */
        File[] files = dir.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {

            if (file.isDirectory()) {

                uploadDirectoryToS3(
                        file,
                        baseDir,
                        s3Prefix
                );

            } else {

                String relativePath =
                        file.getAbsolutePath()
                                .substring(baseDir.length() + 1)
                                .replace("\\", "/");

                String s3Key =
                        s3Prefix + relativePath;

                PutObjectRequest request =
                        PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(s3Key)
                                .contentType(
                                        determineContentType(file)
                                )
                                .build();

                s3Client.putObject(
                        request,
                        RequestBody.fromFile(file)
                );

                log.info("Uploaded to S3: {}", s3Key);
            }
        }
    }

    /**
     * Determine correct content type
     */
    private String determineContentType(File file) {

        String name = file.getName();

        if (name.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }

        if (name.endsWith(".ts")) {
            return "video/mp2t";
        }

        if (name.endsWith(".mp4")) {
            return "video/mp4";
        }

        return "application/octet-stream";
    }

    /**
     * Cleanup temp files
     */
    private void cleanupTempFiles(String jobPath) {

        try {

            Path dirPath = Paths.get(jobPath);

            if (Files.exists(dirPath)) {

                Files.walk(dirPath)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);

                log.info(
                        "Temp files cleaned for job: {}",
                        jobPath
                );
            }

        } catch (IOException e) {

            log.warn(
                    "Failed to cleanup temp files: {}",
                    e.getMessage()
            );
        }
    }
}