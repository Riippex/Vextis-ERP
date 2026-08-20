---
name: vextis-architecture-guardian
description: Review, plan, or implement Vextis changes that affect service boundaries, domain ownership, APIs, events, persistence, security, or deployment. Use when adding or changing modules, endpoints, tools, workflows, data access, integrations, or cross-service behavior; skip for isolated copy or formatting edits.
---

# Vextis Architecture Guardian

Protect Vextis's accepted architecture while allowing deliberate evolution.

## Establish the current decision

Before acting, read the relevant repository sources of truth:

1. `docs/adr/`, prioritizing the newest accepted ADR that covers the change.
2. `docs/TECH_STACK.md` for runtime responsibilities and platform choices.
3. `docs/REPO_STRUCTURE.md` for dependency and module boundaries.
4. `docs/CONTRACTS.md` plus affected files under `contracts/` for integration invariants.

Read only the sections and executable contracts needed for the task. Inspect the affected implementation and tests before drawing conclusions.

## Preserve these invariants

- Angular calls the Enterprise Core public API. It does not access Gemini, Pub/Sub, or PostgreSQL directly.
- Java/Spring Enterprise Core is the sole authority for CRM, inventory, credit, order, invoice, approval, and audit mutations.
- Python Agent Runtime coordinates agents, Gemini, ADK, RAG, memory, and workflows. It mutates business state only through authenticated Enterprise Core tools.
- Agent tools, prompts, RAG, and memory do not duplicate business rules or become alternate sources of transactional truth.
- Cross-runtime integration is defined by versioned OpenAPI, AsyncAPI, or JSON Schema contracts rather than shared implementation libraries.
- Module-owned data is accessed through public use cases or domain events, not another module's internal tables.
- Agent-initiated mutations use the same authorization, validation, approval, idempotency, and audit path as human mutations.
- Optional Live, image, and video capabilities reuse existing commands and policies and remain removable behind feature flags.

Treat the delivery fallback in `docs/TECH_STACK.md` as a deliberate product decision, not permission to collapse boundaries silently.

## Review or implementation workflow

1. Identify the requested behavior, affected runtime or module, data owner, callers, and trust boundary.
2. State the expected dependency direction before changing code.
3. Check whether the change affects an API, event, schema, persistence ownership, IAM identity, approval, or idempotency behavior.
4. For cross-boundary behavior, update the executable contract, examples, consumer, and contract tests together. Do not hand-edit generated clients.
5. Keep domain decisions in Enterprise Core application or domain code. Keep transport and provider concerns in adapters.
6. Add or update verification proportional to the risk, including architecture tests when a dependency rule could regress.
7. Report the boundary preserved, contract impact, verification performed, and any unresolved architectural decision.

If the request conflicts with an accepted ADR, do not create a hidden exception. Explain the conflict and ask for a decision when changing direction requires product authority. If the user deliberately changes the architecture, record a superseding ADR and update the affected sources of truth in the same change.

When reviewing only, do not modify files unless the user also asks for implementation.
