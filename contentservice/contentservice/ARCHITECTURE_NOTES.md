# Content Service Architecture Notes

## Why This Service Exists

From first principles, a media platform needs a durable business record for each piece of content. Raw video files alone are not enough. Someone needs to answer:

- what the movie is,
- whether it is visible in the catalog,
- whether media has been uploaded,
- whether encoding succeeded,
- whether playback is ready.

That is why `contentservice` exists.

## Why the Current Design Was Chosen

The current design combines two concerns that fit together naturally:

1. **catalog metadata ownership**, and
2. **workflow state ownership**.

This is a practical choice because the same business object (`Movie`) is the thing users browse and the thing operators need to track through the processing pipeline.

## Core Architectural Decisions

- Use PostgreSQL as the durable source of truth.
- Use REST for synchronous catalog actions.
- Use Kafka consumers for async pipeline updates.
- Persist operational state directly on the `Movie` entity.
- Use soft delete instead of destructive delete.

## Tradeoffs Made

### Benefits
- Very simple read model for clients
- Easy debugging because all state is on one record
- Minimal orchestration complexity

### Costs
- The `Movie` entity mixes business metadata and pipeline metadata.
- State transitions are distributed across event timing rather than transactions.
- There is no separate audit trail for workflow changes.

## Scalability Implications

- Reads scale reasonably well with pagination and database indexes.
- Writes are modest compared to media transfer and encoding.
- PostgreSQL becomes central as the catalog grows.
- If traffic increases sharply, search and browse queries may need read replicas or dedicated search infrastructure.

## Reliability Considerations

- `@Version` helps with concurrent updates.
- Soft deletes reduce accidental destructive mistakes.
- Event handlers are reasonably idempotent for duplicate upload/ready events.

What is still missing:

- explicit retry/backoff policy documentation,
- replay/reconciliation jobs,
- and stronger event contract guarantees.

## Failure Scenarios

### Upload event never arrives
Movie remains `PENDING` even though an object may exist elsewhere.

### Encoding success event never arrives
Movie remains `UPLOADED` or `ENCODING` forever.

### Duplicate events arrive
Current code handles some duplicate paths defensively, which is good.

### Database unavailable
The service loses all meaningful function because it is the metadata authority.

## Coupling / Cohesion Analysis

### Cohesion
High cohesion overall: metadata and lifecycle state belong to the same business object.

### Coupling
Moderate coupling to event payload shapes from other services. Even though there is no shared database coupling, there is still schema coupling through Kafka messages.

## Performance Bottlenecks

- PostgreSQL query performance for larger catalogs
- synchronous request latency during create/list/search operations
- event consumer lag under high workflow volume

## Security Considerations

- No authentication on mutating endpoints
- No authorization around delete operations
- Database credential handling needs hardening
- Broad Kafka deserialization trust should be reduced

## What Can Be Improved

- Add Flyway/Liquibase migrations
- Add contract tests against event schemas
- Add audit/event history table
- Externalize topic names
- Add authentication and role-based controls

## Possible Refactors

- Separate catalog metadata from pipeline status into two aggregates if the domain grows
- Introduce dedicated read models for browse/search use cases
- Add an outbox pattern if this service starts producing events later

## Scalability Enhancements

- read replicas,
- better indexing,
- search offload to Elasticsearch/OpenSearch,
- cache popular catalog reads.

## Observability Improvements

- structured logs,
- metrics for state transitions,
- consumer lag dashboards,
- trace correlation from API request to Kafka event handling.

## Resiliency Improvements

- dead-letter topics,
- replay tooling,
- reconciliation job for stuck movies,
- circuit-breaker patterns around downstream dependencies if added later.

## Modernization Opportunities

- shared event-contract library,
- OpenTelemetry,
- containerized deployment,
- profile-based configuration.

## Technical Debt

- minimal test coverage,
- source-controlled secret,
- development-style schema management,
- hardcoded topic usage in event listeners.

## Alternative Architectures

### Alternative 1: Separate workflow-state service
Pros: cleaner bounded context separation.
Cons: more joins across services for common queries.

### Alternative 2: Event-sourced catalog state
Pros: perfect history.
Cons: much higher complexity than the current system likely needs.

## Bottom Line

`contentservice` is a sensible anchor service. It is the place where business truth lives. The main next step is not re-architecture; it is operational hardening and stronger lifecycle observability.