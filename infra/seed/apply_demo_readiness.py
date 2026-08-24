# /// script
# requires-python = ">=3.11"
# dependencies = [
#   "cloud-sql-python-connector[pg8000]>=1.18,<2",
#   "sqlparse>=0.5,<1",
# ]
# ///
"""Apply the idempotent hackathon readiness seed to Cloud SQL."""

from __future__ import annotations

import os
from pathlib import Path

import sqlparse
from google.cloud.sql.connector import Connector, IPTypes

INSTANCE_CONNECTION_NAME = os.getenv(
    "VEXTIS_CLOUD_SQL_CONNECTION_NAME",
    "vextis-erp:us-central1:vextis-hackathon-pg",
)
DATABASE_NAME = os.getenv("VEXTIS_DATABASE_NAME", "vextis")
DATABASE_USER = os.getenv("POSTGRES_USER", "vextis_app")
SEED_PATH = Path(__file__).with_name("demo-readiness.sql")


def required_environment(name: str) -> str:
    value = os.getenv(name)
    if value is None or not value.strip():
        raise RuntimeError(f"{name} must be supplied through the environment")
    return value


def main() -> None:
    password = required_environment("POSTGRES_PASSWORD")
    statements = [
        statement
        for statement in sqlparse.split(SEED_PATH.read_text(encoding="utf-8"))
        if statement
    ]

    with Connector(ip_type=IPTypes.PUBLIC) as connector:
        connection = connector.connect(
            INSTANCE_CONNECTION_NAME,
            "pg8000",
            user=DATABASE_USER,
            password=password,
            db=DATABASE_NAME,
        )
        try:
            cursor = connection.cursor()
            for statement in statements:
                cursor.execute(statement)
            connection.commit()

            checks = {
                "customers": "SELECT COUNT(*) FROM crm_customers WHERE tenant_id = 'demo-tenant'",
                "stock_items": "SELECT COUNT(*) FROM inventory_stock WHERE tenant_id = 'demo-tenant'",
                "credit_profiles": "SELECT COUNT(*) FROM billing_credit_profiles WHERE tenant_id = 'demo-tenant'",
            }
            counts: dict[str, int] = {}
            for name, query in checks.items():
                cursor.execute(query)
                counts[name] = int(cursor.fetchone()[0])
            print("Demo readiness seed applied:", counts)
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()


if __name__ == "__main__":
    main()
