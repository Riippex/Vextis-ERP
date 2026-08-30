# Vextis ERP — Official Demo Script & Screenplay

> **Track:** The Fortified Enterprise Fleet
> **Target Video Duration:** 3:30 – 4:00 minutes
> **Demo Operator:** Rafael Patiño Díaz
> **Deployed Web Application:** [https://vextis-erp.web.app](https://vextis-erp.web.app) (Firebase Hosting)
> **Local Environment:** `http://localhost:4200` (Angular) · `http://localhost:8080` (Enterprise Core) · `http://localhost:8081` (Agent Runtime)
> **Demo Video Status:** `[Demo Video Placeholder - Recording in progress]`

---

## Screenplay Overview

```text
[0:00 - 0:35] Part 1: Mission Control (/app) & Fortified Agent Fleet Governance
[0:35 - 1:45] Part 2: Autonomous Order-to-Cash Execution (/app/purchase-orders/new) & Policy Guardrails
[1:45 - 2:30] Part 3: Human-in-the-Loop Approval, Invoice Issuance & Proposal Assets
[2:30 - 3:20] Part 4: Ask Vextis (Widget) — Live Gemini Audio & Grounded pgvector RAG
[3:20 - 3:45] Part 5: Dual-Runtime Architecture, Audit Trail & Cloud Infrastructure
```

---

## Part 1: Mission Control & Fleet Governance (0:00 – 0:35)

### Visual
- Browser opens on **Mission Control Dashboard** (`/app`).
- Screen displays the **Approved Agent Registry** table with live telemetry:
  - `vextis_coordinator`: Model `gemini-3.5-flash`, Prompt `v1.0.0`, Allowed Tools: `record_execution_plan`, `request_workflow_approval`.
  - `vextis_crm_agent`: Model `gemini-3.5-flash`, Allowed Tools: `lookup_customer`, `register_quote_asset`.
  - `vextis_inventory_agent`: Model `gemini-3.5-flash`, Allowed Tools: `get_stock`, `reserve_stock`.
  - `vextis_billing_agent`: Model `gemini-3.5-flash`, Allowed Tools: `get_credit`, `create_invoice`.

### Narration (English)
> *"Welcome to Vextis ERP, an agentive enterprise operating system built for the Google All Things Agentic Hackathon under **The Fortified Enterprise Fleet** track.<br>
> Unlike unconstrained chatbots, Vextis coordinates multi-agent operations across three core enterprise departments: **CRM & Sales**, **Inventory & Operations**, and **Finance & Billing**.<br>
> Here in Mission Control (`/app`), we see our live Governed Agent Fleet. Every agent is powered by **Gemini 3.5 Flash** and orchestrated via the **Google Agent Development Kit (ADK)**. Every tool is strictly governed: the Agent Runtime never touches the transactional database directly. Only Java Enterprise Core holds write authority through server-side tool allowlists and tenant isolation."*

---

## Part 2: Autonomous Order-to-Cash Execution & Policy Guardrails (0:35 – 1:45)

### Visual
- Navigate to **Receive Purchase Order** (`/app/purchase-orders/new`).
- Select sample purchase order `output/pdf/vextis-demo-purchase-order.pdf` (Purchase order from Acme Colombia for 10 units of `VXT-CHAIR-01` at COP 100 each, subtotal COP 1,000 + IVA COP 190 = total COP 1,190 with Net 30 payment terms).
- Click **"Process Purchase Order"**.
- The UI triggers:
  1. Signed URL Cloud Storage upload to `vextis-erp-hackathon-assets`.
  2. Enterprise Core intake event via Transactional Outbox.
  3. Google Cloud Pub/Sub topic `order-events` pushes event to Agent Runtime (`vextis-agent-runtime`).
  4. Real-time execution timeline updates:
     - `Order received`
     - `Agent planning started`
     - `Gemini 3.5 Flash produced structured execution plan`
     - `CRM context validated: Active Customer Acme Colombia`
     - `Inventory checked: 40 units in stock; 10 units reserved in PostgreSQL (30 available, 10 reserved)`
     - `Finance readiness evaluated: Commercial terms requiring managerial approval`
  5. Audit log reflects server-side policy enforcement:
     - Invocations outside an agent's registered capability are blocked with status `DENIED`.

### Narration (English)
> *"Let's watch an autonomous order-to-cash workflow in action.<br>
> A customer sends an unformatted purchase order PDF. When submitted, Enterprise Core uploads it to Cloud Storage, writes an outbox event to Google Cloud Pub/Sub topic `order-events`, and the Google ADK Coordinator kicks off.<br>
> Gemini 3.5 extracts the order lines, validates the customer through the CRM Specialist agent, and the Inventory Specialist executes an authoritative stock reservation in PostgreSQL, updating stock from 40 available to 30 available and 10 reserved.<br>
> Notice our security guardrail in the audit trail: when an unauthorized action or unapproved tool is invoked, Enterprise Core immediately rejects the call with a durable **DENIED** audit record."*

---

## Part 3: Human-in-the-Loop Approval, Invoices & Proposal Assets (1:45 – 2:30)

### Visual
- Execution state switches to `WAITING_FOR_APPROVAL` with an amber badge.
- The **Approval Request Card** displays:
  - *Recommendation:* Approve payment terms (Net 30 days for 1,190.00 COP).
  - *Decider:* Human Operator / Financial Manager.
- Click **"Approve Execution"** button.
- Execution transitions to `APPROVED`, then `COMPLETED`:
  - **Invoice Generated**: Invoice `#INV-2026-001` with subtotal 1,000.00 COP, tax 190.00 COP, total 1,190.00 COP.
  - **Proposal Asset Card**: Displays `[AI-GENERATED]` badge, Model: `imagen-3.0-generate-002`, prompt summary: *"Ergonomic executive task chair 3D visual concept"*, and signed Cloud Storage URL.

### Narration (English)
> *"Because commercial credit terms exceed automated thresholds, the workflow safely halts at a **Human-in-the-Loop** gate.<br>
> As the authorized manager, I review the readiness checks and click Approve. Enterprise Core verifies the authorization token, transitions the execution, and instructs the Billing Specialist to generate the invoice.<br>
> On demand, our CRM Specialist can invoke **Imagen 3 on Vertex AI** to produce an AI-generated proposal concept asset, securely registering it in the CRM quote directory with model provenance and generation-pinned signed URLs."*

---

## Part 4: Ask Vextis — Live Audio & Grounded pgvector RAG (2:30 – 3:20)

### Visual
- Click the floating **Ask Vextis** widget in the bottom corner of the interface.
- **Test 1: Gemini Live Audio WebSocket**:
  - Click the microphone icon (consent modal accepted).
  - Voice prompt: *"¿Cuál es el estado del inventario para la silla ejecutiva VXT-CHAIR-01?"*
  - Audio waveform animates, Gemini Live responds in real-time streaming audio via the dedicated `vextis-agent-runtime-live` gateway: *"Actualmente hay 30 unidades disponibles y 10 unidades reservadas para el pedido de Acme Colombia."*
- **Test 2: Grounded pgvector RAG with Document Evidence**:
  - Type in chat: *"¿Cuáles son las políticas de crédito para clientes corporativos?"*
  - Response renders with grounded source snippet and dynamic similarity citation: `[Source: commercial_policy.pdf, similarity >= 0.55]`.

### Narration (English)
> *"Now let's explore **Ask Vextis**, our collaborative assistant widget.<br>
> First, we have real-time bidirectional voice powered by **Gemini Live Audio** over WebSockets connected directly to our dedicated `vextis-agent-runtime-live` gateway with Web Audio streaming.<br>
> Second, Ask Vextis uses **pgvector RAG** over enterprise documents in PostgreSQL. Answers are grounded with cosine similarity scores above our 0.55 similarity floor and cite `commercial_policy.pdf` to eliminate hallucination.<br>
> All interactions respect strict multi-tenant boundaries."*

---

## Part 5: Enterprise Architecture, Audit Trail & Cloud Proof (3:20 – 3:45)

### Visual
- Scroll to the **Governance & Audit Trail** section on the order execution view.
- Expand audit entries showing:
  - Event UUIDs, Correlation IDs (`corr-001`), Actor Types (`AGENT`, `USER`), Tool Names, Execution Timestamps, and Status (`SUCCEEDED`, `DENIED`).
- Google Cloud Console overview:
  - Firebase Hosting: `https://vextis-erp.web.app` (Angular Web)
  - Cloud Run services: `vextis-enterprise-core` (Public/Private), `vextis-agent-runtime`, `vextis-agent-runtime-live`
  - Cloud SQL PostgreSQL instance with `pgvector`
  - Cloud Pub/Sub topic `order-events`
  - Cloud Storage bucket `vextis-erp-hackathon-assets`

### Narration (English)
> *"Every single action taken across the entire lifecycle is durably recorded in the PostgreSQL audit log with end-to-end correlation tracking.<br>
> Vextis ERP runs on Google Cloud using Firebase Hosting for the frontend, Cloud Run for Java Core and Python Agent Runtime, Cloud SQL with pgvector, Pub/Sub, Vertex AI, and the Google Agent Development Kit.<br>
> Thank you for reviewing Vextis ERP — the fortified enterprise agent fleet."*

---

## Demo Preparation & Recovery Checklist

1. **Deterministic Local/Demo Database Seeding**:
   - Run `./tools/seed-demo.ps1` (or `powershell ./infra/seed.ps1`) to populate `demo-tenant` with customers, inventory catalog (40 units of `VXT-CHAIR-01`), credit profiles, and local knowledge documents (`commercial_policy.pdf`). In production, document ingestion is performed through the governed ingestion API using real Vertex AI embeddings without mock embeddings.
2. **Demo Purchase Order File**:
   - Generate sample PDF with `uv run infra/seed/generate_demo_purchase_order.py` (creates `output/pdf/vextis-demo-purchase-order.pdf` with 10 units at COP 100 each, subtotal COP 1,000 + IVA COP 190 = COP 1,190).
3. **Judge Evaluation Access**:
   - Authentication is required to enter the application; judge credentials are provided securely in the private testing instructions on Devpost.
4. **Offline Mock Fallback (if external Vertex latency occurs during rehearsal)**:
   - Set `VEXTIS_RAG_MOCK_EMBEDDINGS_ENABLED=true` / `VEXTIS_IMAGEN_MOCK_ENABLED=true` for deterministic sub-second local test responses.
