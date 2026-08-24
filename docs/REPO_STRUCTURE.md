# Vextis — Monorepo Structure

## Decision

Vextis will live in a monorepo with three deployable applications and shared contracts:

```text
vextis-erp/
├── apps/
│   └── web/                         # Angular
├── services/
│   ├── enterprise-core/             # Java + Spring Boot
│   └── agent-runtime/               # Python + Google ADK
├── contracts/                       # GraphQL, internal OpenAPI, events, and schemas
├── infra/                           # Google Cloud and local infrastructure
├── docs/                            # Architecture, ADRs, and demo
├── tools/                           # Development automation
├── compose.yaml
├── .env.example
├── README.md
└── LICENSE
```

No generic `backend/` folder is used: there are two backends with different responsibilities and deployment cycles.

## Main dependency rule

```text
Angular ────────> Enterprise Core <──────── Agent Runtime
                         |
                         v
                    PostgreSQL

Enterprise Core ──events──> Pub/Sub ──> Agent Runtime
```

- Angular never queries PostgreSQL nor calls Gemini directly.
- Agent Runtime never writes directly to ERP tables.
- Enterprise Core never imports Python code nor depends on prompts.
- All three applications depend on `contracts/`, not on each other at the code level.
- Communication happens through authenticated APIs and versioned events.

## 1. Angular Web

```text
apps/web/
├── src/app/
│   ├── core/                        # Auth, layout, interceptors, configuration
│   ├── shared/                      # Reusable visual components
│   ├── features/
│   │   ├── dashboard/
│   │   ├── crm-sales/
│   │   ├── inventory-operations/
│   │   ├── finance-billing/
│   │   ├── agent-mission-control/
│   │   └── approvals/
│   └── api/                         # Operations and types generated from GraphQL
├── public/
├── Dockerfile
├── package.json
└── angular.json
```

### Rules

- Organize by feature, not by generic technical type (`components/`, `services/`, etc.).
- `shared/` contains only genuinely shared elements with no business rules.
- `api/` is generated code; it is not edited by hand.
- Mission Control is cross-cutting, but consumes data from Enterprise Core.
- The frontend does not decide permissions or financial rules; it only reflects capabilities delivered by the API.

## 2. Enterprise Core

```text
services/enterprise-core/
├── src/main/java/com/vextis/
│   ├── crm/
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── api/
│   ├── inventory/
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── api/
│   ├── billing/
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── api/
│   ├── workflow/
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── api/
│   ├── identity/
│   ├── audit/
│   └── shared/
├── src/main/resources/
│   ├── db/migration/                # Flyway
│   └── application.yml
├── src/test/
├── Dockerfile
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
└── README.md
```

### What each layer means

- `domain/`: entities, value objects, rules, and domain interfaces; no Spring, JPA, or Google Cloud.
- `application/`: use cases and transactional coordination.
- `infrastructure/`: JPA, Pub/Sub, Storage, external clients, and adapters.
- `api/`: public GraphQL adapters, internal REST, DTOs, and mappers.

### Rules

- CRM, Inventory, and Billing are modules, not initial microservices.
- A module does not query another module's internal tables.
- Internal integration happens through public use cases or domain events.
- `shared/` only contains technical primitives or truly universal concepts; it is not a dumping ground.
- Every agent-initiated mutation goes through the same use cases and validations as a human-initiated mutation.
- The transactional outbox lives here because the core owns business transactions.

## 3. Agent Runtime

```text
services/agent-runtime/
├── src/vextis_agents/
│   ├── app/                         # Configuration and entrypoints
│   ├── coordinator/                 # Fleet routing
│   ├── agents/
│   │   ├── crm/
│   │   ├── inventory/
│   │   └── billing/
│   ├── workflows/
│   │   └── order_to_cash/
│   ├── tools/
│   │   ├── core_api/                # Tools that call Java
│   │   ├── documents/
│   │   └── approvals/
│   ├── rag/
│   │   ├── ingestion/
│   │   ├── retrieval/
│   │   └── embeddings/
│   ├── memory/
│   ├── policies/
│   ├── observability/
│   └── generated/                   # Generated OpenAPI client
├── tests/
│   ├── unit/
│   ├── integration/
│   └── evals/                       # Agent evaluations
├── pyproject.toml
├── Dockerfile
└── README.md
```

### Rules

