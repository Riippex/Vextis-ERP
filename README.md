# Vextis ERP

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-21_Spring_Boot_3.4-orange.svg)](./services/enterprise-core)
[![Python](https://img.shields.io/badge/Python-3.13_Google_ADK-yellow.svg)](./services/agent-runtime)
[![Angular](https://img.shields.io/badge/Angular-19_Signals-red.svg)](./apps/web)
[![Google Cloud](https://img.shields.io/badge/Google_Cloud-Vertex_AI_%7C_Cloud_Run_%7C_Cloud_SQL-4285F4.svg)](./infra)

> **All Things Agentic Hackathon** official submission under **The Fortified Enterprise Fleet** track.  
> Transforming unstructured business inputs into auditable, multi-agent operations across Sales, Inventory, and Finance.

---

## 🌟 Hackathon Track & Highlights

Vextis unites the three competition capabilities into a single cohesive platform:

1. **The Fortified Enterprise Fleet (Official Entry Track):**
   - **Mission Control Registry:** Real-time visibility into registered specialist agents (`vextis_coordinator`, `vextis_crm_agent`, `vextis_inventory_agent`, `vextis_billing_agent`), their active models (`gemini-3.5-flash`), prompt versions, and strict tool allowlists.
   - **Deterministic Tool Governance:** Every database write is executed exclusively by Java Enterprise Core. Tool invocations outside an agent's registered capability are instantly rejected and recorded as `DENIED`.
   - **Durable Audit Trail:** Immutable PostgreSQL audit logging tracking actors (`AGENT` vs `USER`), correlation IDs, timestamps, and outcomes.

2. **The Taskmaster (Autonomous Workflows):**
   - **Intake to Settlement:** Ingests unformatted purchase order PDFs, uses Gemini to extract SKU lines, coordinates inventory availability, reserves real stock, evaluates credit terms, and issues official fiscal invoices.
   - **Human-in-the-Loop (HITL) Checkpoints:** Automatically halts workflows requiring managerial sign-off before committing high-risk credit or financial decisions.

3. **The Collaborative Partner (Ask Vextis & Multimodal Innovation):**
   - **Gemini Live Audio Bridge:** Real-time, low-latency bidirectional voice interaction over WebSockets using browser Web Audio API streaming.
   - **Grounded pgvector RAG:** Instant enterprise document Q&A backed by PostgreSQL vector search with strict similarity thresholds and source citations.
   - **Vertex AI Memory Bank:** Persistent cross-conversation memory bounded by tenant isolation.
   - **Imagen 3 Concept Visuals:** Automatic generation of 3D proposal visuals with prompt sanitization, registered directly in the CRM quote directory.

---

## 🏛 Official Architecture

```text
+-------------------------------------------------------------------------+
|                              ANGULAR WEB                                |
|             Mission Control · Purchase Orders · Ask Vextis              |
+------------------------------------+------------------------------------+
                                     | GraphQL / WebSockets
                                     v
+------------------------------------+------------------------------------+
|            TRANSACTIONAL AUTHORITY (Java 21 / Spring Boot)              |
|   - Sole authority for mutations   - Cryptographic tool policies        |
|   - Multi-tenant data isolation    - Durable PostgreSQL audit trail     |
+------------------+---------------------------------+--------------------+
                   | Outbox Events                   ^ Tool Invocations
                   v (Pub/Sub)                       | (REST + HMAC/Token)
+------------------+---------------------------------+--------------------+
|               AGENTIC INTELLIGENCE (Python 3.13 / Google ADK)           |
|   - Specialist Fleet Coordination  - Gemini 3.5 Flash Planner           |
|   - Grounded pgvector RAG          - Vertex AI Memory Bank              |
|   - Gemini Live Audio WebSocket    - Imagen 3 Visual Asset Generator    |
+-------------------------------------------------------------------------+
```

- **Java** is the sole authority for CRM, inventory, and billing mutations.
- **Python** coordinates agents, RAG, memory, and workflows, but does not write directly to business tables.
- **GraphQL SDL, OpenAPI, AsyncAPI, and JSON Schema** are the contracts between Angular, Java, and Python.
- **PostgreSQL + pgvector** holds transactional data, durable state, audit, outbox, idempotency, and RAG vectors.
- **Cloud Storage** holds documents and original artifacts.

---

## 📁 Monorepo Structure

```text
apps/web/                         Angular 22 web client (Material 3, Apollo GraphQL, Signals, Web Audio)
services/enterprise-core/         Java 21 + Spring Boot 4.1 transactional backend
services/agent-runtime/           Python 3.13 + Google ADK agent runtime
contracts/                        GraphQL SDL, internal OpenAPI 3.0, AsyncAPI, and JSON Schemas
infra/                            Terraform IAC, deployment configs, and deterministic seed scripts
docs/                             Architecture decisions (ADR), runbooks, demo script, and pitch deck
```

---

## 🚀 Local Quickstart

### Prerequisites
- **Java 17+** (The Gradle wrapper automatically configures Java 21)
- **Node.js 24** and **pnpm 11**
- **Python 3.13** and **uv**
- **Docker Desktop**

### Setup & Run
```powershell
# 1. Clone & prepare environment
Copy-Item .env.example .env

# 2. Launch infrastructure & services
./tools/dev.ps1 infra     # Cloud SQL PostgreSQL + pgvector + Pub/Sub emulator
./tools/dev.ps1 core      # Enterprise Core (Java 21 / Spring Boot on :8080)
./tools/dev.ps1 agents    # Agent Runtime (Python 3.13 / Google ADK on :8081)
./tools/dev.ps1 web       # Angular Web UI (on :4200)
```

- **Angular Web UI:** `http://localhost:4200`
- **Public GraphQL Endpoint:** `http://localhost:8080/graphql`
- **Agent Runtime Health:** `http://localhost:8081/health`
- **Full Verification Suite:** `./tools/check.ps1`

---

## 🧪 Comprehensive Verification Suite

| Component | Test Suite | Results |
|---|---|---|
| **Enterprise Core** | `./gradlew test` | **134 passed / 0 failures** |
| **Agent Runtime** | `pytest` & `ruff check` | **135 passed / 0 failures (0 lint errors)** |
| **Angular Web UI** | `pnpm test`, `pnpm lint`, `pnpm build` | **32 passed / 0 failures (0 lint errors, build clean)** |

---

## 📚 Official Hackathon Deliverables

- **Demo Video Script:** [`docs/DEMO_SCRIPT.md`](./docs/DEMO_SCRIPT.md)
- **Pitch Deck & Architecture Story:** [`docs/PITCH_DECK.md`](./docs/PITCH_DECK.md)
- **Official Devpost Submission Narrative:** [`docs/SUBMISSION.md`](./docs/SUBMISSION.md)
- **Contracts Documentation:** [`docs/CONTRACTS.md`](./docs/CONTRACTS.md)
- **Tech Stack Specification:** [`docs/TECH_STACK.md`](./docs/TECH_STACK.md)

---

## 📄 License & Attributions

Copyright © 2026 Rafael Patiño Díaz.  
Distributed under the [Apache License 2.0](./LICENSE). See also [NOTICE](./NOTICE).
