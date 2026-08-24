# CI/CD runbook

## Environments and branches

- Local development remains the default developer environment.
- `develop` integrates changes and publishes versioned images for the shared `hackathon` environment.
- Pull requests never receive Google Cloud credentials.
- Production and a separate Google Cloud `dev` environment are intentionally out of scope for the hackathon.

## Continuous integration

`.github/workflows/ci.yml` runs on every pull request and on `develop`:

- Enterprise Core Gradle tests, including architecture and integration tests;
- Agent Runtime tests/evals, Ruff, and strict mypy;
- Angular lint, tests, production build, and generated GraphQL drift check;
- Terraform formatting and validation without contacting a state backend.

The workflow has read-only repository permissions and no Google Cloud identity.

## Continuous delivery

After all four CI jobs pass on `develop`, the delivery job checks whether deployable sources changed. Only then does GitHub exchange its short-lived OIDC token for the `vextis-build-hackathon` service identity. No service-account key or GitHub secret is stored.

Cloud Build publishes all three images using the immutable Git commit SHA as the tag. Publishing an artifact does not deploy it. Promotion to Cloud Run remains a deliberate Terraform operation until remote state and protected GitHub environments are in place.

## Trust boundary

The Workload Identity Provider accepts tokens only when all of these claims match:

- repository ID `1338929025` (`Riippex/Vextis-ERP`);
- repository owner ID `221794453` (`Riippex`);
- ref `refs/heads/develop`.

The federated service account can create Cloud Builds, stage source archives, write build logs, and publish images. It cannot deploy Cloud Run, modify Terraform-managed infrastructure, access runtime secrets, query Cloud SQL, or call Vertex AI.

## Promotion

Before promoting a commit:

1. Confirm the CI workflow is green.
2. Confirm Cloud Build published the same SHA for `enterprise-core`, `agent-runtime`, and `web`.
3. Set `enterprise_core_image_tag` and `agent_runtime_image_tag` to that SHA.
4. Run `terraform plan` and require zero destructive actions.
5. Apply manually and run authenticated health checks.

Automated promotion must not be enabled until Terraform state is remote, locking is configured, and the GitHub `hackathon` environment requires approval.
