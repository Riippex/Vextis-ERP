---
name: vextis-vertical-slice
description: Plan and implement one thin end-to-end Vextis business capability across the necessary contracts, Spring core, Agent Runtime, Angular UI, tests, and observability. Use for a feature or workflow that should reach a demonstrable outcome; do not use for broad architecture reviews or unrelated maintenance.
---

# Vextis Vertical Slice

Deliver the smallest complete business outcome that can be demonstrated and verified without creating placeholder layers.

## Load project constraints

Read `docs/TECH_STACK.md`, `docs/REPO_STRUCTURE.md`, `docs/CONTRACTS.md`, and the accepted ADRs relevant to the feature. Read the affected executable contracts and existing code before planning. Apply `vextis-architecture-guardian` invariants throughout the slice.

## Define the slice

Express the outcome as an observable scenario with:

- initiating actor and tenant;
- input or triggering event;
- business result;
- agent autonomy, if any;
- approval or rejection point for sensitive actions;
- evidence visible in the UI, API, event stream, or audit timeline.

Keep one primary happy path and only the failure paths needed for safety, idempotency, or a convincing demo. Defer optional media, Live, infrastructure, and abstractions unless they are part of the requested outcome.

## Implement contract first

1. Identify the owning Enterprise Core module and the minimum public API, agent tool, or event contract required.
2. Change OpenAPI, AsyncAPI, or JSON Schema before consumers when crossing a runtime boundary. Include valid examples and preserve compatibility or version the contract.
3. Implement Enterprise Core domain rules and application use cases. Add persistence and transport adapters only as needed.
4. Implement Agent Runtime orchestration only when the outcome needs model reasoning, asynchronous coordination, retrieval, or agent tools. Use structured Pydantic inputs and outputs.
5. Implement the smallest Angular flow that initiates the action and exposes status, approval, result, and relevant audit evidence.
6. Add infrastructure or configuration only for resources the slice actually exercises.

Never let Agent Runtime write business tables directly, place authorization only in prompts, or let Angular invent business state transitions.

## Make the slice trustworthy

Cover the applicable checks:

- domain and application tests for business invariants;
- contract validation and generated-client consistency;
- integration tests at changed adapters or service boundaries;
- Agent Runtime unit tests and an eval when model behavior affects the outcome;
- Angular behavior for loading, success, rejection, and actionable failure states;
- tenant and actor propagation, correlation IDs, idempotency, authorization, approvals, and audit records;
- reproducible seed data or a documented demo input when the slice is user-facing.

Run the narrowest relevant checks first, then the broader project checks justified by the change. Do not claim an end-to-end result when any required boundary was mocked without saying so.

## Completion report

Summarize:

1. the business outcome now working;
2. the runtimes and contracts changed;
3. the verification run and its result;
4. any mocked or deferred component;
5. the next thinnest slice, without implementing it unless requested.

When asked only to plan a slice, stop after producing acceptance criteria, boundaries, contract impact, ordered implementation work, and verification strategy.
