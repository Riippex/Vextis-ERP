# Vextis delivery roadmap and AI handoff

Last verified: **August 26, 2026 (America/Bogota)**

Owner: **Rafael Patiño Díaz**

Primary hackathon category: **Fortified Enterprise Fleet**

## 1. Purpose

This is the single execution-level source of truth for what Vextis has delivered,
what is being integrated, and what should be built next. It is written so the
owner, Codex, Claude, or another coding agent can resume work without relying on
chat history.

This document coordinates delivery; it does not override architecture or
contracts. When sources disagree, use this order:

1. the newest accepted decision in [`adr/`](./adr/);
2. [`TECH_STACK.md`](./TECH_STACK.md);
3. [`REPO_STRUCTURE.md`](./REPO_STRUCTURE.md);
4. [`CONTRACTS.md`](./CONTRACTS.md) and executable files under `contracts/`;
5. this roadmap for sequencing and current status.

## 2. Product objective and scope

Vextis is one agentic CRM/ERP experience spanning three departments:

- CRM and Sales;
- Inventory and Operations;
- Finance and Billing.

The product combines collaborative assistance, asynchronous task execution, and
enterprise agent governance. The official entry category is Fortified Enterprise
Fleet, while the same software demonstrates capabilities from all three tracks.

The hackathon architecture has three deployable applications, not one backend per
department:

```text
Angular Web -> Enterprise Core (Java/Spring) <- Agent Runtime (Python/ADK)
                    |                                  |
                    v                                  v
            Cloud SQL PostgreSQL              Gemini / Vertex AI
                    |
                    +-> transactional outbox -> Pub/Sub
```

Non-negotiable ownership rules:

- Angular calls Enterprise Core GraphQL and never calls Gemini or PostgreSQL.
- Enterprise Core owns every CRM, inventory, finance, approval, audit, and
  transactional decision.
- Agent Runtime coordinates Gemini, ADK agents, RAG, memory, and Live sessions.
  It changes business state only through authenticated Enterprise Core tools.
- Memory and RAG are context, never transactional truth or authorization.
- GraphQL, OpenAPI, AsyncAPI, and JSON Schema are the cross-runtime contracts.

## 3. Current release state

