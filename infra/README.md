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
reason above — and, for the same reason, it never creates the `vextis_app`
Cloud SQL **user** either: there is no `google_sql_user` resource, only the
`google_sql_database_instance` and `google_sql_database` it runs on. Every
Cloud Run service references its secret at `version = "latest"`, and
`module.cloud_run` depends on `module.iam`, so a single unscoped `terraform
apply` on a first deploy tries to create those Cloud Run revisions in the same
run that first creates the secret containers — before any version can exist,
and before a `vextis_app` user exists to generate `vextis-db-password`'s value
from in the first place. Cloud Run then fails to come up at all, not fails at
runtime, and the apply that was supposed to hand you a working environment
errors out instead.

**A first deploy in a new project must therefore run in three phases, in this
order.** A redeploy of an existing environment (instance, database, user and
secrets already exist) just runs phase 3.

**Phase 1 — create the Cloud SQL instance and database, and every Secret
Manager container**, with `-target` so Cloud Run and everything else in the
plan stays out of this apply:

```bash
cd terraform/environments/hackathon
terraform apply     -target=module.cloud_sql.google_sql_database_instance.postgres     -target=module.cloud_sql.google_sql_database.application     -target=module.cloud_sql.google_secret_manager_secret.database_password     -target=module.iam.google_secret_manager_secret.agent_tools_token     -target=module.iam.google_secret_manager_secret.live_gateway_token     -target=module.iam.google_secret_manager_secret.demo_admin_token     -target=module.iam.google_secret_manager_secret.core_callback_token
```

The Cloud SQL instance alone can take several minutes to come up — this is the
slow step in the phase, not a hang. On a redeploy or a retry after a partial
failure this is a no-op for anything that already exists; if any of the five
secrets exists in the project but outside this state (for example, created by
hand in an earlier partial attempt), import it instead of letting `apply` fail
on a name collision: `terraform import <target address> <secret_id>`, using
the same address as its `-target` above and the literal secret ID (e.g.
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
value has to match the actual Cloud SQL user's password, and — because
Terraform creates the instance and database but not the user — that user may
not exist yet either. Check for it first and branch: `create` on a first
deploy (the instance from Phase 1 exists, but no application user does yet),
`set-password` on a redeploy or a rotation (the user is already there from a
previous run of this same block). Either way, the same generated password ends
up in both the Cloud SQL user and the secret version:

```bash
DB_PASSWORD="$(openssl rand -base64 32)"
if gcloud sql users list     --project=vextis-erp --instance=vextis-hackathon-pg     --format="value(name)" | grep -qx vextis_app; then
  gcloud sql users set-password vextis_app       --project=vextis-erp --instance=vextis-hackathon-pg       --password="$DB_PASSWORD"
else
  gcloud sql users create vextis_app       --project=vextis-erp --instance=vextis-hackathon-pg       --password="$DB_PASSWORD"
fi
printf '%s' "$DB_PASSWORD" | gcloud secrets versions add vextis-db-password     --project=vextis-erp --data-file=-
unset DB_PASSWORD
```

Both branches leave `vextis-db-password`'s new version matching whatever
`vextis_app`'s live password actually is, so this block is safe to re-run on a
partially-deployed install without knowing in advance which case it's in.

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
