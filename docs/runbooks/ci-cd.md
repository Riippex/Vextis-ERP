# CI/CD runbook

## Environments and branches

- Local development remains the default developer environment.
- `develop` integrates changes and runs the complete validation suite.
- `main` publishes versioned images for the shared `hackathon` environment after validation succeeds.
- Pull requests never receive Google Cloud credentials.
- Production and a separate Google Cloud `dev` environment are intentionally out of scope for the hackathon.

## Continuous integration

`.github/workflows/ci.yml` runs on every pull request and on pushes to `develop` and `main`:

- Enterprise Core Gradle tests, including architecture and integration tests;
- Agent Runtime tests/evals, Ruff, and strict mypy;
- Angular lint, tests, production build, and generated GraphQL drift check;
- Terraform formatting and validation without contacting a state backend.

The workflow has read-only repository permissions and no Google Cloud identity.

## Continuous delivery

After all four CI jobs pass on `main`, the delivery job checks whether deployable sources changed. Only then does GitHub exchange its short-lived OIDC token for the `vextis-build-hackathon` service identity. No service-account key or GitHub secret is stored. Pushes to `develop` validate the code but never receive a Google Cloud identity.

Cloud Build publishes all three images using the immutable Git commit SHA as the tag. Publishing an artifact does not deploy it. Promotion to Cloud Run remains a deliberate Terraform operation until remote state and protected GitHub environments are in place.

## Trust boundary

The Workload Identity Provider accepts tokens only when all of these claims match:

- repository ID `1338929025` (`Riippex/Vextis-ERP`);
- repository owner ID `221794453` (`Riippex`);
- ref `refs/heads/main`.

The federated service account can create Cloud Builds, stage source archives, write build logs, and publish images. It cannot deploy Cloud Run, modify Terraform-managed infrastructure, access runtime secrets, query Cloud SQL, or call Vertex AI.

## Promotion

Before promoting a commit:

1. Confirm the CI workflow is green.
2. Confirm Cloud Build published the same SHA for `enterprise-core`, `agent-runtime`, and `web`.
3. Set `enterprise_core_image_tag` and `agent_runtime_image_tag` to that SHA.
4. Run `terraform plan` and require zero destructive actions.
5. Apply manually and run authenticated health checks.

For the web/authentication boundary, promotion order is mandatory:

1. Publish an Enterprise Core image that contains Firebase token validation.
2. Point `enterprise_core_image_tag` at that immutable image.
3. Apply Terraform to create `vextis-enterprise-core-public`; never create the
   public service with an older image that lacks application authentication.
4. Confirm anonymous `/graphql` requests receive `401` and `/internal/**`
   receives `403` on the public service.
5. Build Angular and deploy Firebase Hosting. Hosting rewrites only `/graphql`
   to the public Core; all SPA routes fall back to `index.html`.

Firebase CLI authentication is separate from Google Cloud CLI authentication.
The one-time project setup must register `vextis-erp` with Firebase, create a
web app/default Hosting site, enable email/password sign-in, disable public
self-signup, and create the demo user through an authorized admin flow. Firebase
web configuration is loaded at runtime from `/__/firebase/init.json`; it is not
stored in environment files or secrets.

Automated promotion must not be enabled until Terraform state is remote, locking is configured, and the GitHub `hackathon` environment requires approval.
