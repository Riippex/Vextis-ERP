# Vextis ERP

Vextis es una plataforma CRM/ERP agentiva para coordinar procesos empresariales entre **CRM y Ventas**, **Inventario y Operaciones** y **Finanzas y Facturación**.

El producto combina las capacidades de los tres tracks de All Things Agentic Hackathon en una sola experiencia:

- **Collaborative Partner:** entiende objetivos, recupera contexto y solicita aclaraciones.
- **Taskmaster:** ejecuta workflows empresariales asíncronos de principio a fin.
- **Fortified Enterprise Fleet:** registra, autoriza, observa y audita los agentes.

La categoría oficial de inscripción es **Fortified Enterprise Fleet**.

## Arquitectura oficial

```text
Angular Web
    |
    v
Enterprise Core — Java 21 / Spring Boot — Cloud Run
    |                         |
    |                         +-> Transactional Outbox -> Pub/Sub
    v                                                  |
Cloud SQL PostgreSQL + pgvector                        v
                                             Agent Runtime — Python / Google ADK
                                                |        |        |
                                                |        |        +-> Memory Bank
                                                |        +----------> Gemini / Vertex AI
                                                +-------------------> Model Armor
```

- **Java** es la única autoridad para mutaciones de CRM, inventario y facturación.
- **Python** coordina agentes, RAG, memoria y workflows, pero no escribe directamente en las tablas empresariales.
- **GraphQL SDL, OpenAPI, AsyncAPI y JSON Schema** son los contratos entre Angular, Java y Python.
- **PostgreSQL** conserva datos transaccionales, estados durables, auditoría, outbox, idempotencia y vectores del RAG.
- **Cloud Storage** conserva documentos y artefactos originales.

## Monorepo

```text
apps/web/                         Angular
services/enterprise-core/         Java + Spring Boot
services/agent-runtime/           Python + Google ADK
contracts/                        GraphQL, OpenAPI interno, AsyncAPI y JSON Schema
infra/                            Terraform, despliegue y datos semilla
docs/                             Arquitectura, decisiones y coordinación
```

## Desarrollo local

Requisitos: Java 17+ para iniciar Gradle (el wrapper descarga el toolchain Java 21), Node.js 24, pnpm 11, uv y Docker Desktop.

```powershell
Copy-Item .env.example .env
./tools/dev.ps1 infra
./tools/dev.ps1 core
./tools/dev.ps1 agents
./tools/dev.ps1 web
```

- Angular: `http://localhost:4200`.
- GraphQL público: `http://localhost:8080/graphql`.
- Health del Agent Runtime: `http://localhost:8081/health`.
- Verificación completa: `./tools/check.ps1`.

Gradle Wrapper y `uv` descargan sus runtimes declarados. No se requiere una instalación global de Gradle ni Python 3.13.

## Fuentes de verdad

La documentación pública se consulta en este orden:

1. [`docs/TECH_STACK.md`](./docs/TECH_STACK.md): tecnologías y responsabilidades de cada runtime.
2. [`docs/REPO_STRUCTURE.md`](./docs/REPO_STRUCTURE.md): estructura y reglas de dependencia.
3. [`docs/CONTRACTS.md`](./docs/CONTRACTS.md): modelo, APIs, eventos y reglas de integración.

Si dos documentos se contradicen, la decisión más reciente registrada en `docs/adr/` debe resolver el conflicto antes de escribir código. No se implementan supuestos silenciosos.

## Estado

El repositorio está en fase de arquitectura y scaffolding. El objetivo inmediato es un flujo vertical desplegado que atraviese Angular, Enterprise Core, Pub/Sub y Agent Runtime antes de ampliar funcionalidades.

## Referencia oficial

- Hackathon: https://allthingsagentichackathon.devpost.com/
- Video: https://www.youtube.com/watch?v=5Xw3LtPeByE
