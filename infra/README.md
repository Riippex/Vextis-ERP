# infra/

Reproducible configuration for Cloud Run, Cloud SQL PostgreSQL, Pub/Sub, Cloud Storage, IAM, Secret Manager, Artifact Registry, and observability.

Minimal infrastructure is defined with Terraform and a `hackathon` environment. Optional resources like Memorystore, GKE, or replicas are not created until a measurable need exists.

For the local readiness demo, apply `infra/seed/demo-readiness.sql` after Flyway creates the schema. The seed is idempotent and scoped to `demo-tenant`; demo rows remain outside production migrations.
