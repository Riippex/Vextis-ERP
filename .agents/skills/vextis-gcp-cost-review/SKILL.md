---
name: vextis-gcp-cost-review
description: Estimate and reduce Google Cloud cost and credit-burn risk for Vextis by reviewing infrastructure, runtime configuration, AI usage, scaling, storage, and observability. Use before deployment or when changing GCP resources; do not deploy, alter billing, or create resources without explicit authorization.
---

# Vextis GCP Cost Review

Produce an evidence-backed cost view that protects the hackathon credits without weakening the required demo.

## Build the inventory

Read `docs/TECH_STACK.md`, `infra/`, deployment configuration, feature flags, and the workload assumptions supplied by the user. Separate resources into:

- always-on baseline;
- request or execution driven;
- storage and retention driven;
- optional demo capabilities;
- production-only resources that should not exist in the hackathon environment.

Cover Cloud Run, Cloud SQL, Pub/Sub, Cloud Storage, Artifact Registry, Secret Manager, networking and egress, logging and tracing, Vertex AI or Agent Engine, Gemini tokens, embeddings, Memory Bank, Model Armor, Live sessions, and image or video generation when present.

When prices, quotas, free tiers, promotional-credit coverage, or regional availability matter, verify them against current official Google Cloud sources. State the region, currency, assumptions, and date checked. Do not assume every model, SKU, tax, or marketplace charge is covered by promotional credits.

## Model realistic usage

Estimate at least:

1. an idle or development baseline;
2. a reproducible demo run;
3. expected hackathon testing volume;
4. a runaway scenario caused by retries, loops, concurrency, logging, or media generation.

Prefer ranges when inputs are uncertain. Identify the largest cost drivers and the event or configuration that triggers each one. Keep one-time build costs distinct from runtime costs.

## Apply cost guardrails

Recommend only controls relevant to the observed configuration, including:

- a dedicated hackathon project and billing visibility;
- budget alerts, noting that alerts alone do not stop spend;
- service quotas, request limits, concurrency, timeouts, retry ceilings, and Cloud Run maximum instances;
- zero minimum instances where cold starts are acceptable;
- the smallest Cloud SQL shape that supports the demo plus an explicit shutdown or deletion plan;
- short retention, sampling, exclusions, and redaction for high-volume logs and traces;
- lifecycle or TTL policies for uploads, generated media, audio, embeddings, and temporary artifacts;
- caching or batching only when it reduces measured model or infrastructure usage;
- feature flags and per-user limits for Live, image, and video generation;
- idempotency and loop ceilings that prevent repeated paid agent actions.

Do not recommend extra infrastructure solely for theoretical scale. Preserve the accepted architecture and distinguish a hackathon safeguard from a future production recommendation.

## Deliver the review

Report:

- estimated baseline, demo, expected, and runaway ranges;
- assumptions that materially affect the estimate;
- top cost drivers in priority order;
- current protections and missing guardrails;
- immediate no-regret changes;
- optional changes with their product tradeoffs;
- a simple way to verify actual spend after deployment.

Do not create projects, enable APIs, change quotas, attach billing accounts, redeem credits, or deploy resources unless the user explicitly requests that action. Never request or expose payment details, promotional codes, or credentials.
