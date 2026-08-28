# Vextis ERP — Official Demo Script & Screenplay

> **Track:** The Fortified Enterprise Fleet  
> **Target Video Duration:** 3:30 – 4:00 minutes  
> **Demo Operator:** Rafael Patiño Díaz  
> **Environment:** Deployed Google Cloud Run + Cloud SQL + Angular Web (`http://localhost:4200` or Cloud Run Web URL)

---

## Screenplay Overview

```text
[0:00 - 0:35] Part 1: Mission Control & Fortified Agent Fleet Governance
[0:35 - 1:45] Part 2: Autonomous Order-to-Cash Execution & Visible Policy Denial
[1:45 - 2:30] Part 3: Human-in-the-Loop Approval, Invoice Issuance & Multimodal Asset
[2:30 - 3:20] Part 4: Ask Vextis — Live Gemini Audio, Grounded RAG & Memory Bank
[3:20 - 3:45] Part 5: Enterprise Architecture, Audit Trail & Google Cloud Proof
```

---

## Part 1: Mission Control & Fleet Governance (0:00 – 0:35)

### Visual
- Browser opens on **Mission Control** (`/mission-control`).
- Screen displays the **Approved Agent Registry** table with live telemetry:
  - `vextis_coordinator`: `gemini-3.5-flash`, prompt `v1.0.0`, allowed tools: `record_execution_plan`, `request_approval`.
  - `vextis_crm_agent`: `gemini-3.5-flash`, allowed tools: `get_customer_context`, `register_quote_asset`.
  - `vextis_inventory_agent`: `gemini-3.5-flash`, allowed tools: `check_inventory`, `reserve_inventory`.
  - `vextis_billing_agent`: `gemini-3.5-flash`, allowed tools: `check_credit_standing`, `issue_invoice`.

### Narration (English)
> *"Welcome to Vextis ERP, an agentive enterprise platform built for the Google All Things Agentic Hackathon under the **Fortified Enterprise Fleet** track.*  
> *Unlike traditional chatbots, Vextis coordinates complex business operations across three core enterprise departments: **CRM & Sales**, **Inventory & Operations**, and **Finance & Billing**.*  
> *Here in Mission Control, you see our live Governed Agent Fleet. Every agent is powered by **Gemini 3.5 Flash** and orchestrating via the **Google Agent Development Kit (ADK)**. Every tool is strictly governed: the Agent Runtime never touches the transactional database directly. Only Java Enterprise Core holds write authority through cryptographic tool policies and tenant isolation."*

---

## Part 2: Autonomous Order-to-Cash Execution & Visible Policy Denial (0:35 – 1:45)

### Visual
- Navigate to **Purchase Orders > Receive Purchase Order** (`/purchase-orders/receive`).
- Drag-and-drop sample file `PO-2026-001.pdf` (Purchase order from Acme Colombia for 10 units of `VXT-CHAIR-01` totaling 1,190.00 COP with 30-day payment terms).
- Click **"Process Purchase Order"**.
- The UI triggers:
  1. Signed URL Cloud Storage upload.
  2. Enterprise Core intake event via Transactional Outbox.
  3. Google Cloud Pub/Sub pushes event to Agent Runtime.
  4. Real-time execution timeline updates:
     - `Order received`
     - `Agent planning started`
     - `Gemini 3.5 Flash produced 3-step structured plan`
     - `CRM context validated: Active Customer Acme Colombia`
     - `Inventory checked: Stock available & 10 units reserved`
     - `Finance readiness evaluated: Commercial terms requiring executive approval`
  5. Audit trail records an attempted unauthorized tool call by a rogue actor:
     - `Actor: rogue-agent | Action: START_EXECUTION_PLANNING | Result: DENIED`

### Narration (English)
> *"Let's watch a complete autonomous order-to-cash workflow in action.*  
> *A customer sends an unformatted purchase order PDF. We submit the document. Immediately, Enterprise Core uploads it to Cloud Storage, fires an outbox event to Google Cloud Pub/Sub, and the Google ADK Coordinator kicks off.*  
> *Gemini 3.5 extracts the order lines, validates the customer through the CRM Specialist agent, and the Inventory Specialist executes an authoritative stock reservation in PostgreSQL.*  
> *Notice our security guardrail in the audit trail: when an unauthorized identity or unapproved tool is invoked, Enterprise Core immediately rejects the call with a durable **DENIED** audit record."*

---

## Part 3: Human-in-the-Loop Approval & Multimodal Closure (1:45 – 2:30)

### Visual
- Execution state switches to `WAITING_FOR_APPROVAL` with an amber badge.
- The **Approval Request Card** displays:
  - *Recommendation:* Approve payment terms (30 days for 1,190.00 COP).
  - *Decider:* Rafael Patiño (Human Operator).
