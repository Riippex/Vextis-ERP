# Vextis ERP

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21_Spring_Boot_4.1-orange.svg)](services/enterprise-core)
[![Python](https://img.shields.io/badge/Python-3.13_Google_ADK-yellow.svg)](services/agent-runtime)
[![Angular](https://img.shields.io/badge/Angular-22.1_Signals-red.svg)](apps/web)
[![Google Cloud](https://img.shields.io/badge/Google_Cloud-Vertex_AI_%7C_Cloud_Run_%7C_Cloud_SQL-4285F4.svg)](infra)

> **All Things Agentic Hackathon** official submission under **The Fortified Enterprise Fleet** track.<br>
> Transforming unstructured business inputs into auditable, multi-agent operations across Sales, Inventory, and Finance.

---

## 🌐 Live Deployed Application

- **Hosted URL:** [https://vextis-erp.web.app](https://vextis-erp.web.app) (Deployed on Firebase Hosting)
- **Default Tenant:** `demo-tenant` (Pre-seeded with customer `Acme Colombia`, credit limits, and inventory catalog)
- **Judge Access:** Self-service registration is disabled for enterprise platform security. Dedicated evaluation credentials are provided privately in the *Testing Instructions / Access Instructions* field of the official Devpost submission.

### Live Access & Evaluation Walkthrough
1. **Mission Control (`/app`):** Inspect the 4 registered specialist agents (`vextis_coordinator`, `vextis_crm_agent`, `vextis_inventory_agent`, `vextis_billing_agent`), their active models (`gemini-3.5-flash`), allowed tool scopes, and operational status.
2. **Order-to-Cash Pipeline (`/app/purchase-orders/new`):** Ingest purchase orders, follow autonomous multi-agent planning and stock reservations, review Human-in-the-Loop manager approval gates (`WAITING_FOR_APPROVAL`), and inspect generated invoices.
3. **Ask Vextis (Floating Assistant Widget):** Query grounded enterprise Q&A with similarity citations backed by PostgreSQL `pgvector`, and engage in real-time bidirectional voice interaction via Gemini Live Audio WebSockets directly to the Live gateway.

---

## 🌟 Hackathon Track & Architecture Overview

Vextis participates officially under **The Fortified Enterprise Fleet** track, incorporating autonomous task execution and collaborative assistance as supporting capabilities:

1. **The Fortified Enterprise Fleet (Official Entry Track):**
   - **Mission Control Registry:** Real-time visibility into registered specialist agents, model configurations (`gemini-3.5-flash`), prompt versions, and strict tool allowlists.
   - **Deterministic Tool Governance:** Every database mutation is executed exclusively by Java Enterprise Core. Tool invocations outside an agent's registered capability are blocked server-side and recorded as `DENIED`.
   - **Durable Audit Trail:** Structured PostgreSQL audit logging tracking actors (`AGENT` vs `USER`), correlation IDs, timestamps, and execution outcomes.

2. **Supporting Autonomous Workflows (Order-to-Cash):**
   - **Intake to Settlement:** Ingests unformatted purchase order PDFs, uses Gemini 3.5 Flash to extract SKU line items, coordinates inventory availability, reserves stock, evaluates credit terms, and issues invoices.
   - **Human-in-the-Loop (HITL) Checkpoints:** Automatically halts workflows requiring managerial sign-off before committing high-risk credit or financial decisions.

3. **Supporting Collaborative Assistant (Ask Vextis):**
   - **Gemini Live Audio Bridge:** Real-time, low-latency bidirectional voice interaction over WebSockets directly to `vextis-agent-runtime-live` using browser Web Audio API streaming.
   - **Grounded pgvector RAG:** Enterprise document Q&A backed by PostgreSQL vector search with similarity thresholds and source citations.
   - **On-Demand Proposal Visuals:** Capability-gated generation of proposal visual concepts via Imagen 3 on Vertex AI, registered with generation metadata and signed Cloud Storage URLs.

---

## 🏛 Architecture Diagram

```text
+-------------------------------------------------------------------------+
|                   FIREBASE HOSTING: ANGULAR WEB APP                     |
|                        https://vextis-erp.web.app                       |
|           Mission Control (/app) · New Order · Ask Vextis Widget        |
+-------------------+---------------------------------+-------------------+
                    | GraphQL (HTTP/HTTPS)            | WebSockets (/ws/live)
                    v                                 v
+-------------------+--------------------+  +---------+-------------------+
|      CLOUD RUN: ENTERPRISE CORE        |  |    CLOUD RUN: LIVE GATEWAY  |
|      (Java 21 / Spring Boot 4.1.0)     |  |       (Python 3.13 / ADK)   |
|  - Sole authority for mutations        |  |  - Bidirectional Web Audio  |
|  - Multi-tenant data isolation         |  |  - WebSocket Session Client |
|  - Role-based tool allowlists          |  +---------+-------------------+
|  - Structured PostgreSQL audit log     |            |
+-------------------+--------------------+            |
                    | Outbox Events                   |
                    v (Pub/Sub 'order-events')        |
+-------------------+--------------------+            |
|       CLOUD RUN: AGENT RUNTIME         |            |
|          (Python 3.13 / ADK)           |            |
|  - Specialist Fleet Coordination       |            |
|  - Gemini 3.5 Flash Reasoning          |            |
|  - Grounded pgvector RAG               |            |
+-------------------+--------------------+            |
                    | Tool Calls (REST)               |
                    v                                 v
+-------------------+--------------------+  +---------+-------------------+
|      MANAGED GOOGLE CLOUD DATA         |  |       VERTEX AI & STORAGE   |
|   - Cloud SQL (PostgreSQL 16)          |  |   - Gemini 3.5 Flash / Live |
|   - pgvector (Embeddings & RAG)        |  |   - Imagen 3 (Concepts)     |
|   - Cloud Pub/Sub ('order-events')     |  |   - Cloud Storage (GCS)     |
+----------------------------------------+  +-----------------------------+
```

- **Java** is the sole authority for CRM, inventory, and billing mutations.
- **Python** coordinates agents, RAG, and workflows, but does not write directly to business tables.
- **GraphQL SDL, OpenAPI, AsyncAPI, and JSON Schema** serve as strict contracts between Angular, Java, and Python.
- **PostgreSQL + pgvector** stores transactional data, durable state, audit logs, outbox events, and RAG embeddings.
- **Cloud Storage** stores uploaded documents and generated proposal assets.

---

## 📁 Monorepo Structure

```text
apps/web/                         Angular 22.1.x web client (Material 3, Apollo GraphQL, Signals, Web Audio)
services/enterprise-core/         Java 21 + Spring Boot 4.1.0 transactional backend
services/agent-runtime/           Python 3.13 + Google ADK agent runtime and Live gateway
contracts/                        GraphQL SDL, internal OpenAPI 3.0, AsyncAPI, and JSON Schemas
infra/                            Terraform IAC, deployment configs, and deterministic seed scripts
docs/                             Architecture decisions (ADR), runbooks, demo script, and pitch deck
```

---

## 🚀 Local Quickstart

### Prerequisites
- **Java 17+ or 21** (The Gradle wrapper automatically configures the required Java 21 toolchain)
- **Node.js 22+** and **pnpm 10+**
- **Python 3.13** and **uv**
- **Docker Desktop**

### Setup & Run
```powershell
# 1. Clone & prepare environment
Copy-Item .env.example .env

# 2. Launch local infrastructure & services
./tools/dev.ps1 infra     # Local PostgreSQL + pgvector container + Pub/Sub emulator in Docker
./tools/dev.ps1 core      # Enterprise Core (Java 21 / Spring Boot on :8080)
./tools/dev.ps1 agents    # Agent Runtime (Python 3.13 / Google ADK on :8081)
./tools/dev.ps1 web       # Angular Web UI (on :4200)

# 3. Seed demo data (optional)
./tools/seed-demo.ps1     # Seeds customers, inventory, and credit profiles for demo-tenant
```

- **Angular Web UI:** `http://localhost:4200`
- **Public GraphQL Endpoint:** `http://localhost:8080/graphql`
- **Agent Runtime Health:** `http://localhost:8081/health`
- **Full Verification Suite:** `./tools/check.ps1`

---

## 🧪 Comprehensive Verification Suites

| Component | Test Suite | Validation Status |
|---|---|---|
| **Enterprise Core** | `./gradlew test` | Unit, controller, and integration tests passing cleanly |
| **Agent Runtime** | `uv run pytest`, `uv run ruff check`, `uv run mypy` | Unit and contract tests passing (excludes `model_eval` by default); lint and typecheck clean |
| **Angular Web UI** | `pnpm web:test`, `pnpm web:lint`, `pnpm web:build` | Unit tests passing, ESLint clean, production build passing |

> Note: `uv run pytest` runs fast unit and contract tests by default. Tests marked `@pytest.mark.model_eval` require live Vertex AI credentials and are invoked explicitly with `uv run pytest -m model_eval`.

---

## 📚 Deliverables & Documentation

- **Demo Video:** `[Demo Video Placeholder - Recording in progress]`
- **Demo Screenplay & Script:** [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md)
- **Pitch Deck & Architecture Story:** [`docs/PITCH_DECK.md`](docs/PITCH_DECK.md)
- **Devpost Submission Narrative:** [`docs/SUBMISSION.md`](docs/SUBMISSION.md)
- **Contracts Specification:** [`docs/CONTRACTS.md`](docs/CONTRACTS.md)
- **Tech Stack Specification:** [`docs/TECH_STACK.md`](docs/TECH_STACK.md)

---

## 📄 License & Attributions

Copyright © 2026 Rafael Patiño Díaz.
Distributed under the [Apache License 2.0](LICENSE). See also [NOTICE](NOTICE).
