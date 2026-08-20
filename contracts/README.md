# Contracts

Contratos ejecutables entre Angular, Enterprise Core y Agent Runtime.

```text
openapi/public-api.yaml          Angular -> Enterprise Core
openapi/agent-tools-api.yaml     Agent Runtime -> Enterprise Core
events/asyncapi.yaml             Eventos Pub/Sub
events/schemas/*.json            Payloads versionados
```

Los clientes TypeScript y Python se generan desde OpenAPI. Los schemas y ejemplos se validan en CI. No se comparte implementación entre lenguajes.

