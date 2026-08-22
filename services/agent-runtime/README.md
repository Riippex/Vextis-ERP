# Agent Runtime

Python service with Google ADK for multi-agent coordination, Gemini, RAG, memory, evaluation, and workflow resumption.

Contains:

- Coordinator Agent.
- CRM Agent.
- Inventory Agent.
- Billing Agent.
- Workflows, tools, policies, RAG, memory, and evals.

Consumes Pub/Sub and calls Enterprise Core's authenticated API. Has no direct write permissions on business tables.

## Pub/Sub push consumer

The push route is disabled by default. Enable it with:

```text
VEXTIS_PUBSUB_PUSH_ENABLED=true
VEXTIS_ENTERPRISE_CORE_URL=<enterprise-core-url>
VEXTIS_AGENT_TOOLS_TOKEN=<secret-shared-with-enterprise-core>
VEXTIS_COORDINATOR_AGENT_ID=coordinator-agent
```

In Cloud Run, keep the service unauthenticated setting disabled. Configure the Pub/Sub push subscription with a dedicated service account and OIDC token that has only `roles/run.invoker` on Agent Runtime. The application-level service token must come from Secret Manager and protects calls from Agent Runtime back to Enterprise Core.
