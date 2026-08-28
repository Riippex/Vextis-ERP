# Vextis ERP — Devpost Official Submission Package

> **Competition:** Google Cloud All Things Agentic Hackathon  
> **Official Track:** The Fortified Enterprise Fleet  
> **Repository:** [https://github.com/Riippex/Vextis-ERP](https://github.com/Riippex/Vextis-ERP)  
> **Author:** Rafael Patiño Díaz  

---

## 1. Project Title & Elevator Pitch

### Project Title
**Vextis ERP — The Governed Multi-Agent Enterprise Operating System**

### Elevator Pitch (English)
> *Vextis transforms unstructured business documents and voice requests into auditable, end-to-end operations across Sales, Inventory, and Billing through a governed fleet of Gemini-powered agents, guaranteed by a Java dual-authority architecture on Google Cloud.*

### Elevator Pitch (Spanish)
> *Vextis convierte documentos comerciales no estructurados y solicitudes por voz en operaciones empresariales completas y auditables en Ventas, Inventario y Facturación mediante una flota gobernada de agentes Gemini, respaldada por una arquitectura de doble autoridad en Google Cloud.*

---

## 2. Inspiration & The Problem

Mid-market enterprises lose thousands of hours annually manually translating customer purchase orders, checking stock across disparate spreadsheets, and reconciling credit policies. 

When enterprises attempt to adopt Generative AI agents, they face the **"Rogue Agent Dilemma"**: giving autonomous LLMs direct write access to enterprise SQL databases introduces catastrophic risks of hallucinations, unauthorized discounts, and data corruption. 

We built **Vextis ERP** to prove that enterprise agent fleets can be both **deeply autonomous** and **mathematically governed**.

---

## 3. What It Does

Vextis unites the three official hackathon capabilities in a single industrial platform:

1. **The Fortified Enterprise Fleet (Track Entry):**
   - **Mission Control Registry:** Real-time visibility into registered agents (`vextis_coordinator`, `vextis_crm_agent`, `vextis_inventory_agent`, `vextis_billing_agent`), their active models (`gemini-3.5-flash`), prompt versions, and strict tool allowlists.
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

## 4. How We Built It

Vextis is built on an enterprise **Dual-Authority Monorepo Architecture**:

```text
                                  +-----------------------+
                                  |      Angular Web      |
                                  |   (Material 3 / UI)   |
                                  +-----------+-----------+
                                              | GraphQL
                                              v
+-----------------------+         +-------------------------------+
|  Cloud SQL PostgreSQL | <-----> |   Java 21 / Spring Boot Core  |
|  + pgvector (Vector)  |         |   (Sole Mutation Authority)   |
+-----------------------+         +---------------+---------------+
                                                  | Outbox Events
                                                  v (GCP Pub/Sub)
+-----------------------+         +---------------+---------------+
|  Vertex AI / Gemini   | <-----> |   Python 3.13 Agent Runtime   |
|  (Memory Bank, Live)  |         |   (Google ADK Specialist Fleet)
+-----------------------+         +-------------------------------+
```

- **Frontend (`apps/web`):** Angular 19+ (Standalone Components, Signals, Material 3, Apollo GraphQL Client, Web Audio API streaming).
- **Transactional Authority (`services/enterprise-core`):** Java 21, Spring Boot 3.4, Spring Security, Flyway database migrations, Transactional Outbox Pattern, JDBC Idempotent Repositories.
- **Agent Intelligence (`services/agent-runtime`):** Python 3.13, Google Agent Development Kit (ADK), Gemini 3.5 Flash, Vertex AI ImageGenerationModel (Imagen 3), WebSocket Live Bridge.
- **Google Cloud Infrastructure (`infra/`):** Terraform IAC provisioning Cloud Run, Cloud SQL (PostgreSQL with `pgvector`), Cloud Pub/Sub, Cloud Storage, and Google Secret Manager.
- **Contracts (`contracts/`):** Strict single-source-of-truth schema definitions in GraphQL SDL, OpenAPI 3.0, and AsyncAPI.

---

## 5. Challenges We Overcame

1. **Eliminating Direct Database Mutation by AI:** Early agent prototypes frequently caused data race conditions. We solved this by designing the *Agent-Tools API* where agents only suggest intents, and Enterprise Core validates cryptographic signatures, RBAC permissions, and ACID constraints before writing.
2. **Deterministic Multi-Tenant Memory:** Bounding Vertex AI Memory Bank and pgvector queries so that tenant data can never leak across organization boundaries, even under complex conversational queries.
3. **WebSockets Audio Synchronization:** Building a resilient Web Audio PCM pipeline to stream microphone input to the Gemini Live endpoint with zero audio degradation and instant interruption handling.

---

## 6. Accomplishments That We're Proud Of

- **100% Real Transactional Backing:** Zero fake mocks in the primary workflow. Real PDF intake -> real Pub/Sub event -> real Gemini ADK planning -> real SQL stock reservation -> real invoice generation.
- **Comprehensive Test & Eval Coverage:** Over 300 automated unit, integration, and contract evaluation tests passing cleanly across Java (136 tests), Python (137 tests), and Angular (32 tests).
- **Instant Recovery & Deterministic Seeding:** Fully automated database seeding and reset script (`./infra/seed.ps1`) ensuring 100% reproducible demo states.

---

## 7. What We Learned

- High-trust enterprise AI does not come from smarter prompts, but from **stricter architectural boundaries**.
- The Google Agent Development Kit (ADK) paired with Gemini 3.5 Flash provides unmatched speed and tool-calling fidelity for structured business workflows.
- Keeping schemas version-controlled across OpenAPI and GraphQL prevents agent drift and runtime integration bugs.

---

## 8. What's Next for Vextis ERP

- **Automated EDI & B2B Ingestion Connectors:** Ingesting XML/EDIFACT purchase orders from external vendor networks.
- **Predictive Inventory Replenishment Agents:** Proactively ordering raw materials based on predicted sales trends.
- **Full Mobile Companion:** Extending the Gemini Live voice interface to an Android mobile client for warehouse operators.

---

## 9. Built With

`google-cloud` `vertex-ai` `gemini-3.5-flash` `google-agent-development-kit` `imagen-3` `gemini-live` `java-21` `spring-boot` `python-3.13` `angular` `postgresql` `pgvector` `docker` `terraform` `cloud-run` `cloud-pubsub` `cloud-storage`

---

## 10. Links & Verification

- **GitHub Repository:** [https://github.com/Riippex/Vextis-ERP](https://github.com/Riippex/Vextis-ERP)
- **Demo Video (YouTube):** [https://www.youtube.com/watch?v=5Xw3LtPeByE](https://www.youtube.com/watch?v=5Xw3LtPeByE)
- **Architecture Documentation:** [`docs/TECH_STACK.md`](file:///docs/TECH_STACK.md) and [`docs/CONTRACTS.md`](file:///docs/CONTRACTS.md)
- **Demo Script:** [`docs/DEMO_SCRIPT.md`](file:///docs/DEMO_SCRIPT.md)
