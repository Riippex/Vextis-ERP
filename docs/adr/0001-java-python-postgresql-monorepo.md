# ADR 0001 — Java + Python + PostgreSQL in a monorepo

- Status: Accepted
- Date: 2026-08-19

## Context

Vextis needs transactional ERP/CRM business rules while also needing speed and first-class support for Google ADK, RAG, and agentive workflows.

## Decision

- Angular in `apps/web`.
- Enterprise Core Java/Spring Boot in `services/enterprise-core`.
- Agent Runtime Python/Google ADK in `services/agent-runtime`.
- Cloud SQL PostgreSQL as the durable source of truth.
- Pub/Sub with a transactional outbox for asynchronous integration.
- Executable contracts in `contracts`.

Enterprise Core is the sole authority for business mutations. Agent Runtime coordinates and calls authenticated tools; it does not write directly to ERP tables.

> The Java build and public-contract choice were updated by ADR 0002: Gradle Kotlin DSL and GraphQL for Angular; OpenAPI remains for internal tools.

## Consequences

- Two deployable backends, not one microservice per department.
- Higher build complexity from two languages, mitigated by generated contracts and root-level automation.
- Clearer domain boundaries and a reasonable path for enterprise scaling.
- Firestore and the historical `web/api/worker/agents/shared` layout are dropped for new code.
