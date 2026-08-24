# Vextis — Technology Stack Decision

## Decision

Vextis will use a hybrid architecture with only two deployable backends:

1. **Enterprise Core in Java:** rules and transactions for CRM/Sales, Inventory/Operations, and Finance/Billing.
2. **Agent Runtime in Python:** multi-agent coordination, Gemini, ADK, RAG, memory, and evaluations.

The frontend will be Angular and the execution/data platform will be Google Cloud.

This split is not "microservices for fashion's sake." There is a concrete boundary: the model decides and coordinates in Python; the Java core validates and executes all business mutations.

## Definitive stack

### Frontend

- Stable Angular with strict TypeScript.
- Angular Material for the visual system.
- Signals/RxJS for local state and async flows.
- Apollo Angular and GraphQL Code Generator for operations and types from the public schema.
- Server-Sent Events for the execution timeline; polling as a fallback.
- Firebase Hosting or Cloud Run for web deployment.
- Identity Platform/Firebase Authentication for demo login.

### Enterprise Core

- Java 21 LTS.
- Stable Spring Boot.
- Gradle Wrapper with Kotlin DSL.
- Spring for GraphQL for the public API consumed by Angular.
- Spring Modulith for boundaries and events between modules.
- Hexagonal/modular monolith architecture.
- Spring Data JPA/Hibernate.
- Flyway for migrations.
- Bean Validation.
- PostgreSQL.
- Testcontainers, JUnit 5, and ArchUnit.

Internal modules:

```text
core/
├── crm-sales/
├── inventory-operations/
├── finance-billing/
├── workflow-audit/
└── shared-kernel/
```

The core is the sole authority that can create orders, reserve inventory, approve discounts, consume credit, or issue invoices. Agents do not write directly to its tables.

### Agent Runtime

- Python 3.13.
- Google ADK 2.x.
- Google GenAI SDK.
- Vertex AI for Gemini 3.5+ and embeddings.
- FastAPI only for callbacks, health checks, and adapters not covered by Agent Engine.
- Pydantic for structured contracts.
- Vertex AI Agent Engine Runtime and Memory Bank, if the region/account are available.
- Cloud Run as a deployment alternative and for auxiliary workers.
- Pytest and ADK evaluations.

Agents:

```text
Coordinator Agent
├── CRM Agent
├── Inventory Agent
└── Billing Agent
```

Each agent calls tools backed by the Java core's internal API. It does not access PostgreSQL directly.

### Optional multimodal capabilities

- **Imagen 4 on Vertex AI:** generates a visual asset for an approved quote or proposal. Agent Runtime builds the request, Cloud Storage holds the file, and Enterprise Core records the relationship with the quote.
- **Veo 3 Fast on Vertex AI:** asynchronously generates a short commercial video on explicit request. It is a CRM/Sales tool and reuses the proposal-assets pipeline; it does not introduce a Marketing module.
- **Gemini Live API:** enables low-latency bidirectional conversation from Angular. The session is transported through an Agent Runtime adapter and translates the conversation into the same existing commands and tools.
- **Standard multimodal Gemini:** processes audio files or non-interactive messages; a Live session is not opened for batch tasks.

Live Audio does not create a second set of use cases. Text, uploaded audio, and Live conversation converge on the same contracts, policies, approvals, and idempotency. Image, Veo, and Live are controlled with feature flags and can be disabled without affecting the ERP.

### Data and RAG

- Cloud SQL for PostgreSQL as the transactional source.
- `pgvector` for the first RAG.
- Vertex AI `gemini-embedding` for embeddings.
- Cloud Storage for PDFs, generated images, invoices, allowed temporary audio, and original documents.
- PostgreSQL for metadata, chunks, ACL, hashes, versions, and customer relationships.
- Memory Bank for preferences and long-term agentive memory; not for balances, inventory, or accounting data.

Cloud SQL + pgvector avoids introducing an additional vector database during the hackathon. If volume or latency later justify it, the natural evolution is AlloyDB AI or Vertex AI Search.

### Events and workflows

- Pub/Sub for events between the core and the agentive runtime.
- Transactional outbox pattern in Java so events aren't lost after a commit.
- Idempotency keys in PostgreSQL.
- Cloud Tasks for deferred retries or scheduled callbacks, only if the need arises.
- Durable state in PostgreSQL; Pub/Sub and Redis are never the source of truth.

Main events:

```text
purchase_order.received
quote.approved
quote.visual.generated
quote.video.generated
sales_order.created
inventory.reservation.requested
inventory.exception.detected
human_approval.received
invoice.issued
workflow.completed
workflow.failed
live.session.started
live.session.ended
```

