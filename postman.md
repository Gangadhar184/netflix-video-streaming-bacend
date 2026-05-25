# Postman Guide: How to Test the Project End to End

## Goal

This guide shows how to test every public API in the repository and how to observe the end-to-end flow from movie creation to upload to encoding to playback.

## Services and Base URLs

Create a Postman environment with these variables:

| Variable | Example |
|---|---|
| `content_base_url` | `http://localhost:8081` |
| `video_base_url` | `http://localhost:8082` |
| `stream_base_url` | `http://localhost:8084` |
| `movie_id` | leave blank initially |
| `playlist_path` | leave blank initially |

## Before You Start

You need the following running:

- Kafka and Zookeeper
- PostgreSQL for `contentservice`
- Redis for `streamingservice`
- S3 bucket + valid AWS credentials
- FFmpeg installed for `encodingservice`
- All four Spring Boot services running

If any of these are missing, the full workflow will stop partway through.

## Suggested Postman Collection Structure

1. `Content Service`
2. `Video Service`
3. `Streaming Service`
4. `Negative Tests`

There is no public REST API in `encodingservice`; it is driven by Kafka events.

## Step 1: Create a Movie in Content Service

### Request
- Method: `POST`
- URL: `{{content_base_url}}/api/v1/movies`
- Headers: `Content-Type: application/json`

### Body
```json
{
  "title": "Interstellar",
  "description": "A science-fiction film about space travel and survival.",
  "genre": "SCI_FI",
  "director": "Christopher Nolan",
  "castMembers": "Matthew McConaughey, Anne Hathaway, Jessica Chastain",
  "releaseYear": 2014,
  "rating": 8.6,
  "thumbnailUrl": "https://example.com/interstellar.jpg",
  "durationMinutes": 169
}
```

### What to expect
- HTTP `201 Created`
- Response contains a numeric `id`
- Initial `videoStatus` should be `PENDING`

### Important Postman action
Save the response `id` into your environment variable `movie_id`.

## Step 2: List Movies

### Request
- Method: `GET`
- URL: `{{content_base_url}}/api/v1/movies?page=0&size=10`

### What to expect
- HTTP `200 OK`
- A paginated result containing the movie you created

## Step 3: Get Movie by ID

### Request
- Method: `GET`
- URL: `{{content_base_url}}/api/v1/movies/{{movie_id}}`

### What to expect
- HTTP `200 OK`
- The movie record you created
- `videoStatus` still `PENDING` before upload

## Step 4: Search by Title

### Request
- Method: `GET`
- URL: `{{content_base_url}}/api/v1/movies/search?title=Interstellar&page=0&size=10`

### What to expect
- HTTP `200 OK`
- Matching movie page

## Step 5: Filter by Genre

### Request
- Method: `GET`
- URL: `{{content_base_url}}/api/v1/movies/genre/SCI_FI?page=0&size=10`

### What to expect
- HTTP `200 OK`
- Page of `SCI_FI` movies

## Step 6: Upload the Video File

### Request
- Method: `POST`
- URL: `{{video_base_url}}/api/v1/videos/upload/{{movie_id}}`
- Body type: `form-data`

### Form-data field
| Key | Type | Value |
|---|---|---|
| `file` | File | choose an `.mp4`, `.mkv`, or `.mov` file |

### What to expect
- HTTP `201 Created`
- Response with:
  - `movieId`
  - `videoKey`
  - `status` = `UPLOADED`
  - success message

### What happens internally
1. `videoservice` validates the file.
2. It uploads the raw object to S3.
3. It publishes `video.uploaded` to Kafka.
4. `contentservice` should eventually update the movie to `UPLOADED`.
5. `encodingservice` should begin FFmpeg processing.

## Step 7: Poll Content Service for Processing State

### Request
- Method: `GET`
- URL: `{{content_base_url}}/api/v1/movies/{{movie_id}}`

### Expected state transitions
- `PENDING`
- `UPLOADED`
- `READY` or `FAILED`

### How to use this step
Run the request every few seconds after upload.

