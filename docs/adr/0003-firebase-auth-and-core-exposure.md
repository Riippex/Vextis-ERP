# ADR 0003: Firebase authentication and Enterprise Core exposure

Status: Accepted on August 23, 2026.

## Context

Angular needs a public HTTPS entry point, while Agent Runtime needs private,
service-authenticated access to the internal tools API. Making the existing
Enterprise Core Cloud Run service anonymous would remove an IAM boundary from
`/internal/agent-tools/**` even if application authorization remained in place.

## Decision

- Firebase Hosting serves the static Angular application and rewrites
  `/graphql` to `vextis-enterprise-core-public`.
- Firebase Authentication issues the user ID token. Angular sends it as a
  bearer token and Enterprise Core verifies it with Firebase Admin.
- The verified Firebase UID becomes the business `USER` actor. The browser
  cannot provide its own actor or tenant identifier.
- The hackathon tenant remains server-configured as `demo-tenant` until tenant
  membership is modeled explicitly.
- The same Enterprise Core code and container image run in two Cloud Run
  services with different exposure modes:
  - `PUBLIC`: permits health endpoints, requires Firebase authentication for
    `/graphql`, and denies `/internal/**`.
  - `INTERNAL`: permits health and internal tool routes behind Cloud Run IAM,
    and denies GraphQL.
- `LOCAL` is an explicit developer-only mode and is never configured in GCP.

## Consequences

- Enterprise Core remains one deployable application and one business
  authority; this is a trust-boundary deployment split, not a new backend.
- Internal agent routes retain Cloud Run IAM plus the application-level agent
  token and tenant/agent checks.
- The public runtime gets its own least-privilege service account. It can reach
  Cloud SQL, read only the database password secret, and publish outbox events;
  it cannot read the agent-tools secret.
- Both runtimes scale to zero. The extra service adds operational inventory but
  no idle instance baseline.
- A future API gateway or load balancer is unnecessary for the hackathon and
  can be introduced later only if measured requirements justify it.
