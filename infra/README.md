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

### Bootstrapping secret versions

Terraform creates every Secret Manager **secret** (the named container) but
deliberately creates no **version** (the actual value) for any of them, for the
reason above. Every Cloud Run service references its secret at
`version = "latest"`, and `module.cloud_run` depends on `module.iam`, so a
single unscoped `terraform apply` on a first deploy tries to create those Cloud
Run revisions in the same run that first creates the secret containers —
before any version can exist. Cloud Run then fails to come up at all, not
fails at runtime, and the apply that was supposed to hand you a working
environment errors out instead.

**A first deploy in a new project must therefore run in three phases, in this
order.** A redeploy of an existing environment (secrets already have versions)
just runs phase 3.

**Phase 1 — create only the Secret Manager containers**, with `-target` so
Cloud Run and everything else in the plan stays out of this apply:

```bash
cd terraform/environments/hackathon
terraform apply     -target=module.cloud_sql.google_secret_manager_secret.database_password     -target=module.iam.google_secret_manager_secret.agent_tools_token     -target=module.iam.google_secret_manager_secret.live_gateway_token     -target=module.iam.google_secret_manager_secret.demo_admin_token     -target=module.iam.google_secret_manager_secret.core_callback_token
```

If any of these five secrets already exists in the project (for example, a
partially-applied previous attempt), import it instead of letting `apply`
fail on a name collision: `terraform import <target address> <secret_id>`,
using the same address as its `-target` above and the literal secret ID (e.g.
`vextis-agent-tools-token`) as the import ID.

**Phase 2 — give every secret a version**, following the commands below. Do
this for all five before moving on; a service whose secret still has no
version fails Phase 3.

```bash
openssl rand -base64 32 | gcloud secrets versions add SECRET_ID     --project=vextis-erp     --data-file=-
```

`--data-file=-` reads the value from standard input piped in from `openssl`, so
it never touches a file on disk or a shell history entry. This environment
provisions five secrets that need a version this way:

| Secret ID | Consumed by | Set up in |
| --- | --- | --- |
| `vextis-db-password` | Enterprise Core (both services) | pre-existing |
| `vextis-agent-tools-token` | Agent Runtime (private), Enterprise Core | pre-existing |
| `vextis-core-callback-token` | Enterprise Core (public), Agent Runtime | pre-existing |
| `vextis-demo-admin-token` | Enterprise Core (private only) | this change |
| `vextis-live-gateway-token` | Agent Runtime (Live gateway), Enterprise Core (private) | this change |

`vextis-db-password` is the one exception to the generic command above: its
value has to match the actual Cloud SQL user's password, so set the user first
and feed Secret Manager the same value:

```bash
DB_PASSWORD="$(openssl rand -base64 32)"
gcloud sql users set-password vextis_app     --project=vextis-erp --instance=vextis-hackathon-pg     --password="$DB_PASSWORD"
printf '%s' "$DB_PASSWORD" | gcloud secrets versions add vextis-db-password     --project=vextis-erp --data-file=-
unset DB_PASSWORD
```

The other four are opaque bearer tokens compared byte-for-byte by the
application (`MessageDigest.isEqual`), so any sufficiently random value works;
the plain `openssl rand -base64 32 | gcloud secrets versions add ...` command
above is enough for each. `vextis-demo-admin-token` and `vextis-live-gateway-token`
are the two new ones: without a version, `/internal/demo/**` answers `503`
(fails closed by design, see `DemoManagementController`) and the Live gateway
cannot resolve its own service identity against Enterprise Core, so voice
sessions fail at the WebSocket handshake.

`vextis-live-gateway-token` must differ from `vextis-agent-tools-token` — reuse
the same value and `ConfiguredServiceCallerIdentities` refuses to start
(`vextis.agent-tools.live-gateway-token must differ from
vextis.agent-tools.service-token`), because sharing it would collapse the
public gateway and the private runtime back into one service identity, which is
exactly the separation ADR 0005 exists to enforce.

**Phase 3 — run the full apply.** With all five secrets versioned, run
`terraform apply` with no `-target` from `terraform/environments/hackathon`.
Cloud Run now finds a `latest` version for every secret it references and the
rest of the environment (Cloud Run, Pub/Sub, Storage, Artifact Registry,
GitHub OIDC) comes up in this pass:

```bash
terraform apply
```

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
