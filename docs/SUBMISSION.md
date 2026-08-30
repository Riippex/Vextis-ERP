# Vextis ERP — Devpost Official Submission Package

> **Competition:** Google Cloud All Things Agentic Hackathon
> **Official Track:** The Fortified Enterprise Fleet
> **Repository:** [https://github.com/Riippex/Vextis-ERP](https://github.com/Riippex/Vextis-ERP)
> **Live Deployed Application:** [https://vextis-erp.web.app](https://vextis-erp.web.app) (Firebase Hosting)
> **Demo Video Status:** `[Demo Video Placeholder - Recording in progress]`
> **Author:** Rafael Patiño Díaz

---

## 1. Project Title & Elevator Pitch

### Project Title
**Vextis ERP — The Governed Multi-Agent Enterprise Operating System**

### Elevator Pitch (English)
> *Vextis transforms unstructured business documents and voice requests into auditable, end-to-end operations across Sales, Inventory, and Billing through a governed fleet of Gemini-powered agents, backed by a Java dual-authority architecture on Google Cloud.*

### Elevator Pitch (Spanish)
> *Vextis convierte documentos comerciales no estructurados y solicitudes por voz en operaciones empresariales completas y auditables en Ventas, Inventario y Facturación mediante una flota gobernada de agentes Gemini, respaldada por una arquitectura de doble autoridad en Google Cloud.*

---

## 2. Inspiration & The Problem

Mid-market enterprises process numerous purchase orders, quotes, and pricing requests daily through manual coordination across fragmented systems and spreadsheets.

When companies attempt to introduce Generative AI agents into operational workflows, they face the **"Rogue Agent Dilemma"**: giving LLMs direct write access to enterprise databases introduces severe risks of data races, unapproved terms, and unconstrained mutations.

We built **Vextis ERP** to prove that enterprise agent fleets can be both **deeply autonomous** and **transactionally governed**.

---

## 3. What It Does

Vextis delivers a governed multi-agent enterprise architecture under **The Fortified Enterprise Fleet** track:

1. **The Fortified Enterprise Fleet (Official Entry Track):**
   - **Mission Control Registry (`/app`):** Real-time visibility into registered specialist agents (`vextis_coordinator`, `vextis_crm_agent`, `vextis_inventory_agent`, `vextis_billing_agent`), their active models (`gemini-3.5-flash`), prompt versions, and strict tool allowlists.
   - **Deterministic Tool Governance:** Every database write is executed exclusively by Java Enterprise Core. Tool invocations outside an agent's registered capability are blocked server-side and recorded as `DENIED`.
   - **Durable Audit Trail:** Structured PostgreSQL audit logging tracking actors (`AGENT` vs `USER`), correlation IDs, timestamps, and execution outcomes.

2. **Supporting Autonomous Workflows (Order-to-Cash):**
   - **Intake to Settlement (`/app/purchase-orders/new`):** Ingests unformatted purchase order PDFs, uses Gemini 3.5 Flash to extract SKU lines, coordinates inventory availability, reserves real stock in PostgreSQL, evaluates credit terms, and issues invoices.
   - **Human-in-the-Loop (HITL) Checkpoints:** Automatically halts workflows requiring managerial sign-off before committing high-risk credit or financial decisions.

3. **Supporting Collaborative Assistant (Ask Vextis Widget):**
   - **Gemini Live Audio Bridge:** Real-time, low-latency bidirectional voice interaction over WebSockets using browser Web Audio API streaming directly to a dedicated `vextis-agent-runtime-live` gateway.
   - **Grounded pgvector RAG:** Enterprise document Q&A backed by PostgreSQL vector search with similarity thresholds and source citations.
   - **On-Demand Proposal Visuals:** Capability-gated generation of proposal visual concepts via Imagen 3 on Vertex AI, registered with generation metadata and signed Cloud Storage URLs.

---

## 4. How We Built It

Vextis is built on an enterprise **Dual-Authority Monorepo Architecture**:

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

- **Frontend (`apps/web`):** Angular 22.1.x (Standalone Components, Signals, Material 3, Apollo GraphQL Client, Web Audio API streaming), deployed to **Firebase Hosting**.
- **Transactional Authority (`services/enterprise-core`):** Java 21, Spring Boot 4.1.0, Spring Security, Flyway database migrations, Transactional Outbox Pattern, JDBC Idempotent Repositories, deployed to **Cloud Run**.
- **Agent Intelligence & Live Gateway (`services/agent-runtime`):** Python 3.13, Google Agent Development Kit (ADK), Gemini 3.5 Flash, WebSocket Live Audio Bridge, deployed to **Cloud Run**.
- **Google Cloud Infrastructure (`infra/`):** Terraform IAC provisioning Cloud Run, Cloud SQL (PostgreSQL 16 with `pgvector`), Cloud Pub/Sub (`order-events`), Cloud Storage (`vextis-erp-hackathon-assets`), and Google Secret Manager.
- **Contracts (`contracts/`):** Version-controlled schema definitions in GraphQL SDL, OpenAPI 3.0, and AsyncAPI.

---

## 5. Live Application Access & Testing Guide

Judges can evaluate the deployed system live at **[https://vextis-erp.web.app](https://vextis-erp.web.app)**:

1. **Mission Control (`/app`):**
   - Review registered agents (`vextis_coordinator`, `vextis_crm_agent`, `vextis_inventory_agent`, `vextis_billing_agent`), their active models (`gemini-3.5-flash`), allowed tool scopes, and operational status.
2. **Order Execution & Manager Approval (`/app/purchase-orders/new`):**
   - Submit purchase order documents, follow multi-agent coordination steps, inspect Human-in-the-Loop approval checkpoints (`WAITING_FOR_APPROVAL`), and review generated invoices.
3. **Ask Vextis (Floating Assistant Widget):**
   - Test grounded document Q&A with similarity citations backed by PostgreSQL `pgvector`, and test real-time voice streaming via Gemini Live Audio WebSockets directly to the Live gateway.

---

## 6. Challenges We Overcame

1. **Separating Mutation Authority from AI Reasoning:** Preventing data inconsistencies by ensuring the Agent Runtime has zero direct SQL write access; all mutations must be authorized and validated by Enterprise Core tool controllers.
2. **Multi-Tenant Data Isolation:** Enforcing tenant scoping across GraphQL resolvers, database queries, and vector similarity search.
3. **Low-Latency Voice Streaming:** Building a bidirectional Web Audio PCM streaming pipeline connecting browser WebSockets directly to the dedicated Live gateway.

---

## 7. What We Learned

- High-trust enterprise AI requires strict architectural boundaries and explicit server-side authorization rather than reliance on prompt constraints alone.
- The Google Agent Development Kit (ADK) paired with Gemini 3.5 Flash provides reliable structured tool calling for business workflow coordination.
- Version-controlled API schemas (OpenAPI, GraphQL, AsyncAPI) prevent runtime integration drift across microservices.

---

## 8. What's Next for Vextis ERP

- **Automated B2B EDI Connectors:** Ingestion pipelines for standard XML/EDIFACT purchase order streams.
- **Predictive Inventory Replenishment:** Proactive material reordering based on historical demand trends.
- **Mobile Warehouse Client:** Extending Gemini Live voice capabilities to Android warehouse handhelds.

---

## 9. Built With

`google-cloud` `vertex-ai` `gemini-3.5-flash` `google-agent-development-kit` `imagen-3` `gemini-live` `java-21` `spring-boot` `python-3.13` `angular` `postgresql` `pgvector` `docker` `terraform` `cloud-run` `cloud-pubsub` `cloud-storage` `firebase-hosting`

---

## 10. Deliverables & Reference Links

- **GitHub Repository:** [https://github.com/Riippex/Vextis-ERP](https://github.com/Riippex/Vextis-ERP)
- **Live Deployed Web App:** [https://vextis-erp.web.app](https://vextis-erp.web.app)
- **Demo Video:** `[Demo Video Placeholder - Recording in progress]`
- **Demo Screenplay & Script:** [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md)
- **Pitch Deck & Architecture Story:** [`docs/PITCH_DECK.md`](docs/PITCH_DECK.md)
- **Contracts Specification:** [`docs/CONTRACTS.md`](docs/CONTRACTS.md)
- **Tech Stack Specification:** [`docs/TECH_STACK.md`](docs/TECH_STACK.md)
