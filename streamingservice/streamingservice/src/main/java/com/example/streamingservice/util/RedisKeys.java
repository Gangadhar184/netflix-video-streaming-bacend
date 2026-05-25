package com.example.streamingservice.util;

/**
 * Central definition of all Redis key prefixes used by the streaming service.
 *
 * Previously these were duplicated as identical string constants in both
 * VideoEncodedEventConsumer and StreamingController. A typo in one would
 * silently break the lookup in the other with no compile error.
 */
public final class RedisKeys {

    private RedisKeys() {} // utility class

    /** Stores the S3 key of the HLS master playlist, keyed by movieId. */
    public static final String MASTER_PLAYLIST_PREFIX = "streaming:playlist:";

    /** Caches the pre-signed streaming URL, keyed by movieId. */
    public static final String STREAMING_URL_PREFIX   = "streaming:url:";

    /** Caches signed M3U8 playlist content, keyed by S3 path. */
    public static final String SIGNED_PLAYLIST_PREFIX = "streaming:signed-playlist:";

    public static String masterPlaylistKey(Long movieId) {
        return MASTER_PLAYLIST_PREFIX + movieId;
    }

    public static String streamingUrlKey(Long movieId) {
        return STREAMING_URL_PREFIX + movieId;
    }

    public static String signedPlaylistKey(String s3Path) {
        return SIGNED_PLAYLIST_PREFIX + s3Path;
    }
}