# Agent Runtime

Python service with Google ADK for multi-agent coordination, Gemini, RAG, memory, evaluation, and workflow resumption.

Contains:

- Coordinator Agent with ADK-native delegation.
- CRM and Sales specialist Agent.
- Inventory and Operations specialist Agent.
- Finance and Billing specialist Agent.
- Workflows, tools, policies, RAG, memory, and evals.

Consumes Pub/Sub and calls Enterprise Core's authenticated API. Has no direct write permissions on business tables.

The three specialists are registered as real ADK subagents of the coordinator and are shared by
text chat and Live sessions. Each receives one narrow, tenant-bound read tool backed by Enterprise
Core: exact customer lookup, SKU availability, or customer credit status. Missing records are
reported explicitly and the specialists cannot claim a mutation occurred. Transactional workflow
actions continue through authenticated Core tools, human approval, idempotency, and audit.

## Pub/Sub push consumer

The push route is disabled by default. Enable it with:

```text
VEXTIS_PUBSUB_PUSH_ENABLED=true
VEXTIS_ENTERPRISE_CORE_URL=<enterprise-core-url>
VEXTIS_AGENT_TOOLS_TOKEN=<secret-shared-with-enterprise-core>
VEXTIS_COORDINATOR_AGENT_ID=coordinator-agent
VEXTIS_GEMINI_MODEL=gemini-3.5-flash
VEXTIS_GEMINI_LOCATION=us
VEXTIS_LIVE_MODEL=gemini-live-2.5-flash-native-audio
VEXTIS_LIVE_LOCATION=us-central1
GOOGLE_CLOUD_PROJECT=<project-id>
GOOGLE_CLOUD_LOCATION=us-central1
GOOGLE_GENAI_USE_VERTEXAI=true
```

`VEXTIS_COORDINATOR_AGENT_ID` identifies the authenticated Agent Runtime service for Live-session
validation. Business tools additionally send a delegated logical identity (`vextis_coordinator`,
`vextis_crm_agent`, `vextis_inventory_agent`, or `vextis_billing_agent` by default), and Enterprise
Core enforces the active registry entry's exact `allowed_tools` policy before invoking a use case.

For the purchase-order slice, Google ADK sends the Cloud Storage PDF to Gemini as untrusted multimodal data and enforces a strict Pydantic output schema. The validated proposal is then recorded through Enterprise Core's authenticated `record_plan` tool; Agent Runtime never writes workflow tables directly.

The schema extracts only explicit SKU, quantity, and requested payment-term facts. Enterprise Core then evaluates CRM, stock, and credit readiness from tenant-scoped records and persists that evidence; Agent Runtime cannot assert those authoritative outcomes itself.

In Cloud Run, keep the service unauthenticated setting disabled. Configure the Pub/Sub push subscription with a dedicated service account and OIDC token that has only `roles/run.invoker` on Agent Runtime. The application-level service token must come from Secret Manager and protects calls from Agent Runtime back to Enterprise Core.

## Durable preference memory

Ask Vextis can use Vertex AI Agent Engine Memory Bank for explicit language, response-style, and
default-workspace preferences. Enable it only after an Agent Engine exists in the configured
project and region:

```text
VEXTIS_MEMORY_BANK_ENABLED=true
VEXTIS_MEMORY_BANK_AGENT_ENGINE_ID=<agent-engine-resource-id>
VEXTIS_MEMORY_BANK_LOCATION=us-central1
```

Each Memory Bank user scope is a SHA-256 pseudonym derived from tenant and authenticated actor.
The service stores no raw Firebase UID, conversation transcript, secret, stock, credit, permission,
balance, or accounting fact. Retrieval is capped at five 500-character preference snippets and is
injected as untrusted context. Normal retrieval failures fail open and are disclosed as bounded
evidence; an explicit preference write fails closed instead of claiming it was stored.

## Knowledge base: embeddings and ingestion

Documents and queries are pinned to one embedding space, identified as
`provider:model:dimension` and stored on every chunk. Enterprise Core only
compares vectors within a space, so a Vertex query cannot be answered with
mock-embedded chunks and vice versa.

```text
VEXTIS_RAG_EMBEDDING_MODEL=text-embedding-004
VEXTIS_RAG_EMBEDDING_DIMENSION=768
VEXTIS_RAG_EMBEDDING_LOCATION=us-central1
VEXTIS_RAG_MIN_SIMILARITY=0.55
VEXTIS_RAG_MOCK_EMBEDDINGS_ENABLED=false
```

A Vertex embedding failure raises; it never degrades to the deterministic
SHA-256 mock. The mock is reachable only through
`VEXTIS_RAG_MOCK_EMBEDDINGS_ENABLED=true`, which exists for offline tests and
local runs, and it declares its own space so nothing it writes can be confused
with real content. With neither Vertex nor the flag configured, the coordinator
omits `search_knowledge_base` rather than answering from an empty index.

### Ingesting a document

Ingestion is a command, not a product feature: there is no upload UI and no
public GraphQL mutation. The command chunks and embeds the file locally with the
same embedder the agents query with, then posts it to Enterprise Core, which
enforces the tenant and the `ingest_knowledge_document` allowlist entry on the
`vextis_document_ingestor` registry agent before writing anything.

```bash
uv run python -m vextis_agents.rag.ingest \
  --tenant demo-tenant \
  --document-uri urn:vextis:policy:commercial \
  --file ../../docs/commercial_policy.md
```

Re-running it with unchanged content is idempotent; changed content stores a new
version and replaces the previous chunks.

## Tests and evaluations

```bash
uv run pytest          # deterministic suites, what CI gates on
uv run ruff check src tests
uv run mypy src tests
```

`tests/unit` and the flat files under `tests/evals` are deterministic contract
checks — prompts, schemas, tool wiring, sanitisation — and call no model.

`tests/evals/behavior` holds the behavioural evaluations: they build the real
coordinator, call a real Vertex model, and assert on what it does for grounded
RAG, prompt injection, delegation, and policy denial. Enterprise Core is stubbed
per scenario so a run is reproducible and costs only model tokens.

They are marked `model_eval` and deselected by default, because they need Vertex
credentials and are non-deterministic. Run them explicitly:

```bash
GOOGLE_CLOUD_PROJECT=<project-id> \
VEXTIS_GEMINI_MODEL=gemini-3.5-flash \
GOOGLE_GENAI_USE_VERTEXAI=true \
uv run pytest -m model_eval
```

Without those variables the suite skips with an explicit reason rather than
reporting a pass.
