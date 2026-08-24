# infra/

Reproducible configuration for Cloud Run, Cloud SQL PostgreSQL, Pub/Sub, Cloud Storage, IAM, Secret Manager, Artifact Registry, and observability.

Minimal infrastructure is defined with Terraform and a `hackathon` environment. Optional resources like Memorystore, GKE, or replicas are not created until a measurable need exists.

## Hackathon database

`terraform/environments/hackathon` declares the minimal Cloud SQL PostgreSQL
environment in `us-central1`: one zonal `db-f1-micro` instance, the `vextis`
database, automated backups retained for three runs, and Secret Manager metadata
for the application password. It has no authorized IP networks; applications
must connect through Cloud SQL Connector or Cloud SQL Auth Proxy.

Terraform intentionally does not manage the database user's password or a
secret version because provider state stores managed secret values in plaintext.
Provision those after the infrastructure apply, passing the generated password
only through process memory and standard input.

The environment also creates separate keyless service identities for Enterprise
Core, Agent Runtime, and authenticated Pub/Sub push delivery. Enterprise Core
can connect only to the hackathon Cloud SQL instance and read its own database
secret. Agent Runtime can use Vertex AI but has no Cloud SQL access. Both
backends can read the dedicated `vextis-agent-tools-token`; the Pub/Sub push
identity has no application or data permissions.

GitHub Actions federates into `vextis-build-hackathon` only from `main`. That
identity builds component images and deploys the selected Cloud Run or Firebase
Hosting release; it cannot read application secrets or data. Terraform owns the
Cloud Run configuration while delivery owns only the immutable container image
revision, as recorded in ADR 0004.

The `order-events` topic is provisioned for the Enterprise Core transactional
outbox. Publisher access is granted on that topic only. Its authenticated push
subscription is intentionally deferred until Agent Runtime has a stable Cloud
Run URL and can receive the corresponding resource-level `roles/run.invoker`
binding.

Run Terraform from `terraform/environments/hackathon`. Local state is ignored by
Git; migrate it to a protected remote backend before collaborating on production
infrastructure.

For the local readiness demo, apply `infra/seed/demo-readiness.sql` after Flyway creates the schema. The seed is idempotent and scoped to `demo-tenant`; demo rows remain outside production migrations.
