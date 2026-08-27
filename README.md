# Vextis ERP

Vextis is an agentive CRM/ERP platform that coordinates business processes across **CRM and Sales**, **Inventory and Operations**, and **Finance and Billing**.

The product combines the capabilities of all three All Things Agentic Hackathon tracks in a single experience:

- **Collaborative Partner:** understands goals, retrieves context, and asks for clarification.
- **Taskmaster:** executes end-to-end asynchronous business workflows.
- **Fortified Enterprise Fleet:** registers, authorizes, observes, and audits agents.

The official entry category is **Fortified Enterprise Fleet**.

## Official architecture

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

- **Java** is the sole authority for CRM, inventory, and billing mutations.
- **Python** coordinates agents, RAG, memory, and workflows, but does not write directly to business tables.
- **GraphQL SDL, OpenAPI, AsyncAPI, and JSON Schema** are the contracts between Angular, Java, and Python.
- **PostgreSQL** holds transactional data, durable state, audit, outbox, idempotency, and RAG vectors.
- **Cloud Storage** holds documents and original artifacts.

## Monorepo

```text
apps/web/                         Angular
services/enterprise-core/         Java + Spring Boot
services/agent-runtime/           Python + Google ADK
contracts/                        GraphQL, internal OpenAPI, AsyncAPI, and JSON Schema
infra/                            Terraform, deployment, and seed data
docs/                             Architecture, decisions, and coordination
```

## Local development

Requirements: Java 17+ to start Gradle (the wrapper downloads the Java 21 toolchain), Node.js 24, pnpm 11, uv, and Docker Desktop.

```powershell
Copy-Item .env.example .env
./tools/dev.ps1 infra
./tools/dev.ps1 core
./tools/dev.ps1 agents
./tools/dev.ps1 web
```

- Angular: `http://localhost:4200`.
- Public GraphQL: `http://localhost:8080/graphql`.
- Agent Runtime health: `http://localhost:8081/health`.
- Full verification: `./tools/check.ps1`.

Gradle Wrapper and `uv` download their declared runtimes. No global Gradle or Python 3.13 install is required.

## Sources of truth

Public documentation is consulted in this order:

1. [`docs/TECH_STACK.md`](./docs/TECH_STACK.md): technologies and responsibilities of each runtime.
2. [`docs/REPO_STRUCTURE.md`](./docs/REPO_STRUCTURE.md): structure and dependency rules.
3. [`docs/CONTRACTS.md`](./docs/CONTRACTS.md): model, APIs, events, and integration rules.

Execution status, ordered capability bundles, completion gates, and AI handoff
instructions live in [`docs/ROADMAP.md`](./docs/ROADMAP.md).

If two documents contradict each other, the most recently recorded decision in `docs/adr/` resolves the conflict before writing code. No silent assumptions are implemented.

## Status

The MVP runs as an end-to-end vertical slice across Angular, Enterprise Core,
Pub/Sub, and the Agent Runtime. Mission Control exposes the three business
departments, workflow execution state, and a tenant-scoped registry of approved
agents with their model, prompt version, runtime identity, capabilities, and
allowed tools. The registry is descriptive governance metadata; authorization
remains enforced independently by application policy and Google Cloud IAM.

## License

Copyright © 2026 Rafael Patiño Díaz.

Vextis ERP is distributed under the [Apache License 2.0](./LICENSE). See also the attribution file [NOTICE](./NOTICE). Third-party dependencies retain their own licenses.

## Official reference

- Hackathon: https://allthingsagentichackathon.devpost.com/
- Video: https://www.youtube.com/watch?v=5Xw3LtPeByE
