# ADR 0004: Selective component delivery

- Status: accepted
- Date: 2026-08-24

## Context

Vextis has three independently deployable components: the Angular web app, the
Java Enterprise Core, and the Python Agent Runtime. The original workflow built
all container images whenever any deployable source changed and left promotion
as a manual Terraform operation. That made small changes consume unnecessary
Cloud Build time and coupled releases that have different operational risk.

Terraform state for the hackathon environment is still local. Letting a CI job
run Terraform would therefore create a second infrastructure authority without
shared locking or a reliable state history.

## Decision

CI remains a single, credential-free validation workflow. It tests all three
components and validates Terraform on pull requests and pushes to `develop` and
`main`, because contracts and architecture rules can cross component boundaries.

After a successful CI run on `main`, three independent delivery workflows inspect
the validated commit:

- Web changes build Angular and deploy Firebase Hosting.
- Enterprise Core or shared-contract changes build one immutable Core image and
  deploy it to both the internal and public Core services.
- Agent Runtime or shared-contract changes build and deploy only the Agent image.

Each workflow has its own path filter and concurrency group. A manual dispatch is
an explicit redeploy of the current `main` revision and therefore bypasses the
path filter. Deployment jobs use the protected GitHub `hackathon` environment and
short-lived Workload Identity Federation credentials; pull requests and
`develop` never receive Google Cloud credentials.

Terraform continues to own service configuration, IAM, secrets, scaling, ingress,
and deletion protection. Delivery owns only the Cloud Run container image field.
Terraform ignores image drift so an infrastructure apply cannot roll back a
revision deployed by delivery. Infrastructure changes remain manual until remote
state and locking exist.

The existing `vextis-build-hackathon` identity is reused as the delivery identity
for the hackathon. It can submit Cloud Builds, update Cloud Run services, deploy
Firebase Hosting, consume the required project APIs, and act as the three specific
runtime service accounts. It has no access to runtime secrets, Cloud SQL data, or
Vertex AI.

## Consequences

- A web-only change does not build either backend.
- A Core-only change does not build Agent Runtime or Angular.
- A shared contract can intentionally deploy both affected backends.
- A failed or cancelled CI run cannot start an automatic deployment.
- Core deployment updates two services as one workflow; a failure between them is
  visible but can require a manual rerun to converge both services.
- The hackathon uses one narrowly federated delivery identity for simplicity. A
  later multi-environment system should split identities per component and
  environment.
