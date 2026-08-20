---
name: vextis-agent-security-review
description: Review a Vextis agent, workflow, prompt, tool, RAG, memory, Live, or multimodal flow for agent-specific security, privacy, authorization, and governance risks. Use for design or code reviews of AI execution paths; do not substitute it for a general repository vulnerability scan.
---

# Vextis Agent Security Review

Review agentic behavior as an untrusted decision-making layer whose actions must remain constrained by deterministic enterprise controls.

## Establish scope and evidence

Read the relevant parts of `docs/CONTRACTS.md`, `docs/TECH_STACK.md`, accepted ADRs, executable contracts, and affected implementation. Trace the complete path from user or event input through Gemini, ADK, retrieval, memory, tools, Enterprise Core, persistence, and audit output.

Do not report hypothetical findings without identifying the affected component and a plausible path to impact. This workflow covers agent-specific security; use a dedicated security audit when the request concerns general application vulnerabilities.

## Review the trust boundaries

Check the applicable controls:

- **Identity and tenancy:** service identities are authenticated, `agent_id` and tenant context cannot be asserted by an untrusted caller, and tools use least-privilege credentials.
- **Authorization:** Enterprise Core enforces scopes, state transitions, monetary limits, and ownership independently of prompts or model output.
- **Tool safety:** tools are allowlisted, narrowly typed, idempotent where mutating, bounded by timeouts and retries, and do not expose generic SQL, arbitrary URLs, or unrestricted endpoints.
- **Human approval:** sensitive or irreversible actions pause before mutation; approval records bind the exact proposal, actor, evidence, and expiration.
- **Prompt and content isolation:** uploaded documents, retrieved chunks, audio transcripts, and tool responses are treated as data rather than system instructions. Untrusted content is screened when the platform capability exists.
- **Structured output:** model decisions are parsed into strict schemas, validated, and rejected safely when incomplete, malformed, out of policy, or unsupported by evidence.
- **RAG and memory:** authorization filters run before retrieval; tenant data cannot cross boundaries; provenance is retained; memory does not become the source of truth for balances, stock, credit, permissions, or accounting state.
- **Privacy and retention:** secrets and sensitive personal data are minimized, redacted from logs, and retained only under an explicit policy. Raw audio is not persisted by default.
- **Asynchronous execution:** events carry trusted identity and correlation data, consumers deduplicate deliveries, replays cannot repeat mutations, and failed workflows resume from durable state safely.
- **Multimodal outputs:** generated assets record origin and model metadata, are labeled as AI-generated when required, and cannot block or silently alter the commercial transaction.
- **Observability:** logs capture decisions, policy checks, tool calls, approvals, and outcomes without storing secrets or private hidden reasoning traces.

Assume model output, retrieval content, and user-supplied files can be adversarial. A successful prompt must never elevate IAM privileges or bypass Enterprise Core rules.

## Report findings

For each validated issue provide:

1. severity and affected flow;
2. concrete evidence and trust-boundary failure;
3. plausible abuse or failure scenario;
4. business and data impact;
5. the smallest durable remediation;
6. a regression test or eval that proves the control.

Separate confirmed findings from open questions and defense-in-depth recommendations. Highlight safe behavior already present so the review accurately represents residual risk.

When asked only to review, do not change files. When asked to fix issues, preserve Vextis architecture and verify the control at the deterministic boundary rather than relying on stronger prompt wording alone.
