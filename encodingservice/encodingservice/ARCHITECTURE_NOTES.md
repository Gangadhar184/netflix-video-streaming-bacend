# Encoding Service Architecture Notes

## Why This Service Exists

Encoding is a fundamentally different class of work from CRUD APIs. It is computational, asynchronous, and often the slowest step in the system. That is why it is isolated.

From first principles, if a task:

- takes seconds or minutes,
- depends on native binaries,
- may fail for media-specific reasons,
- and consumes heavy CPU/disk resources,

then it deserves its own processing boundary.

## Why the Current Design Was Chosen

The chosen design is a classic event-driven worker pattern:

- wait for upload event,
- pull source object,
- process it,
- push outputs,
- publish result event.

That is a good fit for transcoding workloads because it keeps request/response services fast.

## Core Architectural Decisions

- Use Kafka as the work queue trigger.
- Use S3 as both source and destination object store.
- Use local temp storage as a working area.
- Use FFmpeg directly via process execution.
- Publish explicit success/failure completion events.

## Tradeoffs Made

### Advantages
- Very understandable pipeline
- Minimal moving parts inside the service
- Clear integration boundaries

### Tradeoffs
- Native process management inside a JVM adds operational complexity.
- Local temp storage can become a bottleneck.
- Failure recovery is event-driven but not fully orchestrated.
- Parallel job scaling is external to the code.

## Scalability Implications

- This service is the primary compute bottleneck in the system.
- Horizontal scaling is possible by running more workers in the same Kafka consumer group.
- Disk, CPU, and temp storage sizing matter more here than in any other module.

## Reliability Considerations

- Job directories isolate concurrent work.
- `finally` cleanup reduces temp-file buildup.
- Failure events prevent silent stalls in downstream business state.

Still missing:

- bounded concurrency control,
- explicit retry policies,
- poison-message handling,
- and durable job progress tracking.

## Failure Scenarios

### S3 download fails
Encoding never starts; a failure event should still be emitted.

### FFmpeg exits non-zero
Movie should eventually be marked `FAILED` in `contentservice`.

### Upload of encoded outputs fails
The job becomes partially successful unless downstream logic reconciles it.

### Temp disk fills up
Worker may fail unpredictably and affect multiple jobs.

## Coupling / Cohesion Analysis

### Cohesion
High cohesion: every class supports the encoding pipeline.

### Coupling
Moderate coupling to:

- S3 key conventions,
- event payload schema,
- FFmpeg availability and CLI behavior.

## Performance Bottlenecks

- FFmpeg CPU utilization
- local disk throughput
- S3 download/upload throughput
- job concurrency contention

## Security Considerations

- FFmpeg path and process execution should be tightly controlled
- downloaded media is untrusted input
- temp files may expose sensitive media if host security is weak
- AWS credentials need standard secret handling

## What Can Be Improved

- use containerized workers with isolated ephemeral volumes,
- make FFmpeg thread count truly configurable,
- add job metrics and structured progress logs,
- support retry-safe partial-job cleanup.

## Possible Refactors

- move command generation into a dedicated encoder strategy class,
- separate manifest generation from transcoding execution,
- add a persistent job table for observability and retries.

## Scalability Enhancements

- autoscaled worker pool,
- queue depth monitoring,
- separate high/low priority topics,
- GPU or hardware-accelerated transcoding where appropriate.

## Observability Improvements

- encode duration by resolution,
- failure-rate dashboards,
- per-job correlation tracing,
- consumer lag monitoring.

## Resiliency Improvements

- DLQ configuration,
- bounded retries,
- idempotent output validation,
- orphaned temp-file scavenger job.

## Modernization Opportunities

- job-runner model on Kubernetes,
- workflow orchestration for large batches,
- shared schema contracts,
- artifact checksums for encoded outputs.

## Technical Debt

- environment-specific FFmpeg path in checked-in config,
- minimal tests,
- no explicit worker lifecycle controls,
- no repository-visible retry/DLQ policy.

## Alternative Architectures

### Alternative 1: Dedicated transcoding platform
Pros: stronger scaling and isolation.
Cons: more infrastructure and higher operational cost.

### Alternative 2: Serverless/media-convert style pipeline
Pros: reduced worker management.
Cons: less control, potentially higher cost, vendor lock-in.

## Bottom Line

`encodingservice` is the right kind of separate service. The core design is sound; the main future work is worker operations, scaling, and recovery discipline.