# ADR 0002 — Gradle y GraphQL para la API pública

- Estado: Accepted
- Fecha: 2026-08-20
- Supera parcialmente: ADR 0001

## Contexto

El bootstrap inicial asumía Maven para Enterprise Core y OpenAPI/REST tanto para Angular como para las herramientas internas. El equipo decidió estandarizar el build Java con Gradle y adoptar GraphQL desde el inicio para la experiencia Angular, donde las pantallas combinan CRM, inventario, facturación, workflows y auditoría.

## Decisión

- Enterprise Core usa Gradle Wrapper con Kotlin DSL y toolchain Java 21.
- Angular consume una API GraphQL explícita del Enterprise Core en `/graphql`.
- `contracts/graphql/public-api.graphqls` es la fuente de verdad del schema público.
- Angular genera operaciones y tipos desde el schema; el código generado no se edita manualmente.
- Agent Runtime conserva una API REST/OpenAPI separada y restringida para tools empresariales.
- Pub/Sub conserva AsyncAPI y JSON Schema para eventos versionados.

GraphQL es una capa de transporte. Los resolvers invocan los mismos casos de uso de aplicación, autorización, aprobaciones, idempotencia y auditoría que cualquier otro adaptador. No contienen reglas de negocio ni acceden directamente a repositorios de otros módulos.

## Consecuencias

- Angular puede solicitar grafos ajustados a cada pantalla sin multiplicar endpoints de composición.
- El schema GraphQL amplía la superficie que debe gobernarse: profundidad, complejidad, paginación y autorización por campo se incorporarán antes de exponer datos reales.
- Las mutaciones serán específicas por caso de uso; no existirán mutaciones genéricas de tablas o registros.
- Mantener GraphQL público y REST interno evita entregar a los agentes una API de exploración arbitraria.
- Las referencias de ADR 0001 a OpenAPI para todos los consumidores quedan reemplazadas por GraphQL público + OpenAPI interno.
