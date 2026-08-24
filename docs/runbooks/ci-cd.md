# CI/CD runbook

## Environments and branches

- Local development remains the default developer environment.
- `develop` integrates changes and runs the complete validation suite.
- `main` is the only branch that can automatically deploy to `hackathon`.
- Pull requests never receive Google Cloud credentials.
- Production and a separate Google Cloud `dev` environment are intentionally out
  of scope for the hackathon.

## Continuous integration

`.github/workflows/ci.yml` runs on every pull request and on pushes to `develop`
and `main`:

- Enterprise Core Gradle tests, including architecture and integration tests;
- Agent Runtime tests/evals, Ruff, and strict mypy;
- Angular lint, tests, production build, and generated GraphQL drift check;
- Terraform formatting and validation without contacting a state backend.

CI validates the complete system even when one component changed. This keeps the
required branch-protection check stable and detects shared-contract regressions.
The workflow has read-only repository permissions and no Google Cloud identity.

## Selective continuous delivery

Three workflows listen for a successful `CI` completion on `main`. Their cheap
change-detection job checks the validated merge commit before any build or cloud
authentication occurs:

| Workflow | Deployable | Paths that trigger it |
| --- | --- | --- |
| `deploy-web.yml` | Angular to Firebase Hosting | `apps/web`, GraphQL contracts, Firebase config, and pnpm workspace files |
| `deploy-enterprise-core.yml` | One image to the internal and public Core services | Enterprise Core, all contracts, its Cloud Build config, Docker context metadata, license, and notice |
| `deploy-agent-runtime.yml` | Agent image to Agent Runtime | Agent Runtime, all contracts, its Cloud Build config, and Docker context metadata |

Unrelated commits end after change detection. Each deployable uses a separate
concurrency group, so one component neither cancels nor rebuilds another. A manual
`workflow_dispatch` always redeploys the current `main` commit and is the recovery
mechanism for a partially failed deployment.

Container images use the immutable Git commit SHA as their tag. The Core workflow
deploys the same SHA to `vextis-enterprise-core` and
`vextis-enterprise-core-public`. The Agent workflow deploys it only to
`vextis-agent-runtime`. The Web workflow builds from the same validated SHA and
publishes Firebase Hosting without creating a web container.

## Approval and trust boundary

Deployment jobs target the protected GitHub environment `hackathon`. Configure at
least one required reviewer and prevent administrators from bypassing protection.
The approval happens after change detection but before cloud authentication or a
billable build.

The Workload Identity Provider accepts tokens only when all of these claims match:

- repository ID `1338929025` (`Riippex/Vextis-ERP`);
- repository owner ID `221794453` (`Riippex`);
- ref `refs/heads/main`.

The federated `vextis-build-hackathon` identity can submit Cloud Builds, stage
source archives, publish images, update Cloud Run services, deploy Firebase
Hosting, consume the required project APIs, and act as the three Vextis runtime
identities. It cannot read runtime secrets, query Cloud SQL, or call Vertex AI.
Credentials are short-lived; no service-account key or deployment secret is
stored in GitHub.

## Infrastructure and release ownership

Terraform owns Cloud Run configuration, IAM, secrets, scaling, ingress, and
deletion protection. Delivery owns only each service's container image. The Cloud
Run module ignores image drift so a later infrastructure apply cannot roll back a
deployed revision. Terraform execution remains manual until state is remote and
locked; delivery does not read or write Terraform state.

The SPA entry paths (`/`, `/login`, `/app`, and `/app/**`) use
`Cache-Control: no-cache` so browsers revalidate the current release.
Content-hashed JavaScript and CSS assets remain immutable and cached for one year.
Firebase Hosting rewrites only `/graphql` to the public Core; all SPA routes fall
back to `index.html`.

## Recovery

1. Identify the failed component and validated commit SHA in GitHub Actions.
2. If CI failed, fix it; do not deploy that revision.
3. If build or deployment failed transiently, manually dispatch only that
   component's workflow. It redeploys current `main`.
4. For rollback, deploy a previously published immutable image SHA with `gcloud
   run deploy` or roll back the Firebase Hosting release, then record the selected
   SHA. Do not modify Terraform image variables to perform a release rollback.
5. Confirm Cloud Run reports a ready revision, then run authenticated application
   smoke tests. Anonymous `/graphql` must return `401`, and `/internal/**` must
   return `403` on the public Core.

Firebase web configuration is loaded at runtime from `/__/firebase/init.json`; it
is not stored in environment files or secrets. Email/password sign-in remains
admin-provisioned and public self-signup remains disabled.
