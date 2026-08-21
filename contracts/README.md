# Contracts

Executable contracts between Angular, Enterprise Core, and Agent Runtime.

```text
graphql/public-api.graphqls     Angular -> Enterprise Core
openapi/agent-tools-api.yaml     Agent Runtime -> Enterprise Core
events/asyncapi.yaml             Pub/Sub events
events/schemas/*.json            Versioned payloads
```

Angular generates operations and types from GraphQL; Python generates its tools client from OpenAPI. Schemas and examples are validated in CI. No implementation is shared between languages.
