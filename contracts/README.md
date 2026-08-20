# Contracts

Contratos ejecutables entre Angular, Enterprise Core y Agent Runtime.

```text
graphql/public-api.graphqls     Angular -> Enterprise Core
openapi/agent-tools-api.yaml     Agent Runtime -> Enterprise Core
events/asyncapi.yaml             Eventos Pub/Sub
events/schemas/*.json            Payloads versionados
```

Angular genera operaciones y tipos desde GraphQL; Python genera su cliente de tools desde OpenAPI. Los schemas y ejemplos se validan en CI. No se comparte implementación entre lenguajes.
