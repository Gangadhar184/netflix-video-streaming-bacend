# Configuration Audit Report

## Executive Summary

The repository has a clear service split and mostly understandable runtime configuration, but the configuration story is still **development-oriented rather than production-ready**.

The biggest findings are:

1. `contentservice` contains a source-controlled database secret (redacted here).
2. Local infrastructure is only partially defined in `docker-compose.yml`.
3. Kafka consumer safety is under-specified: no repository-level retry, DLQ, or error-handler configuration is present.
4. AWS, Redis, PostgreSQL, and FFmpeg assumptions are not fully standardized across services.
5. Test configuration is thin and, in at least one module, appears out of sync with the current code shape.

## Audit Scope

Reviewed files:

- `docker-compose.yml`
- `contentservice/contentservice/src/main/resources/application.yaml`
- `videoservice/videoservice/src/main/resources/application.yaml`
- `encodingservice/encodingservice/src/main/resources/application.yaml`
- `streamingservice/streamingservice/src/main/resources/application.yaml`
- Supporting config classes in each service

## Service-by-Service Assessment

| Service | Status | Key Positives | Key Risks |
|---|---|---|---|
| `contentservice` | Needs hardening | Clear DB + Kafka config, actuator enabled | Inline DB secret, `ddl-auto=update`, permissive Kafka JSON trust |
| `videoservice` | Reasonable dev config | Multipart limits, Kafka topics, S3 env placeholders | No auth, no consumer config, S3 credentials mandatory locally |
| `encodingservice` | Functional but environment-sensitive | Explicit FFmpeg path, Kafka producer/consumer config | Hardcoded Windows FFmpeg path, no retry/DLQ config, temp disk assumptions |
| `streamingservice` | Clear cache-focused config | Redis + Kafka + S3 config present, actuator enabled | No auth, permissive trusted packages, Redis not provisioned in compose |

## Root-Level Infrastructure Audit

### `docker-compose.yml`

### What is configured
- Zookeeper
- Kafka

### What is missing for full local end-to-end startup
- PostgreSQL
- Redis
- Any S3-compatible local substitute such as MinIO or LocalStack
- Service containers themselves

### Assessment
The compose file is useful for bootstrapping Kafka locally, but it does **not** represent the full system runtime. New contributors may assume the stack is runnable from compose alone when it is not.

## Detailed Findings by Service

## `contentservice`

### Current config behavior
- Uses PostgreSQL on localhost.
- Uses Hibernate `ddl-auto=update`.
- Uses Kafka consumer settings with JSON deserialization.
- Exposes actuator `health` and `info`.

### Audit findings
- **Secret management risk:** a database password is committed directly in application config. The value is intentionally redacted in this report.
- **Schema management risk:** `ddl-auto=update` is convenient in development but unsafe for production change control.
- **Deserializer looseness:** `spring.json.trusted.packages: "*"` is very broad.
- **Potential type ambiguity:** `spring.json.value.default.type: java.util.HashMap` is unusual when listeners expect typed record payloads.
- **No explicit topic-name properties:** consumers use hardcoded topic names in code instead of central config.

### Recommendations
- Move all DB credentials to environment variables or secret stores.
- Replace `ddl-auto=update` with migrations via Flyway or Liquibase.
- Restrict trusted Kafka packages to known event packages.
- Remove or justify `default.type=HashMap`.
- Externalize topic names into `kafka.topics.*` config.

## `videoservice`

### Current config behavior
- Uses multipart upload limits of 2 GB.
- Publishes JSON events to Kafka.
- Reads AWS credentials and bucket settings from env placeholders.
- Exposes actuator `health` and `info`.

### Audit findings
- **Good:** file-size boundaries exist at both Spring multipart layer and service validation layer.
- **Good:** topic names are externalized.
- **Gap:** no auth-related settings or caller protection.
- **Gap:** no timeout/retry configuration for Kafka publishing or S3 access.
- **Gap:** `S3Presigner` is configured but not currently used by this service.

### Recommendations
- Add request size, client timeout, and retry settings explicitly.
- Add authentication/authorization before exposing upload endpoints.
- Remove unused beans or document why they are kept.
- Add environment-specific profiles for local, dev, and prod.

## `encodingservice`

### Current config behavior
- Consumes and produces Kafka JSON messages.
- Reads S3 credentials from env placeholders.
- Uses a configured FFmpeg binary path.
- Uses a local temp encoding directory.

### Audit findings
- **Portability risk:** FFmpeg path is hardcoded to a Windows path in checked-in config.
- **Disk management risk:** encoding temp directory is local filesystem state with no quota or cleanup policy outside application logic.
- **Reliability gap:** no checked-in retry, DLQ, or error-handler config despite code comments assuming those behaviors.
- **Config drift:** `encoding.ffmpeg.threads` exists in config, but the service currently hardcodes `-threads 2` in command construction.

### Recommendations
- Externalize FFmpeg path per environment/profile.
- Align thread-count config and implementation.
- Add Kafka retry/DLQ/error-handler config explicitly.
- Consider isolated worker containers or job runners instead of long-lived JVM instances for encoding.

## `streamingservice`

### Current config behavior
- Consumes Kafka events.
- Uses Redis for playlist and signed URL cache.
- Uses S3 + S3 presigner.
- Exposes actuator endpoints.

### Audit findings
- **Good:** Redis timeout and presigned URL expiry are explicit.
- **Gap:** no auth or entitlement configuration for streaming access.
- **Gap:** permissive Kafka trusted packages remain broad.
- **Gap:** Redis is required but not provisioned in compose.

### Recommendations
- Add auth and entitlement checks upstream of signed URL issuance.
- Restrict Kafka trusted packages.
- Add Redis deployment definition for local and production docs.
- Consider separating URL cache TTL policy from playlist cache TTL policy in documented config.

## Cross-Cutting Risks

## 1. Secret handling
- One service stores a database secret directly in source-controlled config.
- AWS secrets are correctly modeled as environment placeholders, but there is no documented `.env` strategy.

## 2. Environment parity
- Only Kafka and Zookeeper are defined in compose.
- PostgreSQL, Redis, FFmpeg, and S3 are assumed but not provisioned.

## 3. Event hardening
- Kafka deserialization trust is too permissive.
- No repository-visible dead-letter policy.
- No schema registry or explicit versioning strategy.

## 4. Operational maturity
- No profile matrix (`local`, `dev`, `prod`).
- No centralized config approach.
- No health checks for downstream systems beyond generic actuator defaults.

## Recommended Configuration Roadmap

### Immediate
- Remove inline DB secrets.
- Add README-backed environment variable matrix.
- Add Postgres and Redis to local infrastructure documentation.

### Short term
- Introduce Spring profiles.
- Replace `ddl-auto=update` with migrations.
- Restrict Kafka trusted packages.
- Add explicit retry/backoff/DLQ settings.

### Medium term
- Adopt secret management.
- Add MinIO/LocalStack for local S3-compatible testing.
- Add shared config validation and startup checks.

## Final Verdict

The configuration is **good enough for local development and architectural learning**, but not yet production-grade. The main gap is not conceptual clarity; it is operational hardening.