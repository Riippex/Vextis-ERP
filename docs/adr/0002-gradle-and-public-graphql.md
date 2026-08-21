# ADR 0002 — Gradle and GraphQL for the public API

- Status: Accepted
- Date: 2026-08-20
- Partially supersedes: ADR 0001

## Context

The initial bootstrap assumed Maven for Enterprise Core and OpenAPI/REST for both Angular and internal tools. The team decided to standardize the Java build on Gradle and adopt GraphQL from the start for the Angular experience, where screens combine CRM, inventory, billing, workflows, and audit.

## Decision

- Enterprise Core uses Gradle Wrapper with Kotlin DSL and a Java 21 toolchain.
- Angular consumes an explicit GraphQL API from Enterprise Core at `/graphql`.
- `contracts/graphql/public-api.graphqls` is the source of truth for the public schema.
- Angular generates operations and types from the schema; generated code is not edited by hand.
- Agent Runtime keeps a separate, restricted REST/OpenAPI API for enterprise tools.
- Pub/Sub keeps AsyncAPI and JSON Schema for versioned events.

GraphQL is a transport layer. Resolvers call the same application use cases, authorization, approvals, idempotency, and audit as any other adapter. They contain no business rules and do not access other modules' repositories directly.

## Consequences

- Angular can request screen-shaped graphs without multiplying composition endpoints.
- The GraphQL schema widens the surface that must be governed: depth, complexity, pagination, and field-level authorization will be added before exposing real data.
- Mutations will be use-case specific; there will be no generic table/record mutations.
- Keeping GraphQL public and REST internal avoids giving agents an arbitrary exploration API.
- ADR 0001's references to OpenAPI for all consumers are replaced by public GraphQL + internal OpenAPI.