| Branch/environment | State | Meaning |
| --- | --- | --- |
| `main` | Agent fleet release promoted by [PR #39](https://github.com/Riippex/Vextis-ERP/pull/39) | This is the only branch from which selective delivery may deploy. |
| `develop` | Governed durable memory merged by [PR #40](https://github.com/Riippex/Vextis-ERP/pull/40); all CI checks passed | Memory code is integrated but has not yet been promoted from `develop` to `main`. |
| Task branches | Short-lived branches only | Every completed task opens or updates a PR targeting `develop`. |
| GCP `hackathon` environment | Exact deployed revisions were not reverified for this documentation change | Check component delivery runs and live service revisions before claiming a feature is deployed. |
| GCP environments | One `hackathon` environment | A separate cloud development environment is intentionally out of scope. |

Terraform state remains local and infrastructure applies remain manual. CI must
not become a second Terraform authority until remote state and locking exist.

## 4. Delivered capability bundles

### Platform and delivery foundation — complete

- Java 21/Spring Boot modular Enterprise Core, Python 3.13/Google ADK Agent
  Runtime, Angular, PostgreSQL, GraphQL, internal OpenAPI, and Pub/Sub contracts.
- Firebase Authentication and separate public/internal Core Cloud Run exposure.
- Cloud SQL, Secret Manager, Cloud Storage, Pub/Sub, service accounts, Cloud Run,
  Firebase Hosting, Terraform, and selective component delivery.
- Branch flow and review process documented in
  [`runbooks/pull-requests.md`](./runbooks/pull-requests.md).

### Operational ERP vertical slice — complete

- Mission Control and department workspaces for all three departments.
- Customer, inventory availability, and credit-profile administration.
- Purchase-order document upload to private Cloud Storage.
- Durable purchase-order execution, planning, readiness checks, approval,
  inventory reservation, completion, timeline, and audit.
- Human approval and idempotent, authenticated business mutation paths.

Key integration PRs: [#13](https://github.com/Riippex/Vextis-ERP/pull/13),
[#14](https://github.com/Riippex/Vextis-ERP/pull/14),
[#15](https://github.com/Riippex/Vextis-ERP/pull/15),
[#18](https://github.com/Riippex/Vextis-ERP/pull/18),
[#19](https://github.com/Riippex/Vextis-ERP/pull/19), and
[#31](https://github.com/Riippex/Vextis-ERP/pull/31).

### Ask Vextis and governed agent fleet — complete

- Text chat backed by the same coordinator and specialist agents used by the
  workflow runtime.
- Real ADK coordinator with CRM, Inventory, and Billing specialists.
- Tenant-scoped specialist read tools backed by Enterprise Core.
- Visible agent registry with version, model, prompt, capabilities, runtime
  identity, lifecycle status, and allowed tools.
- Deterministic tool policy enforcement, denied-attempt audit, and visible,
  Core-validated delegation evidence.

Key integration PRs: [#21](https://github.com/Riippex/Vextis-ERP/pull/21),
[#33](https://github.com/Riippex/Vextis-ERP/pull/33),
[#34](https://github.com/Riippex/Vextis-ERP/pull/34),
[#35](https://github.com/Riippex/Vextis-ERP/pull/35),
[#36](https://github.com/Riippex/Vextis-ERP/pull/36),
[#37](https://github.com/Riippex/Vextis-ERP/pull/37), and
[#38](https://github.com/Riippex/Vextis-ERP/pull/38).

### Governed durable preference memory — integrated in `develop`

- Vertex AI Agent Engine Memory Bank adapter in Agent Runtime.
- Tenant-and-actor pseudonymous scopes; raw Firebase UIDs are not sent as
  Memory Bank user IDs.
- Strict storage policy limited to language, response style, and default
  workspace preferences.
- Strict retrieval allowlist prevents arbitrary stored content from entering a
  prompt.
- Retrieval is bounded and injected as untrusted context, never business truth.
- Core validates and persists bounded evidence; Angular displays whether memory
  was applied, stored, or unavailable without exposing preference text.
- The feature remains disabled when no Agent Engine ID is configured.

Integration and evidence: [PR #40](https://github.com/Riippex/Vextis-ERP/pull/40).

## 5. Ordered remaining bundles

Work from the first `READY` bundle unless the owner explicitly reprioritizes it.
Do not silently start an optional bonus while a submission blocker remains.

### B10 — Activate and prove Memory Bank

Status: **READY, requires owner approval for the billable GCP mutation**

Priority: **P0**

Outcome:

- Promote the default-off memory implementation to `main` through a release PR.
- Provision or select the Vertex AI Agent Engine resource used by Memory Bank.
- Set `memory_bank_agent_engine_id` through the reviewed Terraform workflow and
  apply it manually from the authoritative local state.
- Demonstrate that an allowed preference survives a new conversation and that
  forbidden business facts are rejected.

Definition of done:

- Agent Runtime revision is healthy with memory enabled.
- A user stores an allowed preference, starts another conversation, and sees the
  preference applied plus visible Memory Bank evidence.
- An attempted stock, credit, balance, permission, or secret memory is rejected.
- Retrieval failure is visible but does not break normal chat; explicit write
  failure does not claim success.
- Cost impact and exact Terraform variable are recorded in the PR.

Do not create the Agent Engine or run `terraform apply` without explicit owner
approval because both change external state and can consume credits.

### B11 — Governed document RAG

Status: **PLANNED**

Priority: **P0**

Depends on: stable document upload and current Core/Agent authentication

Outcome:

- Ingest approved documents from Cloud Storage into tenant-scoped chunks and
  embeddings.
- Retrieve document evidence for Ask Vextis and workflow planning with source
  attribution.
- Keep document text untrusted and preserve Enterprise Core as business truth.

Definition of done:

- Metadata, hashes, versions, ACL/tenant scope, chunks, and embeddings have an
  explicit owner and migration.
- Re-ingesting the same document is idempotent.
- Retrieval cannot cross tenants and never grants tool permission.
- Answers show useful source evidence and distinguish retrieved text from live
  ERP facts.
- Prompt-injection and malformed-document tests pass.
- Model Armor is integrated if available; otherwise the documented fallback is
  explicit and does not weaken tool authorization.

### B12 — Deterministic demo data, evaluations, and observability

Status: **PLANNED**

Priority: **P0**

Outcome:

- Make the complete three-department scenario reproducible for judges.
- Prove agent delegation, policy denial, human approval, audit, memory, and RAG
  with correlated evidence.

Definition of done:

- One command or documented sequence seeds the demo scenario safely.
- Correlation ID follows browser, Core, Pub/Sub, coordinator, specialist, tool,
  and audit records.
- Evals cover plan structure, correct delegation, unsafe-tool denial, grounded
  answers, and memory boundaries.
- Cloud Logging/Monitoring exposes failures and latency without secrets or raw
  hidden reasoning.
- A smoke-test checklist verifies Firebase, both Core services, Agent Runtime,
  Cloud SQL connectivity, Pub/Sub delivery, and the primary user journey.

### B13 — Gemini Live conversation

Status: **PLANNED**

Priority: **P1 bonus after P0 demo blockers**

Outcome:

- Enable low-latency voice conversation from Angular through the existing Live
  session authorization flow.
- Reuse the same coordinator, tools, approvals, idempotency, and audit as text.

Definition of done:

- Enterprise Core issues short-lived auditable session credentials.
- The browser receives no permanent Vertex AI credential.
- Session expiry and close work; reconnect behavior is explicit.
- Sensitive mutations still pause for human approval.
- Audio retention and consent behavior are visible and documented.

### B14 — Imagen quote/proposal asset

Status: **PLANNED**

Priority: **P1 bonus**

Outcome:

- Generate one user-requested visual for an approved CRM/Sales quote or proposal
  using a Vertex AI image model.
- Store the asset in Cloud Storage and register its relationship in Core.

Definition of done:

- Generation is explicit, feature-flagged, asynchronous where appropriate, and
  does not block the business transaction.
- The asset records model, originating quote, actor, redacted prompt, and AI
  generation label.
- Storage and registration failures are visible and safely retryable.

Veo remains optional after Imagen. It must reuse the same proposal-asset boundary
and must not introduce a fourth Marketing department.

### B15 — Submission and presentation package

Status: **PLANNED**

Priority: **P0 before submission**

Outcome:

- Produce the final demo script, architecture story, evidence checklist, video,
  screenshots, README polish, and pitch deck.

Definition of done:

- The demo shows the three departments acting as one workflow.
- Gemini and Google ADK usage are visible and technically accurate.
- The fleet story includes an allowed tool call, denied tool call, approval,
  registry identity, durable audit, and correlation evidence.
- Memory and RAG are demonstrated without claiming they are business truth.
- Deployment URLs, test credentials, backup recording, and recovery steps are
  verified immediately before submission.
- Final requirements are rechecked against the official hackathon page rather
  than copied from an old conversation.

## 6. MVP completion gate

The hackathon MVP is complete only when all of these are demonstrable in the
deployed `hackathon` environment:

- authenticated Angular entry and stable workspace navigation;
- useful views for CRM/Sales, Inventory/Operations, and Finance/Billing;
- purchase-order document intake and a durable end-to-end execution;
- real specialist delegation across the three departments;
- deterministic tool authorization and at least one visible denial;
- human approval before a sensitive action;
- inventory reservation and completed workflow state;
- durable audit and correlation evidence;
- grounded Ask Vextis answers from live Core data;
- cross-conversation preference memory;
- document RAG with tenant boundaries and source evidence;
- repeatable seed/demo data and passing CI;
- a recorded, rehearsed submission story.

Live audio, Imagen, and Veo improve bonus coverage but do not replace a missing
P0 gate.

## 7. Known constraints and risks

| Risk/constraint | Current control | Next action |
| --- | --- | --- |
| Memory Bank resource is not provisioned | Feature defaults off and never claims a write | B10 with owner approval |
| Terraform state is local | Manual apply is the only infrastructure authority | Keep CI validate-only; migrate later with a reviewed ADR/runbook |
| One cloud environment | `develop` validates but only `main` deploys | Accept for hackathon; add environments after submission |
| Tenant membership is not modeled | Server binds the hackathon user to `demo-tenant` | Do not claim production multi-tenancy; model membership post-hackathon |
| Untrusted documents can contain instructions | Core policies remain authoritative; document text is data | B11 RAG filtering, ACL, tests, and Model Armor/fallback |
| Optional model features can burn credits | Feature flags and zero-resource defaults | Perform cost review before B10, B13, B14, or Veo |
| Demo depends on external services | Durable state, idempotency, selective redeploy, runbooks | B12 smoke tests and B15 backup recording |

## 8. How any AI should resume work

1. Read this file, the applicable ADRs, architecture sources, and
   [`runbooks/pull-requests.md`](./runbooks/pull-requests.md).
2. Run `git status` before changing anything. Preserve unrelated dirty files and
   never commit secrets, generated build output, local state, or another agent's
   changes.
3. Verify GitHub PR and CI state; this document is a snapshot, not a substitute
   for checking live status.
4. Select the first ready bundle and implement the thinnest end-to-end slice that
   satisfies its definition of done.
5. State the dependency direction and data owner before a cross-service change.
6. Update executable contract, consumer, generated client, tests, and docs
   together when a boundary changes.
7. Validate proportionally across Angular, Core, Agent Runtime, contracts, and
   Terraform.
8. Commit only task files on a short-lived branch, push it, and open or update a
   PR targeting `develop`.
9. Update this roadmap in the same PR when a bundle changes state, scope,
   dependency, or evidence. Link the PR after it is known.
10. Never merge, approve, promote, provision billable infrastructure, apply
    Terraform, or deploy without the level of owner authorization required by
    the runbooks.

## 9. Status vocabulary

- **PLANNED:** defined but blocked by earlier priorities or dependencies.
- **READY:** dependencies are satisfied; work may start within existing authority.
- **IN PROGRESS:** a named branch or PR is actively implementing the bundle.
- **INTEGRATED:** merged into `develop`, not necessarily deployed.
- **RELEASED:** promoted through `main` and delivery/smoke evidence is green.
- **BLOCKED:** a concrete external decision or dependency prevents progress.

Avoid marking a bundle complete because code exists locally. Integration,
release, and deployed proof are separate states.