- Click **"Approve Execution"** button.
- Execution transitions to `APPROVED`, then `COMPLETED`:
  - **Invoice Generated**: Invoice `#INV-2026-001` with subtotal 1,000.00 COP, tax 190.00 COP, total 1,190.00 COP.
  - **Multimodal Proposal Asset Card**: Displaying `[AI-GENERATED]` badge, Model: `imagen-3.0-generate-002`, prompt summary: *"Ergonomic executive office chair 3D visual concept"*, and Cloud Storage URI.

### Narration (English)
> *"Because commercial credit terms exceed automated thresholds, the workflow safely halts at a **Human-in-the-Loop** gate.*  
> *As the authorized manager, I review the readiness checks and click Approve. Enterprise Core verifies my token, transitions the execution, and instructs the Billing Specialist to generate the final fiscal invoice.*  
> *Simultaneously, our CRM Specialist invokes **Imagen 3 on Vertex AI** to produce an AI-generated proposal concept asset, permanently linking it to the customer quote with full model provenance."*

---

## Part 4: Ask Vextis — Live Audio, Grounded RAG & Memory Bank (2:30 – 3:20)

### Visual
- Click the floating **Ask Vextis** widget in the bottom right corner.
- **Test 1: Gemini Live Audio WebSocket**:
  - Click the microphone icon (consent modal accepted).
  - Voice prompt: *"¿Cuál es el estado del inventario para la silla ejecutiva VXT-CHAIR-01?"*
  - Audio waveform animates, Gemini Live responds in real-time streaming audio: *"Actualmente hay 40 unidades disponibles y 10 unidades reservadas para el pedido de Acme Colombia."*
- **Test 2: Grounded RAG with Document Evidence**:
  - Type in chat: *"¿Cuáles son las políticas de crédito para clientes corporativos?"*
  - Response renders with exact grounded source snippet and citation: `[Source: credit_policy_2026.pdf, similarity: 0.89]`.
- **Test 3: Vertex AI Memory Bank**:
  - Type: *"Recuerda que mi moneda de reporte preferida es USD."*
  - Agent answers confirming memory saved.
  - Refresh session, ask: *"¿En qué moneda debo preparar el resumen ejecutivo?"*
  - Agent recalls: *"Prepararé el resumen en USD según tu preferencia recordada."*

### Narration (English)
> *"Now let's explore **Ask Vextis**, our collaborative partner interface.*  
> *First, we have real-time bidirectional voice powered by **Gemini Live Audio** over WebSockets with Web Audio API streaming.*  
> *Second, Ask Vextis uses **pgvector RAG** over enterprise documents. Answers are strictly grounded with similarity scores and document citations to eliminate hallucination.*  
> *Third, it integrates **Vertex AI Agent Engine Memory Bank**, persisting operator preferences across sessions with strict tenant boundaries."*

---

## Part 5: Enterprise Architecture & Cloud Proof (3:20 – 3:45)

### Visual
- Scroll to the bottom of the execution detail page to the **Governance & Audit Trail** section.
- Expand audit entries showing:
  - Immutable UUIDs, Correlation ID `corr-001`, Actor Types (`AGENT`, `USER`), Tool Names, Execution Timestamps, and Status (`SUCCEEDED`, `DENIED`).
- Split screen or brief cut to Google Cloud Console:
  - Cloud Run services: `vextis-enterprise-core`, `vextis-agent-runtime`, `vextis-web`.
  - Cloud SQL PostgreSQL instance with `pgvector` extension.
  - Google Cloud Pub/Sub subscription topic `vextis-execution-events`.

### Narration (English)
> *"Every single action taken across the entire lifecycle is immutably recorded in the durable PostgreSQL audit trail with end-to-end correlation tracking.*  
> *Vextis ERP runs 100% on Google Cloud using Cloud Run, Cloud SQL, Pub/Sub, Vertex AI, and Google ADK.*  
> *Thank you for reviewing Vextis ERP — the fortified enterprise agent fleet."*

---

## Backup Scenario (Fail-Safe Checklist)

If live audio or external Vertex AI latency occurs during recording:
1. **Agent Runtime Mock Mode**: Toggle `VEXTIS_AGENT_MOCK_ENABLED=true` / `IMAGEN_MOCK_ENABLED=true` for 100% offline, deterministic sub-second responses.
2. **Deterministic Seed**: Run `./infra/seed.ps1` to restore the demo database to the initial pristine state in 2 seconds.
3. **Pre-recorded Take**: Maintain an uncut clean capture of the full workflow.
