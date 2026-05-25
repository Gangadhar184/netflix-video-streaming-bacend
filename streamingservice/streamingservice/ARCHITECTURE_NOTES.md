# Streaming Service Architecture Notes

## Why This Service Exists

Playback delivery has very different requirements from metadata, upload, or encoding:

- low-latency reads,
- short-lived access control,
- cache friendliness,
- and playlist rewriting for HLS clients.

That is why `streamingservice` exists as its own boundary.

## Why the Current Design Was Chosen

The design uses a lightweight pattern:

- listen for `video.encoded`,
- remember the master playlist key in Redis,
- issue pre-signed URLs at request time.

This keeps the service simple while preserving bucket privacy.

## Core Architectural Decisions

- Use Redis as a fast state/cache layer.
- Use S3 pre-signed URLs rather than public buckets.
- Cache both signed URLs and rewritten playlists briefly.
- Validate playlist paths to prevent cross-object access.

## Tradeoffs Made

### Benefits
- fast playback URL generation,
- low storage complexity,
- clear separation of delivery concerns.

### Costs
- extra dependency on Redis,
- temporary inconsistency if cache is stale,
- controller/service boundary is not perfectly clean today.

## Scalability Implications

- This service should scale well horizontally.
- Redis and S3 become the main scaling dependencies.
- Playlist rewriting can become CPU/string-processing work under very high player traffic, but caching reduces that pressure.

## Reliability Considerations

- URL caches expire automatically.
- playlist-key cache has TTL to avoid immortal stale state.
- cache invalidation happens on re-encode success.

Still, failure handling is not complete:

- no fallback if Redis is unavailable,
- no dedicated exception mapping for invalid playlist path requests,
- no entitlement-aware revocation model.

## Failure Scenarios

### Redis unavailable
Playback lookup may fail even if encoded assets exist.

### Encoded event never arrives
Movie is ready in S3 but undiscoverable by streaming API.

### Playlist rewriting fails due to S3 read issues
Client cannot fetch playable M3U8 despite the object existing.

### Signed URL expires during slow client behavior
Playback may fail until the player refreshes via the API.

## Coupling / Cohesion Analysis

### Cohesion
High cohesion: all code relates to playback access and cache handling.

### Coupling
Moderate coupling to:

- encoding output path conventions,
- Redis key design,
- and event schema from `encodingservice`.

## Performance Bottlenecks

- Redis availability and latency
- S3 presigning throughput under heavy demand
- repeated playlist rewriting if cache TTLs are too short

## Security Considerations

- no caller authentication before issuing signed URLs,
- signed URLs are bearer-style access tokens until expiry,
- path validation is good but should be complemented by stronger API auth.

## What Can Be Improved

- move Redis lookup fully into the service layer,
- add proper exception handlers,
- make quality metadata dynamic rather than hardcoded,
- add entitlement checks before issuing playback access.

## Possible Refactors

- separate cache orchestration from S3 playlist rewriting,
- add a small playback policy service or entitlement adapter,
- share playlist/key conventions through a common library.

## Scalability Enhancements

- Redis clustering,
- edge caching/CDN for playlist responses,
- regional playback services close to users.

## Observability Improvements

- cache hit ratio metrics,
- presign latency metrics,
- streaming request success/failure dashboards,
- correlation from encode event to first playback request.

## Resiliency Improvements

- degraded mode behavior if Redis is unavailable,
- better exception taxonomy,
- replay of `video.encoded` events if cache state is lost.

## Modernization Opportunities

- CDN-backed HLS delivery,
- signed cookies or tokenized gateway access,
- OTel instrumentation,
- richer playback analytics.

## Technical Debt

- controller still touching Redis directly,
- minimal tests,
- hardcoded quality list in responses,
- no auth boundary.

## Alternative Architectures

### Alternative 1: Direct CDN tokenization
Pros: offloads most playback traffic.
Cons: more infrastructure complexity and edge-security design.

### Alternative 2: Playlist manifest gateway only
Pros: centralizes all manifest rewriting and authorization.
Cons: can become a high-traffic hotspot.

## Bottom Line

`streamingservice` is a good final-mile adapter for private media playback. The biggest next step is security and delivery hardening, not fundamental redesign.