# Technical contracts — Vextis

This document defines the stable boundaries between Angular, Enterprise Core, and Agent Runtime. Executable contracts live in `contracts/` as GraphQL SDL, OpenAPI, AsyncAPI, and JSON Schema; this file explains their invariants and ownership.

Status: **decision in effect since August 19, 2026**.

## 1. Authority and dependencies

```text
Angular ───────> Enterprise Core Java <────── Agent Runtime Python
                         |
                         v
                  Cloud SQL PostgreSQL

Enterprise Core ──outbox──> Pub/Sub ──> Agent Runtime
```

- Enterprise Core is the sole authority for CRM, inventory, credit, order, and invoice mutations.
- Agent Runtime never receives write credentials on business tables.
- Angular never calls Gemini, Pub/Sub, or PostgreSQL directly.
- Angular only opens a Live channel with Agent Runtime after Enterprise Core authorizes a short, auditable session; it never receives Vertex AI credentials.
- RAG retrieves evidence; it does not execute business rules.
- Memory Bank holds preferences and agentive context, never balances, stock, or accounting state.

## 2. Data ownership

A single PostgreSQL instance is enough for the hackathon. Each module logically owns its tables:

| Prefix | Owner | Content |
|---|---|---|
| `crm_*` | CRM/Sales | customers, contacts, opportunities, quotes, and commercial terms |
| `inventory_*` | Inventory/Operations | products, aliases, substitutes, stock, reservations, and movements |
| `billing_*` | Finance/Billing | credit, invoices, taxes, and collection status |
| `workflow_*` | Workflow | executions, steps, approvals, and idempotency |
| `audit_*` | Audit | human, agent, and tool actions |
| `agent_*` | Agent governance | registry, versions, capabilities, and policies |
| `rag_*` | Retrieval | documents, chunks, embeddings, ACL, hashes, and versions |
| `outbox_*` | Integration | pending, published, and failed events |

A Java module does not query another module's internal tables directly. Integration happens through public use cases or domain events.

## 3. Minimum aggregates

### CRM/Sales

- `Customer`: identity, contacts, terms, and commercial preferences.
- `Opportunity`: stage and potential value.
- `Quote`: lines, prices, discounts, validity, and status.
- `SalesOrder`: confirmed lines, total, and references to reservation and invoice.

### Inventory/Operations

- `Product`: SKU, aliases, reference price, and substitutes.
- `Stock`: available, reserved, and concurrency version.
- `Reservation`: order, SKU, quantity, and status.

### Finance/Billing

- `CreditAccount`: limit, used, and available.
- `Invoice`: order, customer, subtotal, taxes, total, and status.

### Workflow

- `Execution`: goal, status, current step, and correlation ID.
- `Approval`: proposed option, evidence, decision, actor, and timestamps.
- `IdempotencyRecord`: key, operation, result, and optional expiration.

IDs are stable UUID/ULID. Amounts use decimal and ISO 4217 currency; never `float`.

## 4. States and transitions

```text
RECEIVED -> PLANNING -> RUNNING -> COMPLETED
                           |
                           v
                   WAITING_APPROVAL
                           |
                    approve/reject
                           |
                  RUNNING / FAILED

RECEIVED, PLANNING, and RUNNING can move to FAILED.
FAILED can only be retried toward RUNNING through an explicit, idempotent command.
```

States are never invented from the UI or prompts. An invalid transition is rejected by Enterprise Core.

## 5. APIs

### Public API — Angular to Enterprise Core

Executable source: `contracts/graphql/public-api.graphqls`.

The public API uses specific queries and mutations. Resolvers are adapters to Enterprise Core use cases: they contain no business rules and do not authorize on their own. Pagination and complexity/depth limits will be required before exposing collections.

Minimum resources:

- purchase orders;
- customers, opportunities, and quotes;
- products, stock, and reservations;
- invoices and credit;
- executions, timeline, and results;
- approvals and decisions;
- visible registry and agent audit;
- authorization, query, and closing of Live sessions.

Document ingestion is **not** part of the public API. There is no upload
mutation and no upload UI: a document enters the knowledge base only through the
internal tools API below, driven by an operator running
`python -m vextis_agents.rag.ingest`. Read paths (`knowledgeDocuments`) are
public; write paths are not.

### Chat completion API â€” Enterprise Core to Agent Runtime

Executable source: `contracts/openapi/agent-runtime-api.yaml`.

Enterprise Core sends a tenant-scoped conversation turn to Agent Runtime and receives the assistant
reply plus bounded public activity evidence. Runtime may report at most four logical agent IDs and
eight tool names per agent. It never returns prompts, tool arguments, tool results, credentials, or
hidden model reasoning through this contract.

