# Video Service Architecture Notes

## Why This Service Exists

Uploading large binary payloads is fundamentally different from storing catalog metadata. It involves bandwidth, streaming I/O, file validation, object storage concerns, and failure patterns that do not belong in a metadata service.

That is the first-principles reason for `videoservice`.

## Why the Current Design Was Chosen

The design chooses the simplest useful ingestion boundary:

- one HTTP upload endpoint,
- direct S3 persistence,
- one Kafka event as the handoff to async processing.

This keeps upload latency low and avoids blocking the caller on encoding work.

## Core Architectural Decisions

- Use REST for ingestion.
- Store raw binaries directly in S3.
- Publish an integration event after upload.
- Keep the service stateless with no database.
- Validate aggressively before consuming S3/Kafka resources.

## Tradeoffs Made

### Good tradeoffs
- Simple service model
- Easy to scale horizontally
- Clear handoff into asynchronous pipeline

### Costs
- No persistent ingestion audit trail in the service itself
- Upload success and downstream processing success are separate concerns
- If Kafka publication fails after S3 upload, partial success is possible

## Scalability Implications

- Horizontal scaling is straightforward because the service is mostly stateless.
- The real bottlenecks are network throughput and object-store bandwidth, not CPU.
- Large-file uploads may still require ingress tuning, reverse-proxy tuning, and request timeout tuning in real deployments.

## Reliability Considerations

- Multipart guardrails exist.
- S3 object keys are unique and collision-resistant.
- Kafka send callbacks at least surface publication failures in logs.

But there is still a reliability gap:

- there is no outbox pattern,
- no guaranteed exactly-once handoff,
- and no recovery workflow for “uploaded to S3 but event publish failed.”

## Failure Scenarios

### Upload stream fails mid-request
Client gets an error; nothing durable should be assumed.

### S3 upload succeeds but Kafka publish fails
Raw object exists, but downstream encoding may never start.

### Unsupported file type is uploaded
Request is rejected before storage cost is incurred.

### Client retries same file
Unique S3 keys prevent overwrite collisions, but duplicate media objects may accumulate.

## Coupling / Cohesion Analysis

### Cohesion
High cohesion: everything here is about media ingestion.

### Coupling
Moderate coupling to:

- S3 storage conventions (`raw/{movieId}/...`),
- event schema for `video.uploaded`,
- and downstream assumptions in `encodingservice`.

## Performance Bottlenecks

- network ingress bandwidth,
- S3 upload latency,
- memory pressure if multipart tuning is poor upstream,
- request concurrency under high upload volume.

## Security Considerations

- No authenticated upload boundary
- No MIME-sniffing beyond declared content type
- No malware scanning
- No request throttling
- Object metadata includes correlation and movie identifiers, which is fine operationally but should still be handled intentionally

## What Can Be Improved

- resumable uploads,
- multipart direct-to-S3 presigned upload flow,
- stronger content validation via ffprobe or media inspection,
- persistence of upload audit records,
- retry-safe event publication strategy.

## Possible Refactors

- Move to a direct browser-to-S3 upload model with backend-issued upload tokens.
- Add an upload-session abstraction if files become larger or upload interruptions matter.
- Share event contract definitions across services.

## Scalability Enhancements

- direct-to-S3 uploads,
- CDN or edge upload endpoints,
- upload queue metrics,
- autoscaling based on ingress traffic.

## Observability Improvements

- record upload duration percentiles,
- measure S3 failure rates,
- add correlation IDs to response payloads,
- log request size and outcome in structured form.

## Resiliency Improvements

- outbox or transactional event relay,
- idempotency keys,
- retry policy around S3 transient failures,
- compensation path for orphaned S3 objects.

## Modernization Opportunities

- presigned multipart upload,
- zero-copy streaming where possible,
- content scanning pipeline integration,
- shared platform auth gateway.

## Technical Debt

- no public API contract documentation in-code,
- unused presigner bean,
- stale tests,
- missing auth and rate controls.

## Alternative Architectures

### Alternative 1: Direct client-to-S3 upload
Pros: removes server bandwidth burden.
Cons: more complex client workflow and security token management.

### Alternative 2: Dedicated ingestion gateway
Pros: centralizes upload policy across domains.
Cons: may over-centralize before the platform grows enough to need it.

## Bottom Line

`videoservice` is architecturally sensible. It is thin by design, which is good. Its biggest future challenge is not feature sprawl; it is making ingestion more reliable, secure, and operationally visible.