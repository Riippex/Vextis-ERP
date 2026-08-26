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