For follow-up continuity, Enterprise Core may include up to twelve prior turns from the same
tenant-scoped conversation, capped at 4,000 characters per turn and 12,000 characters total.
Agent Runtime uses this ephemeral history only to resolve conversational references. It treats the
content as untrusted data, does not persist it, and never accepts it as authorization or evidence for
current stock, credit, permissions, orders, quotes, or accounting facts; those still require an
authorized Core tool lookup.

Runtime activity claims are not authoritative. Before storing or exposing them, Enterprise Core
requires an active tenant registry entry, verifies that its service identity matches the trusted
coordinator, intersects tool names with that version's `allowed_tools`, and snapshots the registry
version, display name, model, and prompt version alongside the assistant message. Unknown agents,
untrusted identities, and unapproved tools are discarded. This evidence is informational and grants
no permission to execute a tool.

The request also carries the authenticated actor ID from Enterprise Core. Agent Runtime derives a
tenant-and-actor pseudonymous Memory Bank scope and may store only explicit language,
response-style, or default-workspace preferences. Retrieved values are bounded untrusted context,
never authorization or business evidence. The response exposes only provider availability, the
number of preferences applied, and whether a preference was durably stored; Enterprise Core
validates and snapshots that evidence with the assistant message. It never persists or returns the
preference text through the public conversation contract.

### Tools API â€” Agent Runtime to Enterprise Core

Executable source: `contracts/openapi/agent-tools-api.yaml`.

Every call includes the authenticated service credential, delegated logical `agent_id`,
`correlation_id`, and `idempotency_key` when mutating state. The service credential authenticates
Agent Runtime as `coordinator-agent`; `X-Agent-Id` does not replace that credential and identifies
the active logical registry entry whose exact `allowed_tools` policy Enterprise Core enforces.

The first specialist read slice exposes exact, tenant-scoped lookups for customer legal name,
SKU availability, and customer credit status. These GET operations are bound to the trusted tenant
before ADK receives them, use the coordinator's service identity, and never mutate business state.

**CRM Agent**

- `get_customer(customer_id)`
- `get_customer_context(customer_id)`
- `create_quote(customer_id, lines, idempotency_key)`
- `convert_quote_to_order(quote_id, idempotency_key)`

**Inventory Agent**

- `search_products(query, limit)`
- `check_stock(sku, quantity)`
- `reserve_stock(order_id, sku, quantity, idempotency_key)`
- `find_substitutes(sku, quantity)`

**Billing Agent**

- `get_credit_status(customer_id)`
- `validate_commercial_terms(customer_id, order_id)`
- `create_invoice(order_id, idempotency_key)`
- `get_payment_status(invoice_id)`

**Coordinator**

- `create_execution(source_document_id, idempotency_key)`
- `record_plan(execution_id, structured_plan)`
- `request_approval(execution_id, proposal, evidence, idempotency_key)`
- `record_step_result(execution_id, step_id, result, idempotency_key)`

**Media/Proposal Tool**

- `register_quote_asset(quote_id, storage_uri, media_type, model_id, idempotency_key)`

Imagen and Veo are invoked from Agent Runtime with its service identity. The file is saved to Cloud Storage, and only then does Enterprise Core register the asset against the quote. Failing to generate or register an image or video does not roll back or block the business transaction.

### Knowledge base — embedding space

Every stored chunk records the `provider:model:dimension` that produced its
vector, and `POST /internal/agent-tools/v1/rag/search` requires the same
`embeddingSpace` on the query. Enterprise Core compares vectors only within one
space, so a query embedded by one model can never be answered with chunks
embedded by another. `POST /internal/agent-tools/v1/rag/documents` is the only
write path into the index; it requires the `ingest_knowledge_document` tool,
which is granted to `vextis_document_ingestor` and to no retrieval agent.

Similarity is floored at `vextis.rag.min-similarity` (0.55 by default). A caller
may raise that floor and may not remove it: an unfiltered nearest-neighbour list
reads downstream as if every chunk it returns were relevant evidence.

### Live Session

1. Angular requests a session from Enterprise Core.
2. Enterprise Core validates user, tenant, and permissions, creates the auditable record, and returns an ephemeral credential for Agent Runtime.
3. Angular opens the audio channel with Agent Runtime; it does not connect directly to Vertex AI with permanent credentials.
4. Agent Runtime uses Gemini Live and translates intents into the same authenticated tools defined above.
5. Sensitive actions still require approval, and mutable actions still require an idempotency key.
6. On session close or expiry, only the transcript and metadata allowed by the privacy policy are persisted.

