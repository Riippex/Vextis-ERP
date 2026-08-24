import base64
import json
from typing import Any


def purchase_order_event(**overrides: Any) -> dict[str, Any]:
    event: dict[str, Any] = {
        "event_id": "8b962f0a-1850-4fcc-a6f5-97e45c67a16e",
        "event_type": "purchase_order.received",
        "event_version": 2,
        "occurred_at": "2026-08-21T03:30:00Z",
        "producer": "enterprise-core",
        "tenant_id": "demo-tenant",
        "correlation_id": "corr-001",
        "causation_id": "audit-001",
        "actor": {"type": "USER", "id": "demo-user"},
        "payload": {
            "purchase_order_id": "77cc63cc-3c91-4d80-a918-605b7f231cf8",
            "execution_id": "8d3f290d-1322-44a2-8bd7-3b325f170e07",
            "document_uri": "gs://vextis-demo/orders/po-2026-001.pdf",
        },
    }
    event.update(overrides)
    return event


def pubsub_push_body(event: dict[str, Any] | None = None) -> bytes:
    encoded = base64.b64encode(json.dumps(event or purchase_order_event()).encode()).decode()
    return json.dumps(
        {
            "message": {
                "data": encoded,
                "messageId": "pubsub-message-001",
                "publishTime": "2026-08-21T03:30:01Z",
            },
            "subscription": "projects/demo/subscriptions/order-events-agent-runtime",
        }
    ).encode()


def approval_decided_event(status: str = "APPROVED") -> dict[str, Any]:
    return {
        "event_id": "95a0c3b0-e693-4a44-a396-147453bbbf02",
        "event_type": "workflow.approval.decided",
        "event_version": 1,
        "occurred_at": "2026-08-24T20:00:00Z",
        "producer": "enterprise-core",
        "tenant_id": "demo-tenant",
        "correlation_id": "corr-001",
        "causation_id": "5c854c8f-f66f-4b30-bb49-4058bc9f50e4",
        "actor": {"type": "USER", "id": "firebase-user"},
        "payload": {
            "approval_id": "6d61c37c-b45f-4a03-a96f-93f441ffcae7",
            "execution_id": "8d3f290d-1322-44a2-8bd7-3b325f170e07",
            "order_id": "77cc63cc-3c91-4d80-a918-605b7f231cf8",
            "order_lines": [{"sku": "VXT-CHAIR-01", "quantity": 10}],
            "status": status,
            "recommendation": "Proceed after reviewing evidence.",
            "evidence": [
                {"department": "CRM_SALES", "status": "READY", "detail": "Ready"},
                {"department": "INVENTORY_OPERATIONS", "status": "READY", "detail": "Ready"},
                {"department": "FINANCE_BILLING", "status": "READY", "detail": "Ready"},
            ],
            "decided_by": "firebase-user",
            "reason": "Approved",
        },
    }