### Success path
- `videoStatus` becomes `READY`
- `hlsMasterPlaylistKey` is populated

### Failure path
- `videoStatus` becomes `FAILED`
- `lastEncodingError` may be populated

## Step 8: Request Streaming URL

Only do this after the movie becomes `READY`.

### Request
- Method: `GET`
- URL: `{{stream_base_url}}/api/v1/stream/{{movie_id}}`

### What to expect
- HTTP `200 OK`
- Response fields:
  - `movieId`
  - `streamingUrl`
  - `availableQualities`
  - `expiresInMinutes`

### If you get `404`
That usually means `streamingservice` has not yet cached the playlist key from the `video.encoded` event, or encoding has not completed.

## Step 9: Request a Signed Playlist

This endpoint is used by the HLS player, not usually by a human client, but it is useful for understanding the flow.

### Build the playlist path
The typical value is:

- `encoded/{{movie_id}}/master.m3u8`

Store it in `playlist_path` if you want.

### Request
- Method: `GET`
- URL: `{{stream_base_url}}/api/v1/stream/{{movie_id}}/playlist?path=encoded/{{movie_id}}/master.m3u8`

### What to expect
- HTTP `200 OK`
- Response body is M3U8 text
- Referenced child playlists or segments should be rewritten as signed URLs

## Step 10: Soft Delete the Movie

### Request
- Method: `DELETE`
- URL: `{{content_base_url}}/api/v1/movies/{{movie_id}}`

### What to expect
- HTTP `204 No Content`

### Important note
This is a soft delete in `contentservice`. It does not remove S3 objects or Kafka history.

## Negative Tests You Should Try

## 1. Invalid movie ID on content lookup
- `GET {{content_base_url}}/api/v1/movies/999999`
- Expected: `404 Not Found`

## 2. Invalid genre
- `GET {{content_base_url}}/api/v1/movies/genre/INVALID_GENRE?page=0&size=10`
- Expected: likely `400 Bad Request`

## 3. Empty upload file
- Upload with an empty file
- Expected: `400 Bad Request`

## 4. Unsupported file type
- Upload a `.txt` file
- Expected: `400 Bad Request`

## 5. Streaming before encoding is ready
- `GET {{stream_base_url}}/api/v1/stream/{{movie_id}}` immediately after upload
- Expected: `404 Not Found`

## 6. Invalid playlist path
- `GET {{stream_base_url}}/api/v1/stream/{{movie_id}}/playlist?path=encoded/otherMovie/master.m3u8`
- Expected: failure due to path validation

## Best Way to Understand the Full Flow

If you want to understand the system deeply, use this exact sequence:

1. Create a movie in `contentservice`
2. Fetch it by ID and observe `PENDING`
3. Upload a video in `videoservice`
4. Poll `contentservice` until it moves to `UPLOADED`
5. Keep polling until it becomes `READY` or `FAILED`
6. Call `streamingservice` for the signed streaming URL
7. Call the signed playlist endpoint and inspect the rewritten M3U8 content

That sequence lets you see:

- synchronous metadata creation,
- asynchronous event processing,
- state transitions,
- and final playback delivery.

## Troubleshooting Checklist

### If upload works but status never changes
- Check Kafka broker availability
- Check `contentservice` consumer logs
- Check `encodingservice` consumer logs

### If status becomes `FAILED`
- Check `lastEncodingError` in movie response
- Verify FFmpeg path and OS compatibility
- Verify the uploaded file is a valid media file

### If streaming returns `404`
- Check whether `videoStatus` is `READY`
- Check `streamingservice` logs
- Verify Redis is running

### If signed URL generation fails
- Verify AWS credentials
- Verify bucket name and region
- Verify encoded files actually exist in S3

## Final Note

There are three public API surfaces to test in Postman:

- `contentservice` for catalog lifecycle
- `videoservice` for ingestion
- `streamingservice` for playback access

`encodingservice` is best understood indirectly by watching the state changes and event-driven outcomes produced by the other services.