Agents do not receive generic tools such as `execute_sql`, `update_record`, or `call_any_endpoint`.

## 6. Events

Executable source: `contracts/events/asyncapi.yaml` and `contracts/events/schemas/*.json`.

Required envelope:

```json
{
  "event_id": "01J...",
  "event_type": "purchase_order.received",
  "event_version": 1,
  "occurred_at": "2026-08-19T20:00:00Z",
  "producer": "enterprise-core",
  "tenant_id": "demo-tenant",
  "correlation_id": "01J...",
  "causation_id": "01J...",
  "actor": {
    "type": "USER|AGENT|SYSTEM",
    "id": "inventory-agent"
  },
  "payload": {}
}
```

Initial events:

- `purchase_order.received.v1`
- `workflow.execution.started.v1`
- `workflow.step.completed.v1`
- `workflow.approval.requested.v1`
- `workflow.approval.decided.v1`
- `inventory.reservation.created.v1`
- `inventory.exception.detected.v1`
- `billing.invoice.issued.v1`
- `quote.visual.generated.v1`
- `quote.video.generated.v1`
- `live.session.started.v1`
- `live.session.ended.v1`
- `workflow.execution.completed.v1`
- `workflow.execution.failed.v1`

The logical name inside `event_type` does not carry the version suffix; the version travels in `event_version`. Schema files do include `.v1`.

## 7. Idempotency and reliable publishing

### Mutations

1. The consumer sends a stable `idempotency_key`.
2. Enterprise Core opens a PostgreSQL transaction.
3. It attempts to insert the key under a `UNIQUE(tenant_id, operation, idempotency_key)` constraint.
4. If it already exists, it returns the stored result without repeating the operation.
5. If new, it validates rules, executes the mutation, and persists the result and idempotency record in the same transaction.

### Events

The business mutation and the record in `outbox_events` happen in the same transaction. An independent publisher sends the event to Pub/Sub and marks the outbox as published. Pub/Sub is treated as at-least-once delivery; consumers deduplicate by `event_id`.

Distributed exactly-once is not promised.

## 8. Fleet governance

Each agent registers:

- `agent_id`, name, and version;
- purpose and capabilities;
- allowed tools;
- scopes and monetary limits;
- prompt/policy version;
- deployment status;
- effective service identity or verifiable delegated identity.

The registry is descriptive to users, while Enterprise Core consumes its active status,
effective service identity, and exact `allowed_tools` values as deterministic policy. A registry
row alone grants no permissions: the caller must first authenticate as the configured Agent Runtime
service, belong to the server-bound tenant, and request a tool present in the active logical agent's
allowlist.

The hackathon registry is persisted in `agent_registry_entries` and exposed read-only through
Mission Control. It records the approved ADK agent ID and version, department, purpose,
capabilities, allowed tools, model, prompt version, lifecycle status, and effective service
identity. Only one active version may exist for a logical agent and tenant.

Execution detail exposes the durable `audit_records` associated with its tenant and correlation
ID. Agent-authored records are enriched for display with the current active registry entry, while
the stored actor, action, resource, result, timestamp, and correlation remain the authoritative
evidence. Registry enrichment never changes the historical audit record.

An authenticated workflow-tool call made by an agent outside the configured scope is rejected
before any business use case runs. Core persists a `DENIED` audit record only after binding the
provided tenant, execution ID, and correlation ID to an existing execution. Invalid credentials,
foreign tenants, and forged correlation IDs are rejected without writing untrusted functional
audit data.

The demo must show at least:

1. An allowed action executed by the correct agent.
2. An out-of-scope action rejected by policy.
3. A sensitive action paused for human approval.
4. Audit trail with agent, tool, policy, result, and correlation ID.

## 9. Content security

- External files are saved to Cloud Storage first.
- Model Armor inspects untrusted content before sending it to the model, once the integration is available.
- Document text is treated as data, never as system instructions.
- Logs and audit redact secrets and PII as defined by policy.
- Prompts cannot elevate permissions or change Enterprise Core's limits.
- Raw audio is not persisted by default; retention and consent must be explicit.
- Every generated visual is labeled as AI-generated content and records the model, redacted prompt, user, and originating quote.

## 10. Versioning and Clean Code

- GraphQL SDL, OpenAPI, AsyncAPI, and JSON Schema are validated in CI.
- TypeScript operations/types and Python clients are generated; they are not edited by hand.
- Incompatible changes create a new contract version.
- Transport DTOs are not reused as domain entities.
- Use cases depend on ports; infrastructure implements adapters.
- There is no `shared` library between Java, Python, and TypeScript. Contracts are shared, not implementation.
- Any contract change updates the schema, examples, consumer, and test in the same change.
