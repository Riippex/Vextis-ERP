# Agent Runtime

Python service with Google ADK for multi-agent coordination, Gemini, RAG, memory, evaluation, and workflow resumption.

Contains:

- Coordinator Agent.
- CRM Agent.
- Inventory Agent.
- Billing Agent.
- Workflows, tools, policies, RAG, memory, and evals.

Consumes Pub/Sub and calls Enterprise Core's authenticated API. Has no direct write permissions on business tables.