- Prompts stay close to the agent or workflow that uses them and are versioned.
- Tools are small adapters; they contain no inventory, credit, or billing rules.
- Agent outputs use Pydantic models, not free-form dicts.
- `rag/` retrieves evidence; it does not decide business actions.
- `memory/` stores preferences and context, never balances or stock.
- Evals are part of the product and run in CI.

## 4. Contracts

```text
contracts/
├── graphql/
│   └── public-api.graphqls          # Angular -> Enterprise Core
├── openapi/
│   └── agent-tools-api.yaml         # Agent Runtime -> Enterprise Core
├── events/
│   ├── asyncapi.yaml
│   └── schemas/
│       ├── purchase-order-received.v1.json
│       ├── inventory-exception.v1.json
│       ├── approval-received.v1.json
│       └── workflow-completed.v1.json
└── examples/
```

### Rules

- GraphQL SDL, OpenAPI, and JSON Schema are the sources of truth for integration.
- TypeScript operations/types and Python clients are generated; no binary library is shared between languages.
- Every event carries `eventId`, `eventType`, `version`, `occurredAt`, `correlationId`, `causationId`, `tenantId`, and `payload`.
- Published contracts are backward compatible or receive a new version.
- Valid payload examples are tested in CI.

## 5. Infrastructure

```text
infra/
├── terraform/
│   ├── modules/
│   │   ├── cloud-run/
│   │   ├── cloud-sql/
│   │   ├── pubsub/
│   │   ├── storage/
│   │   └── iam/
│   └── environments/
│       ├── hackathon/
│       └── production/              # Prepared, not necessarily deployed
├── docker/
└── seed/
```

### Rules

- One small, reproducible `hackathon` environment.
- Expensive or optional resources are controlled with flags.
- A separate service account for web/core and for agent runtime.
- Secrets are referenced from Secret Manager and never stored in versioned `.env` files.
- Seed data belongs in `infra/seed/`; schema migrations belong to the core.

## 6. Documentation

```text
docs/
├── architecture/
│   ├── system-context.md
│   ├── containers.md
│   ├── components.md
│   └── diagrams/
├── adr/
│   ├── 0001-monorepo.md
│   ├── 0002-java-python-boundary.md
│   └── 0003-postgres-pgvector.md
├── demo/
│   ├── script.md
│   └── test-scenarios.md
└── runbooks/
```

ADRs record decisions and consequences; they do not repeat installation tutorials.

## 7. Root automation

Root commands must hide the differences between Gradle, pnpm, and Python:

```text
tools/
├── dev.ps1
├── test.ps1
├── generate-contracts.ps1
├── seed.ps1
└── deploy.ps1
```

Conceptual commands:

- `dev`: brings up PostgreSQL and the three applications.
- `test`: runs Java, Angular, Python, contracts, and evals.
- `generate-contracts`: regenerates the Angular and Python clients.
- `seed`: creates reproducible demo scenarios.
- `deploy`: builds and publishes the services to Google Cloud.

Equivalent `.sh` scripts may also exist, but logic must not diverge between them.

## 8. CI/CD by change

```text
Change in apps/web/**
  -> lint + unit tests + Angular build

Change in services/enterprise-core/**
  -> unit + architecture + integration tests + container build

Change in services/agent-runtime/**
  -> lint + type check + unit + evals + container build

Change in contracts/**
  -> validate schemas + regenerate clients + test all consumers

Change in infra/**
  -> terraform fmt + validate + plan
```

The monorepo does not force a full rebuild on every change; pipelines use path filters.

## 9. Data ownership

Although initially a single PostgreSQL instance exists, each module owns its tables:

```text
crm_*          -> CRM/Sales
inventory_*    -> Inventory/Operations
billing_*      -> Finance/Billing
workflow_*     -> Executions, approvals, and idempotency
audit_*        -> Functional audit
rag_*          -> Chunks, embeddings, and metadata
outbox_*       -> Reliable event publication
```

Agent Runtime accesses `rag_*` through a dedicated port or retrieval service. It receives no write permissions on business tables.

## 10. What gets deployed

For the hackathon:

| Unit | Technology | Destination |
|---|---|---|
| `apps/web` | Angular | Firebase Hosting or Cloud Run |
| `services/enterprise-core` | Java | Cloud Run |
| `services/agent-runtime` | Python/ADK | Agent Engine Runtime or Cloud Run |
| PostgreSQL | Cloud SQL | Google Cloud |
| Documents | Cloud Storage | Google Cloud |
| Events | Pub/Sub | Google Cloud |

Future physical separation of CRM, Inventory, or Billing is only considered when a measurable reason around scaling, availability, team, or compliance exists.
