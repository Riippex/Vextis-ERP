# Hackathon demo seed

The demo fixture is intentionally synthetic and contains no real customer or personal data.

Generate the purchase-order PDF from the repository root:

```powershell
uv run --with reportlab infra/seed/generate_demo_purchase_order.py
```

Upload the generated file to:

```text
gs://vextis-erp-hackathon-assets/demo/purchase-orders/PO-2026-001.pdf
```

Apply the idempotent CRM, inventory, and credit readiness data using Application Default Credentials. Load `POSTGRES_PASSWORD` from Secret Manager into the current process; never write it to a file or command output.

```powershell
uv run infra/seed/apply_demo_readiness.py
```

The seed targets `demo-tenant` and may be run repeatedly. Schema changes remain owned by Enterprise Core Flyway migrations.
