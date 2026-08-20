# ADR 0001 — Java + Python + PostgreSQL en monorepo

- Estado: Accepted
- Fecha: 2026-08-19

## Contexto

Vextis necesita reglas ERP/CRM transaccionales y, al mismo tiempo, rapidez y soporte de primera clase para Google ADK, RAG y workflows agentivos.

## Decisión

- Angular en `apps/web`.
- Enterprise Core Java/Spring Boot en `services/enterprise-core`.
- Agent Runtime Python/Google ADK en `services/agent-runtime`.
- Cloud SQL PostgreSQL como fuente de verdad durable.
- Pub/Sub con transactional outbox para integración asíncrona.
- Contratos ejecutables en `contracts`.

Enterprise Core es la única autoridad para mutaciones empresariales. Agent Runtime coordina y llama herramientas autenticadas; no escribe directamente en tablas del ERP.

> La selección de build Java y del contrato público fue actualizada por ADR 0002: Gradle Kotlin DSL y GraphQL para Angular; OpenAPI permanece para las tools internas.

## Consecuencias

- Dos backends desplegables, no un microservicio por departamento.
- Mayor complejidad de build por dos lenguajes, mitigada por contratos generados y automatización raíz.
- Límites de dominio más claros y una ruta razonable de escalado enterprise.
- Firestore y el layout histórico `web/api/worker/agents/shared` quedan descartados para el código nuevo.