### Security

- IAM and one service account per service.
- Service-to-service authentication between Agent Runtime and Enterprise Core.
- Secret Manager for secrets.
- Model Armor before sending untrusted documents or prompts to the model.
- Tool and approval policies live in the core, not only in prompts.
- Cloud Audit Logs and a dedicated functional audit trail.
- Private data and Cloud SQL with no public exposure in a production configuration.

### Observability

- OpenTelemetry.
- Cloud Logging, Monitoring, Trace, and Error Reporting.
- Correlation ID shared across frontend, coordinator, agents, Pub/Sub, and core.
- Business metrics: time per workflow, autonomous steps, approvals, failures, retries, and estimated savings.

### Delivery

- Monorepo.
- Docker per application.
- Artifact Registry.
- Cloud Build for CI/CD.
- Terraform for minimal reproducible infrastructure.
- Local setup with Docker Compose for PostgreSQL and emulators when available.

Structure:

```text
vextis/
├── apps/
│   └── web/
├── services/
│   ├── enterprise-core/
│   └── agent-runtime/
├── packages/
│   └── api-contracts/
├── infra/
│   └── terraform/
├── docs/
└── compose.yaml
```

## Execution diagram

```text
Angular Web
    |
    | GraphQL/HTTPS + SSE
    v
Enterprise Core — Java/Spring Boot — Cloud Run
    |                     |
    | PostgreSQL          | Outbox events
    v                     v
Cloud SQL + pgvector    Pub/Sub
                          |
                          v
              Agent Runtime — Python/ADK
                 |        |         |
                 |        |         +-> Memory Bank
                 |        +------------> Gemini / Vertex AI
                 +---------------------> Model Armor
                          |
                          | authenticated tools
                          v
                  Enterprise Core API

Cloud Storage holds documents and artifacts.
OpenTelemetry connects all tracing.
```

## Why not pick a single technology

### Java only

Viable, and ADK Java exists, but Python currently has the more complete path for ADK 2, graph workflows, RAG, examples, evaluations, and new agent capabilities. All-Java reduces operational surface but increases agentive experimentation time.

### .NET only

.NET is excellent for enterprise software and Cloud Run supports it. A Google GenAI SDK for C# also exists. However, the current Google ADK ecosystem and the hackathon guides are stronger in Python, Java, and Go. It doesn't offer a decisive advantage over Java within a Google-centered hackathon.

### Django/Python only

The fastest path, and it can scale horizontally. However, mixing the agent's probabilistic runtime with the ERP's rules and transactions reduces boundary clarity and demands more discipline to keep a large domain maintainable. Django Admin can be useful for internal tools, but doesn't justify making it the product's core.

### Go

Go would be excellent for efficient services, but doesn't offer a decisive advantage for this scope over Java in business modeling, nor over Python in AI build speed.

## Hackathon rule

The target architecture is Java + Python, but delivery is protected by a rule:

> If by the end of **August 21** there is no deployed vertical slice spanning Angular, core, Pub/Sub, and an agent, the business tools will be temporarily implemented inside the Python service, keeping the GraphQL/OpenAPI contracts and module boundaries. The physical separation into Java will resume after delivery.
>
> This date depends on the Google Cloud credits already being active — if by August 19 they still haven't been requested, push this checkpoint's date by the same number of days the approval was delayed, rather than keeping it fixed on the calendar.

This preserves the vision without sacrificing a working demo.

## Use of Claude

Claude can continue to be a development assistant for design, code generation, tests, and review. It does not drive the production stack.

At runtime, Gemini must be the primary, visible model because it is a competition requirement. If Claude is integrated as a secondary model, there must be a measurable reason — for example, cross-evaluation — and it must not hide or dilute the use of Gemini and Google ADK.

## What is not adopted initially

- Kubernetes/GKE.
- Kafka.
- Service mesh.
- One database per module.
- Elasticsearch.
- Redis/Memorystore without a demonstrated bottleneck.
- Microfrontends.
- Full event sourcing.

These technologies may be valid later, but they don't address a current MVP risk.

## Enterprise evolution

1. **Hackathon:** Cloud Run, Cloud SQL, Pub/Sub, Storage, Agent Engine/Memory Bank, and a single environment.
2. **First customers:** high availability, backups, replicas, Memorystore if its need is measured, separate environments, and hardened IAM policies.
3. **Scale:** AlloyDB/Spanner based on real patterns, Vertex AI Search for large corpora, module partitioning under independent pressure, and GKE only if Cloud Run stops fitting.

Scalability will be preserved through contracts, idempotency, observability, and domain boundaries. It does not depend on starting with microservices or Kubernetes.
